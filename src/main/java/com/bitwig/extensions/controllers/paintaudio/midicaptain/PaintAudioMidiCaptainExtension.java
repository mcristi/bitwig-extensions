package com.bitwig.extensions.controllers.paintaudio.midicaptain;

import com.bitwig.extension.controller.ControllerExtension;
import com.bitwig.extension.controller.ControllerExtensionDefinition;
import com.bitwig.extension.controller.api.Application;
import com.bitwig.extension.controller.api.Arranger;
import com.bitwig.extension.controller.api.Clip;
import com.bitwig.extension.controller.api.ClipLauncherSlotBank;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.CursorDeviceFollowMode;
import com.bitwig.extension.controller.api.CursorRemoteControlsPage;
import com.bitwig.extension.controller.api.CursorTrack;
import com.bitwig.extension.controller.api.DetailEditor;
import com.bitwig.extension.controller.api.HardwareControlType;
import com.bitwig.extension.controller.api.MidiIn;
import com.bitwig.extension.controller.api.PinnableCursorDevice;
import com.bitwig.extension.controller.api.RemoteControl;
import com.bitwig.extension.controller.api.SceneBank;
import com.bitwig.extension.controller.api.SourceSelector;
import com.bitwig.extension.controller.api.Track;
import com.bitwig.extension.controller.api.TrackBank;
import com.bitwig.extension.controller.api.Transport;
import com.bitwig.extensions.CommonState;
import com.bitwig.extensions.Globals;
import com.bitwig.extensions.util.ApplicationUtils;
import com.bitwig.extensions.util.ClipUtils;
import com.bitwig.extensions.util.DeviceUtils;
import com.bitwig.extensions.util.RecordUtils;
import com.bitwig.extensions.util.SceneUtils;
import com.bitwig.extensions.util.TrackUtils;
import static com.bitwig.extension.controller.api.Application.PANEL_LAYOUT_ARRANGE;
import static com.bitwig.extension.controller.api.Application.PANEL_LAYOUT_MIX;

public class PaintAudioMidiCaptainExtension extends ControllerExtension
{
    // Midi CC mappings
    private static final int B1 = 53;
    private static final int B2 = 54;
    private static final int B3 = 55;
    private static final int B4 = 56;
    private static final int UP = 58;

    private static final int BA = 65, BA_LONG = 57;
    private static final int BB = 67, BB_LONG = 66;
    private static final int BC = 60;
    private static final int BD = 68, BD_LONG = 63;
    private static final int DOWN = 69;

    private static final int EXP1 = 50, EXP2 = 51, ENCODER = 52;

    // Constants
    private static final int ON = 127, OFF = 0;

    // State
    private enum ExpressionMode {
        VOLUME,
        DEVICE_PARAM_1,
        DEVICE_PARAM_2,
        PAN
    }
    private ExpressionMode expressionMode = ExpressionMode.VOLUME;

    private enum FocusMode {
        MIX,
        ARRANGE,
    }
    private FocusMode focusMode = FocusMode.MIX;

    private boolean openWindowOnArm = true;

    // API objects
    private ControllerHost host;
    private Transport transport;
    private Application application;
    private TrackBank trackBank;
    private SceneBank sceneBank;
    private Clip cursorClip;
    private DetailEditor detailEditor;
    private Arranger arranger;
    private CursorTrack cursorTrack;
    private PinnableCursorDevice cursorDevice;
    private CursorRemoteControlsPage cursorRemoteControlsPage;

    protected PaintAudioMidiCaptainExtension(
        final ControllerExtensionDefinition controllerExtensionDefinition,
        final ControllerHost host)
    {
        super(controllerExtensionDefinition, host);
    }

