package com.bitwig.extensions.controllers.akai.lpd8_mkii;

import com.bitwig.extension.controller.ControllerExtension;
import com.bitwig.extension.controller.ControllerExtensionDefinition;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.MidiIn;
import com.bitwig.extension.controller.api.NoteInput;
import com.bitwig.extensions.framework.values.Midi;


public class LPD8MKIIExtension extends ControllerExtension
{
    // MIDI Constants
    private static final int CONTROL_CHANGE = 176;
    private static final int PROGRAM_CHANGE = 192;

    private static final int ON = 127, OFF = 0;

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
        if (status == CONTROL_CHANGE && data2 == ON)
        {
            // Handle control change messages
        } else if (status == PROGRAM_CHANGE) {
            // Handle program change messages
        }
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
