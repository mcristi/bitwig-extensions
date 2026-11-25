package com.bitwig.extensions.controllers.allenheath.xonek3;

import java.util.function.Supplier;

import com.bitwig.extension.controller.api.AbsoluteHardwareKnob;
import com.bitwig.extension.controller.api.HardwareButton;
import com.bitwig.extension.controller.api.HardwareSurface;
import com.bitwig.extension.controller.api.MidiIn;
import com.bitwig.extension.controller.api.MidiOut;
import com.bitwig.extension.controller.api.MultiStateHardwareLight;
import com.bitwig.extension.controller.api.RelativeHardwareKnob;

/**
 * The controls and LEDs of a Xone:K3 running its factory map, which is the Xone:K2 map on MIDI channel 15.
 * The numbers are the ones of Bitwig's own Xone:K3 script.
 */
public final class XoneK3Hardware
{
    private static final int CHANNEL = 0xE;

    // top encoders: CC 0-3, push notes 0x34-0x37, LED 0x1E-0x21
    private static final int TOP_ENCODER_CC = 0x00;
    private static final int TOP_ENCODER_NOTE = 0x34;
    private static final int TOP_ENCODER_LED = 0x1E;
    // 4 faders, left to right
    private static final int FADER_CC = 0x10;
    // 3 rows of 4 pots, top row first
    private static final int KNOB_CC = 0x04;
    // the buttons below the pots, top row first; their LEDs are 0-11
    private static final int[] KNOB_BUTTON_NOTE = {0x30, 0x31, 0x32, 0x33, 0x2C, 0x2D, 0x2E, 0x2F, 0x28, 0x29, 0x2A, 0x2B};
    // the 4x4 matrix below the faders, top row first; its LEDs are 0x0C-0x1B
    private static final int[] GRID_NOTE = {
        0x24, 0x25, 0x26, 0x27, 0x20, 0x21, 0x22, 0x23, 0x1C, 0x1D, 0x1E, 0x1F, 0x18, 0x19, 0x1A, 0x1B};
    private static final int GRID_LED = 0x0C;
    // the SHIFT button, bottom right
    private static final int SHIFT_NOTE = 0x0F;
    private static final int SHIFT_LED = 0x1D;
    private static final int LED_COUNT = 34;

    private static final String LATCHING_LAYERS_OFF = "F0 00 00 1A 50 15 00 0D 00 F7";
    private static final String GLOBAL_CHANNEL_15 = "F0 00 00 1A 50 15 00 05 0E F7";

    public final RelativeHardwareKnob[] topEncoders = new RelativeHardwareKnob[4];
    public final HardwareButton[] topEncoderButtons = new HardwareButton[4];
    public final AbsoluteHardwareKnob[] knobs = new AbsoluteHardwareKnob[12];
    public final AbsoluteHardwareKnob[] faders = new AbsoluteHardwareKnob[4];
    public final HardwareButton[] knobButtons = new HardwareButton[12];
    public final HardwareButton[] gridButtons = new HardwareButton[16];
    public final HardwareButton shiftButton;

    private final HardwareSurface surface;
    private final MidiIn midiIn;
    private final MidiOut midiOut;
    private final MultiStateHardwareLight[] topEncoderLights = new MultiStateHardwareLight[4];
    private final MultiStateHardwareLight[] knobButtonLights = new MultiStateHardwareLight[12];
    private final MultiStateHardwareLight[] gridLights = new MultiStateHardwareLight[16];
    private final MultiStateHardwareLight shiftLight;