    @Override
    public void init()
    {
        host = getHost();

        application = host.createApplication();
        application.panelLayout().addValueObserver((layout) -> {
            if (layout.equals(PANEL_LAYOUT_ARRANGE)) {
                focusMode = FocusMode.ARRANGE;
            } else if (layout.equals(PANEL_LAYOUT_MIX)) {
                focusMode = FocusMode.MIX;
            }
        });

        detailEditor = host.createDetailEditor();
        arranger = host.createArranger();

        transport = host.createTransport();
        transport.isPlaying().markInterested();
        transport.defaultLaunchQuantization().markInterested();
        transport.isArrangerRecordEnabled().markInterested();
        transport.isArrangerAutomationWriteEnabled().markInterested();
        transport.isArrangerLoopEnabled().markInterested();
        transport.arrangerLoopStart().markInterested();
        transport.arrangerLoopDuration().markInterested();

        trackBank = host.createTrackBank(Globals.NUMBER_OF_TRACKS, Globals.NUMBER_OF_SENDS, Globals.NUMBER_OF_SCENES);
        trackBank.itemCount().markInterested();

        sceneBank = trackBank.sceneBank();

        for (int i = 0; i < Globals.NUMBER_OF_TRACKS; i++) {
            Track track = trackBank.getItemAt(i);
            track.arm().markInterested();
            track.trackType().markInterested();
            track.exists().markInterested();
            track.isActivated().markInterested();

            int trackIndex = i;
            track.clipLauncherSlotBank().addIsRecordingObserver((int clipIndex, boolean status) -> {
                CommonState.getInstance().setTrackRecordingClipIndex(trackIndex, clipIndex, status);
            });

            SourceSelector inputSelector = track.sourceSelector();
            inputSelector.hasAudioInputSelected().markInterested();

            for (int j = 0; j < Globals.NUMBER_OF_SCENES; j++) {
                sceneBank.getScene(j).exists().markInterested();

                ClipLauncherSlotBank clipLauncherSlotBank = track.clipLauncherSlotBank();
                clipLauncherSlotBank.getItemAt(j).isPlaying().markInterested();
            }
        }

        cursorClip = host.createLauncherCursorClip(Globals.NUMBER_OF_TRACKS, Globals.NUMBER_OF_SCENES);
        cursorClip.exists().markInterested();
        cursorClip.getLoopLength().markInterested();
        cursorClip.getLoopStart().markInterested();
        cursorClip.getPlayStart().markInterested();
        cursorClip.getPlayStop().markInterested();

        cursorTrack = host.createCursorTrack("CURSOR_TRACK", "My Cursor Track", Globals.NUMBER_OF_SENDS, Globals.NUMBER_OF_SCENES, true);
        cursorTrack.position().markInterested();

        cursorDevice = cursorTrack.createCursorDevice("CURSOR_DEVICE", "My Cursor Device", Globals.NUMBER_OF_SENDS, CursorDeviceFollowMode.FOLLOW_SELECTION);

        cursorRemoteControlsPage = cursorDevice.createCursorRemoteControlsPage(9);
        cursorRemoteControlsPage.hasNext().markInterested();
        cursorRemoteControlsPage.hasPrevious().markInterested();
        cursorRemoteControlsPage.selectedPageIndex().markInterested();
        cursorRemoteControlsPage.setHardwareLayout(HardwareControlType.KNOB, 9);

        for (int i = 0; i < 9; i++) {
            final RemoteControl parameter = cursorRemoteControlsPage.getParameter(i);
            parameter.markInterested();
            parameter.exists().markInterested();
            parameter.setIndication(true);
        }

        // Create Midi Inputs
        MidiIn midiIn0 = host.getMidiInPort(0);
        midiIn0.setMidiCallback(this::onMidi);
        midiIn0.setSysexCallback(this::onSysex);

        //      host.showPopupNotification("PaintAudio MidiCaptain extension Initialized");
    }

    private void onMidi(int status, int data1, int data2) {
        if (status != 176) {
            return; // the midi event is a note
        }

        // AKAI LPD8 (is using another script, so no worry for mapping overlap)
        //  CC 13 - CC 22 => knobs
        //  CC 71 - CC 78 => pads

        if (data1 >= 50 && data1 <= 70) {
            handleMidiEvent(data1, data2);
        }
    }

    private void onSysex(String data) {

    }

