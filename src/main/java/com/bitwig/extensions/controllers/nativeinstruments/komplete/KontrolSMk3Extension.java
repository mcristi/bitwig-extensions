package com.bitwig.extensions.controllers.nativeinstruments.komplete;

import com.bitwig.extension.controller.api.Action;
import com.bitwig.extension.controller.api.Application;
import com.bitwig.extension.controller.api.Arranger;
import com.bitwig.extension.controller.api.Clip;
import com.bitwig.extension.controller.api.ClipLauncherSlotBank;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.CursorTrack;
import com.bitwig.extension.controller.api.HardwareButton;
import com.bitwig.extension.controller.api.MidiIn;
import com.bitwig.extension.controller.api.NoteInput;
import com.bitwig.extension.controller.api.RelativeHardwarControlBindable;
import com.bitwig.extension.controller.api.RelativeHardwareKnob;
import com.bitwig.extension.controller.api.ScrollbarModel;
import com.bitwig.extension.controller.api.SettableBeatTimeValue;
import com.bitwig.extension.controller.api.Track;
import com.bitwig.extension.controller.api.TrackBank;
import com.bitwig.extension.controller.api.Transport;
import com.bitwig.extensions.CommonState;
import com.bitwig.extensions.Globals;
import com.bitwig.extensions.controllers.nativeinstruments.komplete.control.ModeButton;
import com.bitwig.extensions.controllers.nativeinstruments.komplete.definition.AbstractKompleteKontrolExtensionDefinition;
import com.bitwig.extensions.controllers.nativeinstruments.komplete.device.DeviceControl;
import com.bitwig.extensions.framework.Layer;
import com.bitwig.extensions.framework.Layers;
import com.bitwig.extensions.framework.values.FocusMode;
import com.bitwig.extensions.util.ClipUtils;
import com.bitwig.extensions.util.RecordUtils;
import static com.bitwig.extension.controller.api.Application.PANEL_LAYOUT_ARRANGE;
import static com.bitwig.extension.controller.api.Application.PANEL_LAYOUT_EDIT;
import static com.bitwig.extension.controller.api.Application.PANEL_LAYOUT_MIX;

public class KontrolSMk3Extension extends KompleteKontrolExtension {

    private ClipSceneCursor clipSceneCursor;
    protected ScrollbarModel horizontalScrollbarModel;
    protected double scrubDistance;
    protected Arranger arranger;
    private final boolean isNksConnect;

    private MidiIn midiIn2;

    // Arranger zoom amount for an encoder movement that used to sweep the whole volume range
    private static final double SHIFT_FOUR_D_ZOOM_SENSITIVITY = 32;

    // Encoder movement (as a fraction of that same sweep) per key-height zoom step in the detail editor
    private static final double SHIFT_FOUR_D_LANE_ZOOM_STEP = 1 / 128.0;

    private String panelLayout = PANEL_LAYOUT_MIX;
    private double laneZoomAccumulator = 0;
    private boolean drumEditorShown = false;

    private boolean footswitch1TipPending = false;
    private boolean footswitch1RingPending = false;

    public KontrolSMk3Extension(final AbstractKompleteKontrolExtensionDefinition definition, final ControllerHost host,
        final boolean isNksConnect) {
        super(definition, host);
        this.isNksConnect = isNksConnect;
    }

    @Override
    protected boolean hasDeviceControl() {
        return true;
    }

    @Override
    public void init() {
        super.init();
        final ControllerHost host = getHost();

        this.arranger = host.createArranger();
        detailEditor = host.createDetailEditor();
        horizontalScrollbarModel = this.arranger.getHorizontalScrollbarModel();
        horizontalScrollbarModel.getContentPerPixel().addValueObserver(this::handleZoomLevel);
        midiProcessor.intoDawMode(0x4);
        layers = new Layers(this);
        mainLayer = new Layer(layers, "Main");
        arrangeFocusLayer = new Layer(layers, "ArrangeFocus");
        sessionFocusLayer = new Layer(layers, "SessionFocus");
        navigationLayer = new Layer(layers, "NavigationLayer");
        clipSceneCursor = viewControl.getClipSceneCursor();

        if (!isNksConnect) {
            midiIn2 = host.getMidiInPort(1);
            final NoteInput noteInput = midiIn2.createNoteInput(
                "MIDI",
                "8?????",  // Note Off, all channels
                "9?????",  // Note On, all channels
                "A?????",  // Poly aftertouch, all channels
                "D?????",  // Channel aftertouch, all channels
                "E?????",  // Pitch bend, all channels
                "B?01??",  // Mod wheel, all channels
                "B?0B??",  // Expression, all channels
                "B?40??",  // Sustain, all channels
                "B?42??",  // Sostenuto, all channels
                "B1????"
            );
            noteInput.setShouldConsumeEvents(true);
        }
        initTrackBank();
        setUpTransport();

        new DeviceControl(host, midiProcessor, viewControl, layers, controlElements);
        initTempoControl();
        midiProcessor.addTempoListener(this::handleTempoIncoming);
        initScrubZoomControl();
        activateStandardLayers();

        application = host.createApplication();
        // Default until the layout observer reports Arrange; the Edit layout keeps the last focus
        updateFocusMode(FocusMode.LAUNCHER.getDescriptor());
        application.panelLayout().addValueObserver((layout) -> {
            if (layout.equals(PANEL_LAYOUT_ARRANGE)) {
                this.updateFocusMode(FocusMode.ARRANGER.getDescriptor());
            } else if (layout.equals(PANEL_LAYOUT_MIX)) {
                this.updateFocusMode(FocusMode.LAUNCHER.getDescriptor());
            }
        });
    }

