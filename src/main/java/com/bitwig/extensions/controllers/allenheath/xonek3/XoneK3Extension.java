package com.bitwig.extensions.controllers.allenheath.xonek3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.bitwig.extension.controller.ControllerExtension;
import com.bitwig.extension.controller.ControllerExtensionDefinition;
import com.bitwig.extension.controller.api.AbsoluteHardwareKnob;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.CursorDeviceFollowMode;
import com.bitwig.extension.controller.api.CursorRemoteControlsPage;
import com.bitwig.extension.controller.api.CursorTrack;
import com.bitwig.extension.controller.api.DrumPadBank;
import com.bitwig.extension.controller.api.HardwareButton;
import com.bitwig.extension.controller.api.HardwareSurface;
import com.bitwig.extension.controller.api.PinnableCursorDevice;
import com.bitwig.extension.controller.api.RelativeHardwareKnob;
import com.bitwig.extension.controller.api.RemoteControl;
import com.bitwig.extension.controller.api.Transport;

/**
 * Controls an FX Chain with a Xone:K3. The cursor device ("K3 FX Device") is pinned on the Chain in
 * Bitwig, and the controls are tied to the Chain's remote control pages by page name:
 * <ul>
 * <li>"encoders": controls 1-4 on the top encoders</li>
 * <li>"eq": controls 1-4 on the first row of knobs, 5-7 on the buttons below them</li>
 * <li>"eq shift": controls 1-4 on the first row of knobs while SHIFT is held</li>
 * <li>"guitar rig": controls 1-4 on the last row of knobs, 5-8 on the buttons below them</li>
 * <li>"looper": controls 1-4 on the first row of the button matrix</li>
 * <li>"shaperbox": controls 1-8 on the second and third row of the button matrix</li>
 * </ul>
 * LAYER restores all automation, SHIFT moves the first row of knobs to "eq shift".
 * The last matrix row holds: unmute all drum pads, two free buttons and fill (while held). A page that the device does not have leaves its controls without function.
 */
public class XoneK3Extension extends ControllerExtension
{
    private static final int PAGE_SIZE = 8;
    // turns closer together than this belong to one movement of an encoder
    private static final int TURN_GAP_MS = 300;
    // all pads of the Drum Machine, so muted pads are seen wherever they are
    private static final int DRUM_PADS = 128;

    private ControllerHost host;
    private HardwareSurface surface;
    private XoneK3Hardware hardware;
    private Transport transport;
    private DrumPadBank drumPads;
    private final List<AutomatedEncoder> automatedEncoders = new ArrayList<>();
    private final List<RemotePage> pages = new ArrayList<>();
    private boolean shiftHeld;
    private boolean automationOverrideActive;

    protected XoneK3Extension(final ControllerExtensionDefinition definition, final ControllerHost host)
    {
        super(definition, host);
    }