    public void handleMidiEvent(int data1, int data2) {
//        host.showPopupNotification("Data received: " + data1 + " " + data2);

//        Action[] actions = application.getActions();
//        for (Action action : actions) {
//            if (action.getName().contains("Follow") || action.getName().contains("follow")) {
//                host.println("Action: " + action.getName() + " : " + action.getId());
//            }
//        }

        switch (data1) {
            case B1:
                if (focusMode == FocusMode.MIX) {
                    int loopIncrement = transport.defaultLaunchQuantization().get().equals("1/4") ? 4 : 1;

                    if (data2 == 1) {
                        cursorClip.getLoopStart().inc(loopIncrement);
                        cursorClip.getLoopLength().inc(-loopIncrement);
                    } else if (data2 == 2) {
                        cursorClip.getLoopStart().inc(-loopIncrement);
                        cursorClip.getLoopLength().inc(loopIncrement);
                    }
                    host.scheduleTask(() -> {
                        cursorClip.getPlayStart().set(cursorClip.getLoopStart().get());
                        detailEditor.zoomToFit();
                    }, Globals.VISUAL_FEEDBACK_TIMEOUT);
                } else {
                    int loopIncrement = transport.defaultLaunchQuantization().get().equals("1/4") ? 4 : 1;

                    if (data2 == 1) {
                        transport.arrangerLoopStart().inc(loopIncrement);
                    } else if (data2 == 2) {
                        transport.arrangerLoopStart().inc(-loopIncrement);
                    }
                }
                break;

            case B2:
                if (focusMode == FocusMode.MIX) {
                    int loopIncrement = transport.defaultLaunchQuantization().get().equals("1/4") ? 4 : 1;

                    if (data2 == 1) {
                        cursorClip.getLoopLength().inc(-loopIncrement);
                    } else if (data2 == 2) {
                        cursorClip.getLoopLength().inc(loopIncrement);
                    }
                    host.scheduleTask(() -> {
                        cursorClip.getPlayStart().set(cursorClip.getLoopStart().get());
                        detailEditor.zoomToFit();
                    }, Globals.VISUAL_FEEDBACK_TIMEOUT);
                } else {
                    int loopIncrement = transport.defaultLaunchQuantization().get().equals("1/4") ? 4 : 1;

                    if (data2 == 1) {
                        transport.arrangerLoopDuration().inc(loopIncrement);
                    } else if (data2 == 2) {
                        transport.arrangerLoopDuration().inc(-loopIncrement);
                    }
                }
                break;

            case B3:
                if (data2 == 1) {
                    this.expressionMode = ExpressionMode.VOLUME;
                    host.showPopupNotification("Expression Mode: Volume");
                } else if (data2 == 2) {
                    this.expressionMode = ExpressionMode.DEVICE_PARAM_1;
                    host.showPopupNotification("Expression Mode: Device Param 1");
                } else if (data2 == 3) {
                    this.expressionMode = ExpressionMode.DEVICE_PARAM_2;
                    host.showPopupNotification("Expression Mode: Device Param 2");
                } else if (data2 == 4) {
                    this.expressionMode = ExpressionMode.PAN;
                    host.showPopupNotification("Expression Mode: Pan");
                } else if (data2 == 50) {
                    this.openWindowOnArm = !this.openWindowOnArm;
                    host.scheduleTask(() -> cursorDevice.isWindowOpen().set(this.openWindowOnArm), Globals.VISUAL_FEEDBACK_TIMEOUT);
                    host.showPopupNotification("Open First Device Window On Arm: " + this.openWindowOnArm);
                }
                break;

            case B4:
                if (data2 == 50) {
                    CommonState.getInstance().toggleQuantizeClipLengthAfterRecord();
                    host.showPopupNotification("Quantize Clip Length After Record: " + CommonState.getInstance().isQuantizeClipLengthAfterRecord());
                    break;
                }

                final String quantization = switch (data2)
                {
                    case 16 -> "1/16";
                    case 8 -> "1/8";
                    case 4 -> "1/4";
                    case 1 -> "1";
                    case 0 -> "none";
                    default -> "1/4";
                };
                transport.defaultLaunchQuantization().set(quantization);
                host.showPopupNotification("Quantization: " + quantization);
                break;

            case BA:
                if (data2 == OFF) {
                    transport.isPlaying().set(false);
                } else if (data2 == ON) {
                    transport.continuePlayback();
                }
                break;
            case BA_LONG:
                if (data2 == OFF) {
                    transport.stop();
                }
                break;

            case BB:
                if (data2 == OFF) {
                    transport.tapTempo();
                }
                break;
            case BB_LONG:
                if (data2 == OFF) {
                    transport.isMetronomeEnabled().toggle();
                }
                break;

            case BC:
                // if MONO tracks (first 3) are deactivated, set the trackOffset to 3
                int trackOffset = trackBank.getItemAt(0).isActivated().get() ? 0 : 3;

                if (data2 == 1) {
                    if (focusMode == FocusMode.MIX) {
                        ApplicationUtils.showMixLayout(application);
                    } else {
                        ApplicationUtils.showArrangerLayout(application);
                    }

                    TrackUtils.arm(host, trackBank, cursorTrack, cursorDevice, trackOffset, this.openWindowOnArm);
                } else if (data2 == 2) {
                    if (focusMode == FocusMode.MIX) {
                        ApplicationUtils.showMixLayout(application);
                    } else {
                        ApplicationUtils.showArrangerLayout(application);
                    }

                    TrackUtils.arm(host, trackBank, cursorTrack, cursorDevice, trackOffset + 1, this.openWindowOnArm);
                } else if (data2 == 3) {
                    if (focusMode == FocusMode.MIX) {
                        ApplicationUtils.showMixLayout(application);
                    } else {
                        ApplicationUtils.showArrangerLayout(application);
                    }

                    TrackUtils.arm(host, trackBank, cursorTrack, cursorDevice, trackOffset + 2, this.openWindowOnArm);
                } else if (data2 == 50) {
                    if (trackOffset == 0) {
                        host.showPopupNotification("Use STEREO input");
                        TrackUtils.deactivate(trackBank, 0);
                        TrackUtils.deactivate(trackBank, 1);
                        TrackUtils.deactivate(trackBank, 2);
                        TrackUtils.activate(trackBank, 3);
                        TrackUtils.activate(trackBank, 4);
                        TrackUtils.activate(trackBank, 5);
                    } else {
                        host.showPopupNotification("Use MONO input");
                        TrackUtils.activate(trackBank, 0);
                        TrackUtils.activate(trackBank, 1);
                        TrackUtils.activate(trackBank, 2);
                        TrackUtils.deactivate(trackBank, 3);
                        TrackUtils.deactivate(trackBank, 4);
                        TrackUtils.deactivate(trackBank, 5);
                    }
                }
                break;

            case BD:
                if (data2 == OFF) {
                    if (focusMode == FocusMode.MIX) {
                        RecordUtils.recordClip(host, application, trackBank, detailEditor, transport, cursorClip, CommonState.getInstance().isQuantizeClipLengthAfterRecord());
                    } else {
                        boolean recordingState = transport.isArrangerRecordEnabled().get();
                        transport.isArrangerRecordEnabled().set(!recordingState);
                        transport.isArrangerAutomationWriteEnabled().set(!recordingState);
                        transport.isArrangerOverdubEnabled().set(!recordingState);
                    }
                }
                break;
            case BD_LONG:
                if (data2 == OFF) {
                    if (focusMode == FocusMode.MIX) {
                        ClipUtils.delete(application, cursorClip);
                    } else {
                        transport.isArrangerLoopEnabled().toggle();
                    }
                }
                break;

            case UP:
                if (data2 == OFF) {
                    if (focusMode == FocusMode.MIX) {
                        SceneUtils.launchPrev(application, sceneBank, trackBank);
                    } else {
                        arranger.zoomIn();
                    }
                }
                break;
            case DOWN:
                if (data2 == OFF) {
                    if (focusMode == FocusMode.MIX) {
                        SceneUtils.launchNext(application, sceneBank, trackBank);
                    } else {
                        arranger.zoomOut();
                    }
                }
                break;

            case EXP1:
                if (this.expressionMode == ExpressionMode.VOLUME) {
                    TrackUtils.setVolume(trackBank, cursorTrack, data2);
                } else if (this.expressionMode == ExpressionMode.DEVICE_PARAM_1) {
                    DeviceUtils.setParameter(cursorRemoteControlsPage, 0, data2);
                } else if (this.expressionMode == ExpressionMode.DEVICE_PARAM_2) {
                    DeviceUtils.setParameter(cursorRemoteControlsPage, 1, data2);
                } else if (this.expressionMode == ExpressionMode.PAN) {
                    TrackUtils.setPan(trackBank, cursorTrack, data2);
                }
                break;

            case ENCODER:
                // Map MIDI controller (0-127) to BPM range (53-180)
                double bpm = 53 + (data2 / 127.0) * (180 - 53);
                // Round to nearest integer (e.g., 66.0, 67.0, 68.0)
                bpm = Math.round(bpm * 1) / 1.0;
                // Normalize BPM to tempo range (20-666 BPM maps to 0-1)
                double normalizedTempo = (bpm - 20) / (666 - 20);

                transport.tempo().set(normalizedTempo);
                break;

            default:
                break;
        }
    }

    @Override
    public void exit()
    {
        // Perform any cleanup once the driver exits
        //      getHost().showPopupNotification("PaintAudio MidiCaptain extension Exited");
    }

    @Override
    public void flush()
    {
        // Send any updates you need here.
    }
}