    protected void initScrubZoomControl() {
        final RelativeHardwareKnob fourDKnob = controlElements.getFourDKnob();
        final RelativeHardwarControlBindable binding = midiProcessor.createIncAction(this::handleFourDInc);
        mainLayer.bind(fourDKnob, binding);

        // The keyboard sends its "selected track volume" / "selected track pan" messages for SHIFT + 4D encoder.
        // Neither changes the track here; both zoom instead.
        mainLayer.bind(controlElements.getFourDKnobMixer(),
            getHost().createRelativeHardwareControlAdjustmentTarget(this::handleShiftFourDAdjustment));
        mainLayer.bind(controlElements.getFourDKnobPan(),
            getHost().createRelativeHardwareControlAdjustmentTarget(this::handleShiftFourDAdjustment));

        final RelativeHardwareKnob loopKnob = controlElements.getLoopModKnob();
        mainLayer.bind(loopKnob, midiProcessor.createIncAction(this::handleLoop));
    }

    private void handleLoop(final int inc) {
        final SettableBeatTimeValue position = controlElements.getShiftHeld().get()
            ? viewControl.getTransport().arrangerLoopDuration()
            : viewControl.getTransport().arrangerLoopStart();
        final double newPos = incrementPosition(position, inc);
        position.set(newPos);
    }

    private void handleFourDInc(final int inc) {
        if (PANEL_LAYOUT_EDIT.equals(panelLayout)) {
            // In DAW mode the keyboard sends the same message with and without SHIFT
            if (controlElements.getShiftHeld().get()) {
                if (inc > 0) {
                    detailEditor.zoomInLaneHeights();
                } else {
                    detailEditor.zoomOutLaneHeights();
                }
            } else if (inc > 0) {
                detailEditor.zoomIn();
            } else {
                detailEditor.zoomOut();
            }
            return;
        }
        if (!PANEL_LAYOUT_ARRANGE.equals(panelLayout)) {
            return; // no scrubbing while the arranger timeline is not visible
        }
        if (controlElements.getShiftHeld().get()) {
            final double newPos = viewControl.getTransport().getPosition().get();
            if (inc > 0) {
                horizontalScrollbarModel.zoomAtPosition(newPos, 0.25);
            } else {
                horizontalScrollbarModel.zoomAtPosition(newPos, -0.25);
            }
        } else {
            handlePositionIncrementWithFocus(inc);
        }
    }

    private void handleShiftFourDAdjustment(final double adjustment) {
        if (PANEL_LAYOUT_ARRANGE.equals(panelLayout)) {
            final double position = viewControl.getTransport().getPosition().get();
            horizontalScrollbarModel.zoomAtPosition(position, adjustment * SHIFT_FOUR_D_ZOOM_SENSITIVITY);
        } else if (PANEL_LAYOUT_EDIT.equals(panelLayout)) {
            // The editor cannot be scrolled vertically, so zoom the key heights to bring more pitches into view.
            // It only zooms in whole steps.
            laneZoomAccumulator += adjustment;
            while (laneZoomAccumulator >= SHIFT_FOUR_D_LANE_ZOOM_STEP) {
                laneZoomAccumulator -= SHIFT_FOUR_D_LANE_ZOOM_STEP;
                detailEditor.zoomInLaneHeights();
            }
            while (laneZoomAccumulator <= -SHIFT_FOUR_D_LANE_ZOOM_STEP) {
                laneZoomAccumulator += SHIFT_FOUR_D_LANE_ZOOM_STEP;
                detailEditor.zoomOutLaneHeights();
            }
        }
    }

    private void handlePositionIncrementWithFocus(final int inc) {
        final SettableBeatTimeValue playPosition = viewControl.getTransport().getPosition();
        final double newPos = incrementPosition(playPosition, inc);
        horizontalScrollbarModel.zoomAtPosition(newPos, 0);
        playPosition.set(newPos);
    }