    @Override
    public void init()
    {
        host = getHost();
        surface = host.createHardwareSurface();
        hardware = new XoneK3Hardware(surface, host.getMidiInPort(0), host.getMidiOutPort(0));

        transport = host.createTransport();
        transport.isFillModeActive().markInterested();
        transport.isAutomationOverrideActive().addValueObserver(active -> {
            automationOverrideActive = active;
            if (!active)
            {
                automatedEncoders.forEach(encoder -> encoder.overridden = false);
            }
        });

        final CursorTrack cursorTrack = host.createCursorTrack("K3_FX_TRACK", "K3 FX Track", 0, 0, true);
        final PinnableCursorDevice cursorDevice = cursorTrack.createCursorDevice(
            "K3_FX_DEVICE", "K3 FX Device", 0, CursorDeviceFollowMode.FOLLOW_SELECTION);
        // the Drum Machine is the first device of the chain the pinned device is in
        drumPads = cursorDevice.deviceChain().createDeviceBank(1).getItemAt(0).createDrumPadBank(DRUM_PADS);
        // only watched for muted pads: no controller colour on the pads in Bitwig
        drumPads.setIndication(false);
        drumPads.hasMutedPads().markInterested();

        final RemotePage encodersPage = new RemotePage(cursorDevice, "encoders");
        final XoneColor[] encoderColors = {XoneColor.YELLOW, XoneColor.YELLOW, XoneColor.ORANGE, XoneColor.LIGHT_BLUE};
        for (int i = 0; i < 4; i++)
        {
            final int index = i;
            encodersPage.bindEncoder(hardware.topEncoders[i], hardware.topEncoderButtons[i], i, encoderColors[i],
                color -> hardware.setTopEncoderLight(index, color));
        }

        // The first row of knobs: the "eq" page, and the "eq shift" page while SHIFT is held (the
        // frequencies of the low and high band on knobs 1 and 3). The low, mid and high band buttons
        // below stay on the "eq" page, but the mid one is off while SHIFT is held.
        final RemotePage eqPage = new RemotePage(cursorDevice, "eq");
        final RemotePage eqShiftPage = new RemotePage(cursorDevice, "eq shift");
        final XoneColor[] bandColors = {XoneColor.RED, XoneColor.YELLOW, XoneColor.BLUE};
        for (int i = 0; i < 4; i++)
        {
            eqPage.bindKnob(hardware.knobs[i], i, () -> !shiftHeld);
            eqShiftPage.bindKnob(hardware.knobs[i], i, () -> shiftHeld);
        }
        for (int i = 0; i < 3; i++)
        {
            final int index = i;
            eqPage.bindToggle(hardware.knobButtons[i], 4 + i, bandColors[i], i == 1 ? () -> !shiftHeld : () -> true,
                color -> hardware.setKnobButtonLight(index, color));
        }
        hardware.shiftButton.pressedAction().setBinding(host.createAction(() -> setShift(true), () -> "Shift"));
        hardware.shiftButton.releasedAction().setBinding(host.createAction(() -> setShift(false), () -> "Shift off"));
        hardware.setShiftLight(() -> shiftHeld ? XoneColor.WHITE : XoneColor.WHITE.dim());

        final RemotePage guitarRigPage = new RemotePage(cursorDevice, "guitar rig");
        // the second button freezes the delay; the last one takes the effect out, so its light is
        // reversed: bright green while the effect is in
        final XoneColor[] guitarRigColors = {XoneColor.ORANGE, XoneColor.LIGHT_BLUE, XoneColor.ORANGE, XoneColor.GREEN};
        for (int i = 0; i < 4; i++)
        {
            final int index = 8 + i;
            guitarRigPage.bindKnob(hardware.knobs[index], i, () -> true);
            guitarRigPage.bindToggle(hardware.knobButtons[index], 4 + i, 0, i == 3, guitarRigColors[i], () -> true,
                color -> hardware.setKnobButtonLight(index, color));
        }

        final RemotePage looperPage = new RemotePage(cursorDevice, "looper");
        for (int i = 0; i < 4; i++)
        {
            final int index = i;
            looperPage.bindExclusiveToggle(hardware.gridButtons[i], i, 4, XoneColor.YELLOW,
                color -> hardware.setGridLight(index, color));
        }

        final RemotePage shaperBoxPage = new RemotePage(cursorDevice, "shaperbox");
        for (int i = 0; i < 8; i++)
        {
            final int index = 4 + i;
            shaperBoxPage.bindExclusiveToggle(hardware.gridButtons[index], i, 8, XoneColor.MAGENTA,
                color -> hardware.setGridLight(index, color));
        }

        initFunctionRow();
        hardware.init();
    }

    /** The last row of the button matrix. */
    private void initFunctionRow()
    {
        // bright while there is something to reset, dim otherwise; the two middle buttons are free
        hardware.layerButton.pressedAction().setBinding(
            host.createAction(this::restoreAutomation, () -> "Restore automation"));
        hardware.setLayerLight(() -> automationOverrideActive ? XoneColor.ORANGE : XoneColor.ORANGE.dim());

        hardware.gridButtons[12].pressedAction().setBinding(
            host.createAction(drumPads::clearMutedPads, () -> "Unmute pads"));
        hardware.setGridLight(12, () -> drumPads.hasMutedPads().get() ? XoneColor.RED : XoneColor.RED.dim());

        final HardwareButton fill = hardware.gridButtons[15];
        fill.pressedAction().setBinding(host.createAction(() -> transport.isFillModeActive().set(true), () -> "Fill on"));
        fill.releasedAction().setBinding(
            host.createAction(() -> transport.isFillModeActive().set(false), () -> "Fill off"));
        hardware.setGridLight(15,
            () -> transport.isFillModeActive().get() ? XoneColor.BLUE : XoneColor.BLUE.dim());
    }