    public XoneK3Hardware(final HardwareSurface surface, final MidiIn midiIn, final MidiOut midiOut)
    {
        this.surface = surface;
        this.midiIn = midiIn;
        this.midiOut = midiOut;

        for (int i = 0; i < 4; i++)
        {
            topEncoders[i] = createEncoder("ENCODER " + (i + 1), TOP_ENCODER_CC + i);
            topEncoderButtons[i] = createButton("ENCODER PUSH " + (i + 1), TOP_ENCODER_NOTE + i);
            topEncoderLights[i] = createLight("ENCODER LED " + (i + 1), TOP_ENCODER_LED + i, topEncoderButtons[i]);
        }
        for (int i = 0; i < 4; i++)
        {
            faders[i] = surface.createAbsoluteHardwareKnob("FADER " + (i + 1));
            faders[i].setAdjustValueMatcher(midiIn.createAbsoluteCCValueMatcher(CHANNEL, FADER_CC + i));
        }
        for (int i = 0; i < 12; i++)
        {
            final String position = (i / 4 + 1) + "-" + (i % 4 + 1);
            knobs[i] = surface.createAbsoluteHardwareKnob("KNOB " + position);
            knobs[i].setAdjustValueMatcher(midiIn.createAbsoluteCCValueMatcher(CHANNEL, KNOB_CC + i));
            knobButtons[i] = createButton("KNOB BUTTON " + position, KNOB_BUTTON_NOTE[i]);
            knobButtonLights[i] = createLight("KNOB BUTTON LED " + position, i, knobButtons[i]);
        }
        for (int i = 0; i < 16; i++)
        {
            final String position = (i / 4 + 1) + "-" + (i % 4 + 1);
            gridButtons[i] = createButton("GRID " + position, GRID_NOTE[i]);
            gridLights[i] = createLight("GRID LED " + position, GRID_LED + i, gridButtons[i]);
        }
        shiftButton = createButton("SHIFT", SHIFT_NOTE);
        shiftLight = createLight("SHIFT LED", SHIFT_LED, shiftButton);
    }

    public void setShiftLight(final Supplier<XoneColor> color)
    {
        shiftLight.state().setValueSupplier(color::get);
    }

    /** Puts the unit in the state the numbers above are for. */
    public void init()
    {
        midiOut.sendSysex(LATCHING_LAYERS_OFF);
        midiOut.sendSysex(GLOBAL_CHANNEL_15);
    }

    /** Hands the LEDs back to the unit. */
    public void exit()
    {
        for (int led = 0; led < LED_COUNT; led++)
        {
            midiOut.sendSysex(new byte[] {(byte)0xF0, 0x00, 0x00, 0x1A, 0x50, 0x15, 0x00, 0x0E, (byte)led, (byte)0xF7});
        }
    }

    public void setTopEncoderLight(final int index, final Supplier<XoneColor> color)
    {
        topEncoderLights[index].state().setValueSupplier(color::get);
    }

    public void setKnobButtonLight(final int index, final Supplier<XoneColor> color)
    {
        knobButtonLights[index].state().setValueSupplier(color::get);
    }

    public void setGridLight(final int index, final Supplier<XoneColor> color)
    {
        gridLights[index].state().setValueSupplier(color::get);
    }

    private RelativeHardwareKnob createEncoder(final String name, final int cc)
    {
        final RelativeHardwareKnob encoder = surface.createRelativeHardwareKnob(name);
        encoder.setAdjustValueMatcher(midiIn.createRelative2sComplementCCValueMatcher(CHANNEL, cc, 40));
        encoder.setStepSize(0.025);
        return encoder;
    }

    private HardwareButton createButton(final String name, final int note)
    {
        final HardwareButton button = surface.createHardwareButton(name);
        button.pressedAction().setPressureActionMatcher(midiIn.createNoteOnVelocityValueMatcher(CHANNEL, note));
        button.releasedAction().setActionMatcher(midiIn.createNoteOffActionMatcher(CHANNEL, note));
        button.isPressed().markInterested();
        return button;
    }

    private MultiStateHardwareLight createLight(final String name, final int led, final HardwareButton button)
    {
        final MultiStateHardwareLight light = surface.createMultiStateHardwareLight(name);
        light.state().setValue(XoneColor.OFF);
        light.state().onUpdateHardware(state -> sendColor(led, state instanceof final XoneColor c ? c : XoneColor.OFF));
        button.setBackgroundLight(light);
        return light;
    }

    private void sendColor(final int led, final XoneColor color)
    {
        midiOut.sendSysex(new byte[] {
            (byte)0xF0, 0x00, 0x00, 0x1A, 0x50, 0x15, 0x00, 0x0E, (byte)led, (byte)color.red, (byte)color.green,
            (byte)color.blue, (byte)color.brightness, (byte)0xF7});
        // Bitwig's own Xone:K3 script pauses 3 ms after every LED message; kept so the unit is not flooded
        try
        {
            Thread.sleep(3);
        }
        catch (final InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
    }
}