    private double incrementPosition(final SettableBeatTimeValue position, final int inc) {
        return Math.round((position.get() + (inc * scrubDistance)) / scrubDistance) * scrubDistance;
    }

    private void handleZoomLevel(final double v) {
        if (v <= 0) {
            return;
        }
        this.scrubDistance = KontrolUtil.roundToNearestPowerOfTwo(80 * v);
    }

    private void handleTempoIncoming(final double v) {
        viewControl.getTransport().tempo().value().setRaw(v);
    }

    private void initTempoControl() {
        viewControl.getTransport().tempo().value().addRawValueObserver(tempo -> midiProcessor.sendTempo(tempo));
    }

    @Override
    public void setUpChannelDisplayFeedback(final int index, final Track channel) {
        channel.volume().value().addValueObserver(value -> {
            final byte v = KontrolUtil.toSliderVal(value);
            midiProcessor.sendVolumeValue(index, v);
        });
        channel.pan().value().addValueObserver(value -> {
            final int v = (int) (value * 127);
            midiProcessor.sendPanValue(index, v);
        });
        channel.addVuMeterObserver(
            201, 0, true,
            leftValue -> midiProcessor.updateVuLeft(index, KontrolUtil.LEVEL_DB_LOOKUP[leftValue]));
        channel.addVuMeterObserver(
            201, 1, true,
            rightValue -> midiProcessor.updateVuRight(index, KontrolUtil.LEVEL_DB_LOOKUP[rightValue]));
    }

    @Override
    protected void initNavigation() {
        final Clip cursorClip = getHost().createLauncherCursorClip(8, 128);
        final Clip arrangerClip = getHost().createArrangerCursorClip(8, 128);

        arrangerClip.exists().markInterested();
        final CursorTrack cursorTrack = clipSceneCursor.getCursorTrack();
        final Application application = viewControl.getApplication();
        final NavigationState navigationState = viewControl.getNavigationState();
        application.panelLayout().addValueObserver(this::handleLayoutChange);

        controlElements.getLeftNavButton()
            .bind(mainLayer, this::handleLeftNavigation, () -> !navigationState.isSceneNavMode());
        controlElements.getRightNavButton()
            .bind(mainLayer, this::handleRightNavigation, navigationState::canGoTrackRight);
        controlElements.getUpNavButton().bind(mainLayer, this::handleUpNavigation, navigationState::canScrollSceneUp);
        controlElements.getDownNavButton()
            .bind(mainLayer, this::handleDownNavigation, navigationState::canScrollSceneDown);

        cursorClip.exists().markInterested();
        final ModeButton quantizeButton = controlElements.getButton(CcAssignment.QUANTIZE);
        sessionFocusLayer.bindPressed(quantizeButton.getHwButton(), () -> cursorClip.quantize(1.0));

        sessionFocusLayer.bind(
            () -> cursorTrack.canHoldNoteData().get() && cursorClip.exists().get(),
            quantizeButton.getLed());

        arrangeFocusLayer.bindPressed(quantizeButton.getHwButton(), () -> arrangerClip.quantize(1.0));
        arrangeFocusLayer.bind(
            () -> cursorTrack.canHoldNoteData().get() && arrangerClip.exists().get(),
            quantizeButton.getLed());

        cursorTrack.canHoldNoteData().markInterested();
        cursorClip.exists().markInterested();

        final ModeButton clearButton = controlElements.getButton(CcAssignment.CLEAR);
        sessionFocusLayer.bindPressed(clearButton.getHwButton(), () -> cursorClip.clearSteps());
        sessionFocusLayer.bind(
            () -> cursorTrack.canHoldNoteData().get() && cursorClip.exists().get(),
            clearButton.getLed());

        arrangeFocusLayer.bindPressed(clearButton.getHwButton(), () -> arrangerClip.clearSteps());
        arrangeFocusLayer.bind(
            () -> cursorTrack.canHoldNoteData().get() && arrangerClip.exists().get(),
            clearButton.getLed());

        clearButton.getLed().isOn()
            .setValueSupplier(() -> cursorTrack.canHoldNoteData().get() && cursorClip.exists().get());
        final ModeButton knobPressed = controlElements.getKnobPressed();
        final ModeButton knobShiftPressed = controlElements.getKnobShiftPressed();
        // Pressing the encoder no longer launches clips. In the EDIT layout it zooms the editor to fit.
        mainLayer.bindPressed(knobPressed.getHwButton(), () -> {
            if (PANEL_LAYOUT_EDIT.equals(panelLayout)) {
                detailEditor.zoomToFitSelectionOrAll();
            }
        });

        // SHIFT + press no longer stops the track. In the EDIT layout it switches the note editor mode.
        // The API has no editor-mode setter or getter, so use Bitwig's actions and track the mode here.
        final Action showPianoRollAction = application.getAction("show_piano_roll_editor_mode");
        final Action showDrumEditorAction = application.getAction("show_pattern_editor_mode");
        mainLayer.bindPressed(knobShiftPressed.getHwButton(), () -> {
            if (PANEL_LAYOUT_EDIT.equals(panelLayout) && showPianoRollAction != null && showDrumEditorAction != null) {
                drumEditorShown = !drumEditorShown;
                (drumEditorShown ? showDrumEditorAction : showPianoRollAction).invoke();
            }
        });

        if (midiIn2 != null) { // not opened for NKS Connect, so no footswitch there
            initDigiTechFS3XLooper();
        }
    }