    private void setShift(final boolean held)
    {
        shiftHeld = held;
        refreshBindings();
    }

    /**
     * Ties every knob and encoder to the page that has it now. All that lose their page are released
     * first, as two pages can share a knob (one of them while SHIFT is held).
     */
    private void refreshBindings()
    {
        pages.forEach(page -> page.refresh(false));
        pages.forEach(page -> page.refresh(true));
    }

    private void restoreAutomation()
    {
        transport.resetAutomationOverrides();
        automatedEncoders.forEach(encoder -> encoder.overridden = false);
    }

    @Override
    public void exit()
    {
        hardware.exit();
    }

    @Override
    public void flush()
    {
        surface.updateHardware();
    }

    /**
     * Bitwig only reports whether any automation is overridden, not which parameter. So an encoder
     * remembers that it was turned on an automated parameter, until it is restored.
     */
    private static final class AutomatedEncoder
    {
        private boolean overridden;
        // the value the encoder set last and when, see RemotePage.turn
        private double target;
        private long lastTurn;
    }

    /** A knob or encoder of a page: how it is tied to its parameter and released, and when it may be. */
    private static final class ControlBinding
    {
        private final BooleanSupplier enabled;
        private final Runnable bind;
        private final Runnable unbind;
        private boolean bound;

        ControlBinding(final BooleanSupplier enabled, final Runnable bind, final Runnable unbind)
        {
            this.enabled = enabled;
            this.bind = bind;
            this.unbind = unbind;
        }
    }

    /** One remote controls page of the device, found by its name, with the controls tied to it. */
    private final class RemotePage
    {
        private final String pageName;
        private final CursorRemoteControlsPage remotes;
        private final List<ControlBinding> bindings = new ArrayList<>();
        private String[] pageNames = new String[0];
        private int selectedPage = -1;
        private boolean active;

        RemotePage(final PinnableCursorDevice device, final String pageName)
        {
            this.pageName = pageName;
            pages.add(this);
            // a cursor of its own, so each page stays on its controls whatever page Bitwig shows
            remotes = device.createCursorRemoteControlsPage("K3 " + pageName, PAGE_SIZE, null);
            for (int i = 0; i < PAGE_SIZE; i++)
            {
                final RemoteControl control = remotes.getParameter(i);
                control.exists().markInterested();
                control.value().markInterested();
                control.hasAutomation().markInterested();
            }
            remotes.pageNames().addValueObserver(names -> {
                pageNames = names;
                update();
            });
            remotes.selectedPageIndex().addValueObserver(index -> {
                selectedPage = index;
                update();
            });
        }

        /** Keeps the cursor on the page with this name; the controls only work while it is there. */
        private void update()
        {
            int wanted = -1;
            for (int i = 0; i < pageNames.length; i++)
            {
                if (pageNames[i].trim().equalsIgnoreCase(pageName))
                {
                    wanted = i;
                    break;
                }
            }
            if (wanted >= 0 && selectedPage != wanted)
            {
                remotes.selectedPageIndex().set(wanted);
            }
            final boolean nowActive = wanted >= 0 && selectedPage == wanted;
            if (nowActive != active)
            {
                active = nowActive;
                refreshBindings();
            }
        }

        /** Releases the controls this page lost ({@code bind} false) or takes the ones it has now. */
        void refresh(final boolean bind)
        {
            for (final ControlBinding binding : bindings)
            {
                final boolean wanted = active && binding.enabled.getAsBoolean();
                if (bind && wanted && !binding.bound)
                {
                    binding.bind.run();
                    binding.bound = true;
                }
                else if (!bind && !wanted && binding.bound)
                {
                    binding.unbind.run();
                    binding.bound = false;
                }
            }
        }

        /**
         * A knob with end stops: it picks the value up before it changes it. It is only tied to this
         * page while {@code enabled} holds.
         */
        void bindKnob(final AbsoluteHardwareKnob knob, final int slot, final BooleanSupplier enabled)
        {
            final RemoteControl control = remotes.getParameter(slot);
            bindings.add(new ControlBinding(enabled, () -> knob.setBinding(control), knob::clearBindings));
        }

