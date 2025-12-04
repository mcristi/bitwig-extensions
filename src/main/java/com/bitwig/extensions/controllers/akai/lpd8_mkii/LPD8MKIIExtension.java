package com.bitwig.extensions.controllers.akai.lpd8_mkii;

import com.bitwig.extension.controller.ControllerExtension;
import com.bitwig.extension.controller.ControllerExtensionDefinition;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.MidiIn;
import com.bitwig.extension.controller.api.NoteInput;


public class LPD8MKIIExtension extends ControllerExtension
{
    // MIDI Constants
    private static final int CHANNEL = 176;
    private static final int PROG_CHANGE = 192;

    // CC mappings
    private static final int PAD_01 = 71;
    private static final int PAD_02 = 72;
    private static final int PAD_03 = 73;
    private static final int PAD_04 = 74;
    private static final int PAD_05 = 75;
    private static final int PAD_06 = 76;
    private static final int PAD_07 = 77;
    private static final int PAD_08 = 78;

    // API objects
    private ControllerHost host;
    private NoteInput noteInput;

    protected LPD8MKIIExtension(
        final ControllerExtensionDefinition controllerExtensionDefinition,
        final ControllerHost host)
    {
        super(controllerExtensionDefinition, host);
    }

    @Override
    public void init()
    {
        host = getHost();

        MidiIn midiIn = host.getMidiInPort(0);
        midiIn.setMidiCallback(this::onMidi);
        midiIn.setSysexCallback(this::onSysex);

        noteInput = midiIn.createNoteInput("Pads", "8?????", "9?????", "D?????", "E?????");
        noteInput.setShouldConsumeEvents(true);
    }

    private void onMidi(int status, int data1, int data2)
    {
        if (status == CHANNEL && data2 > 0)
        {
            switch (data1)
            {
                case PAD_01:
                    triggerDrumPad(0);
                    break;
                case PAD_02:
                    triggerDrumPad(1);
                    break;
                case PAD_03:
                    triggerDrumPad(2);
                    break;
                case PAD_04:
                    triggerDrumPad(3);
                    break;
            }
        } else if (status == PROG_CHANGE) {
            // Handle program change messages if needed
        }
    }

    /**
     * Note messages could be used directly from controller,
     * but we trigger the notes programmatically via CC messages
     * to have the top 4 for other FX and the bottom 4 pads for the drum pad
     */
    private void triggerDrumPad(int index)
    {
        // Send MIDI note to trigger the drum pad (C1 = 36 is typically the first pad)
        noteInput.sendRawMidiEvent(0x90, 36 + index, 127);
        // Send note off after a short duration
        host.scheduleTask(() -> noteInput.sendRawMidiEvent(0x80, 36 + index, 0), 100);
    }

    private void onSysex(String data)
    {
        // Handle sysex messages if needed
    }

    @Override
    public void exit()
    {
        // Perform any cleanup once the driver exits
    }

    @Override
    public void flush()
    {
        // Send any updates you need here
    }
}