    private void initDigiTechFS3XLooper() {
        final int FOOTSWITCH_CHANNEL = 0;
        final int FOOTSWITCH_CC_TIP = 50;
        final int FOOTSWITCH_CC_RING = 51;
        final int FOOTSWITCH_ON_VALUE = 127;

        final int FOOTSWITCH_BINDING_CALLBACK_DELAY = 5;

        final HardwareButton footswitch1ButtonTip = surface.createHardwareButton("FOOTSWITCH_1");
        footswitch1ButtonTip.pressedAction().setActionMatcher(midiIn2.createCCActionMatcher(FOOTSWITCH_CHANNEL, FOOTSWITCH_CC_TIP, FOOTSWITCH_ON_VALUE));
        final HardwareButton footswitch1ButtonRing = surface.createHardwareButton("FOOTSWITCH_1_RING");
        footswitch1ButtonRing.pressedAction().setActionMatcher(midiIn2.createCCActionMatcher(FOOTSWITCH_CHANNEL, FOOTSWITCH_CC_RING, FOOTSWITCH_ON_VALUE));

        final Transport transport = viewControl.getTransport();
        transport.defaultLaunchQuantization().markInterested();

        TrackBank trackBank = getHost().createTrackBank(Globals.NUMBER_OF_TRACKS, Globals.NUMBER_OF_SENDS, Globals.NUMBER_OF_SCENES);

        Clip cursorClip = getHost().createLauncherCursorClip(Globals.NUMBER_OF_TRACKS, Globals.NUMBER_OF_SCENES);
        cursorClip.getLoopLength().markInterested();

        // RecordUtils.recordClip reads the arm state of each track and the recording state of each slot
        for (int i = 0; i < Globals.NUMBER_OF_TRACKS; i++)
        {
            final Track track = trackBank.getItemAt(i);
            track.arm().markInterested();

            final ClipLauncherSlotBank clipLauncher = track.clipLauncherSlotBank();
            for (int j = 0; j < Globals.NUMBER_OF_SCENES; j++)
            {
                clipLauncher.getItemAt(j).isRecording().markInterested();
            }
        }

        mainLayer.bindPressed(footswitch1ButtonTip, () -> {
            if (footswitch1RingPending) {
                footswitch1RingPending = false;
                transport.resetAutomationOverrides();
            } else {
                footswitch1TipPending = true;
                getHost().scheduleTask(() -> {
                    if (footswitch1TipPending) {
                        footswitch1TipPending = false;
                        RecordUtils.recordClip(getHost(), application, trackBank, detailEditor, transport, cursorClip, CommonState.getInstance().isQuantizeClipLengthAfterRecord());
                    }
                }, FOOTSWITCH_BINDING_CALLBACK_DELAY);
            }
        });
        mainLayer.bindPressed(footswitch1ButtonRing, () -> {
            if (footswitch1TipPending) {
                footswitch1TipPending = false;
                transport.resetAutomationOverrides();
            } else {
                footswitch1RingPending = true;
                getHost().scheduleTask(() -> {
                    if (footswitch1RingPending) {
                        footswitch1RingPending = false;
                        ClipUtils.delete(application, cursorClip);
                    }
                }, FOOTSWITCH_BINDING_CALLBACK_DELAY);
            }
        });
    }

    private void handleLeftNavigation() {
        clipSceneCursor.navigateTrackLeft();
    }

    private void handleRightNavigation() {
        clipSceneCursor.navigateTrackRight();
    }

    private void handleUpNavigation() {
        clipSceneCursor.navigateSceneUp();
    }

    private void handleDownNavigation() {
        clipSceneCursor.navigateSceneDown();
    }

    private void handleLayoutChange(final String v) {
        panelLayout = v;
        laneZoomAccumulator = 0;
        currentLayoutType = LayoutType.toType(v);
        midiProcessor.sendLayoutCommand(currentLayoutType);
    }

    @Override
    public void flush() {
        midiProcessor.doFlush();
    }

}