        /**
         * An encoder for an automated parameter. Its LED is dim, and bright while the encoder overrides
         * the automation; pushing the encoder restores the automation of that parameter.
         */
        void bindEncoder(final RelativeHardwareKnob encoder, final HardwareButton push, final int slot,
            final XoneColor color, final Consumer<Supplier<XoneColor>> light)
        {
            final RemoteControl control = remotes.getParameter(slot);
            final AutomatedEncoder state = new AutomatedEncoder();
            automatedEncoders.add(state);
            control.name().addValueObserver(name -> state.overridden = false);

            // Not bound to the parameter itself: such a binding keeps counting from the value it set
            // last, also after the automation is restored (rebinding does not help). The price is that
            // Bitwig does not show the parameter in its bottom bar while the encoder turns.
            bindings.add(new ControlBinding(() -> true,
                () -> encoder.setBinding(
                    host.createRelativeHardwareControlAdjustmentTarget(delta -> turn(control, state, delta))),
                () -> {
                    encoder.clearBindings();
                    state.overridden = false;
                }));
            push.pressedAction().setBinding(host.createAction(() -> {
                if (active)
                {
                    control.restoreAutomationControl();
                    state.overridden = false;
                    state.lastTurn = 0;
                }
            }, () -> "Restore automation"));
            final XoneColor idleColor = color.dim();
            light.accept(() -> {
                if (!active || !control.exists().get())
                {
                    return XoneColor.OFF;
                }
                return state.overridden && automationOverrideActive && control.hasAutomation().get() ? color : idleColor;
            });
        }

        /**
         * Moves the parameter from the value it shows now, so after a restore it goes on from where the
         * automation has it. While the encoder keeps turning the steps add up on the value set last, as
         * the shown value arrives with a delay.
         */
        private void turn(final RemoteControl control, final AutomatedEncoder state, final double delta)
        {
            final long now = System.currentTimeMillis();
            final double from = now - state.lastTurn < TURN_GAP_MS ? state.target : control.value().get();
            state.target = Math.max(0, Math.min(1, from + delta));
            state.lastTurn = now;
            control.value().setImmediately(state.target);
            if (control.hasAutomation().get())
            {
                state.overridden = true;
            }
        }

        /**
         * A button that switches an on/off parameter; lit when on, dim when off, dark without parameter.
         * While {@code enabled} does not hold the button is dark and does nothing.
         */
        void bindToggle(final HardwareButton button, final int slot, final XoneColor color,
            final BooleanSupplier enabled, final Consumer<Supplier<XoneColor>> light)
        {
            bindToggle(button, slot, 0, false, color, enabled, light);
        }

        /**
         * A button of a group in which only one is on at a time: the first {@code groupSize} controls
         * of the page. Switching one on switches the others off; the one that is on can be switched off.
         */
        void bindExclusiveToggle(final HardwareButton button, final int slot, final int groupSize,
            final XoneColor color, final Consumer<Supplier<XoneColor>> light)
        {
            bindToggle(button, slot, groupSize, false, color, () -> true, light);
        }

        /** With {@code reversedLight} the button is bright while the parameter is off. */
        void bindToggle(final HardwareButton button, final int slot, final int groupSize, final boolean reversedLight,
            final XoneColor color, final BooleanSupplier enabled, final Consumer<Supplier<XoneColor>> light)
        {
            final RemoteControl control = remotes.getParameter(slot);
            final XoneColor offColor = color.dim();
            button.pressedAction().setBinding(host.createAction(() -> {
                if (active && enabled.getAsBoolean() && control.exists().get())
                {
                    final boolean switchOn = !isOn(control);
                    for (int other = 0; switchOn && other < groupSize; other++)
                    {
                        if (other != slot)
                        {
                            switchOff(other, other + 1);
                        }
                    }
                    control.value().setImmediately(switchOn ? 1 : 0);
                }
            }, () -> "Toggle"));
            light.accept(() -> {
                if (!active || !enabled.getAsBoolean() || !control.exists().get())
                {
                    return XoneColor.OFF;
                }
                return isOn(control) != reversedLight ? color : offColor;
            });
        }

        /** Switches the controls from slot {@code from} up to, not including, slot {@code to} off. */
        void switchOff(final int from, final int to)
        {
            if (!active)
            {
                return;
            }
            for (int slot = from; slot < to; slot++)
            {
                final RemoteControl control = remotes.getParameter(slot);
                if (control.exists().get())
                {
                    control.value().setImmediately(0);
                }
            }
        }

        private boolean isOn(final RemoteControl control)
        {
            return control.value().get() >= 0.5;
        }
    }
}
