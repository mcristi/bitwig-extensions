package com.bitwig.extensions.controllers.akai.midimix;

import com.bitwig.extension.controller.ControllerExtension;
import com.bitwig.extension.controller.ControllerExtensionDefinition;
import com.bitwig.extension.controller.api.AbsoluteHardwareKnob;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.CursorRemoteControlsPage;
import com.bitwig.extension.controller.api.CursorTrack;
import com.bitwig.extension.controller.api.Device;
import com.bitwig.extension.controller.api.DeviceBank;
import com.bitwig.extension.controller.api.DrumPad;
import com.bitwig.extension.controller.api.DrumPadBank;
import com.bitwig.extension.controller.api.HardwareSurface;
import com.bitwig.extension.controller.api.MidiIn;
import com.bitwig.extension.controller.api.MidiOut;
import com.bitwig.extension.controller.api.PinnableCursorDevice;
import com.bitwig.extension.controller.api.Send;
import com.bitwig.extensions.framework.values.Midi;

public class MidiMixExtension extends ControllerExtension
{
    private static final int NUM_CHANNELS = 8;

    // Default Akai MIDI Mix CC assignments
    // Faders (CC per column)
    private static final int[] CC_FADERS = {19, 23, 27, 31, 49, 53, 57, 61};
    private static final int CC_MASTER_FADER = 62;

    // Knobs: 3 rows per column, maps to first 3 macros of each chain's first device
    private static final int[][] CC_KNOBS = {
        {16, 20, 24, 28, 46, 50, 54, 58},  // row 1 → macro 1
        {17, 21, 25, 29, 47, 51, 55, 59},  // row 2 → macro 2
        {18, 22, 26, 30, 48, 52, 56, 60},  // row 3 → macro 3
    };
    private static final int NUM_KNOB_ROWS = CC_KNOBS.length;

    // Buttons send/receive Note On/Off — note numbers double as LED addresses
    private static final int[] NOTE_MUTE      = {1, 4, 7, 10, 13, 16, 19, 22};
    private static final int[] NOTE_SOLO_MUTE = {2, 5, 8, 11, 14, 17, 20, 23};
    private static final int[] NOTE_REC_ARM   = {3, 6, 9, 12, 15, 18, 21, 24};
    private static final int NOTE_BANK_LEFT   = 25;
    private static final int NOTE_BANK_RIGHT  = 26;
    private static final int NOTE_SOLO        = 27;
    private static final int ON = 127;
    private static final int OFF = 0;

    private static final int TOTAL_PADS = 128;
    private static final double VOLUME_0DB = 0.7937005259840997;
    private static final int NUM_SENDS = 3;
    private static final String[] ROW2_OVERRIDE_PATTERNS = {"snare", "clap", "rim"};

    private ControllerHost host;
    private MidiOut midiOut;
    private CursorTrack cursorTrack;
    private DrumPadBank drumPadBank;
    private boolean hasDrumPads = false;
    private final boolean[] chainMuted = new boolean[NUM_CHANNELS];
    private final boolean[] chainActive = new boolean[NUM_CHANNELS];
    private final boolean[] padExists = new boolean[TOTAL_PADS];
    private int scrollPosition = 0;
    private final CursorRemoteControlsPage[] chainRemoteControls = new CursorRemoteControlsPage[NUM_CHANNELS];
    private final AbsoluteHardwareKnob[][] hwKnobs = new AbsoluteHardwareKnob[NUM_KNOB_ROWS][NUM_CHANNELS];
    private final Send[][] chainSends = new Send[NUM_SENDS][NUM_CHANNELS];
    private final String[] chainName = new String[NUM_CHANNELS];
    private CursorRemoteControlsPage deviceButtonControls;
    private final boolean[] buttonState = new boolean[NUM_CHANNELS];

    protected MidiMixExtension(
        final ControllerExtensionDefinition definition,
        final ControllerHost host)
    {
        super(definition, host);
    }

    @Override
    public void init()
    {
        host = getHost();

        final MidiIn midiIn = host.getMidiInPort(0);
        midiIn.setMidiCallback(this::onMidi);

        midiOut = host.getMidiOutPort(0);

        final HardwareSurface surface = host.createHardwareSurface();
        for (int row = 0; row < NUM_KNOB_ROWS; row++)
        {
            for (int col = 0; col < NUM_CHANNELS; col++)
            {
                final AbsoluteHardwareKnob knob = surface.createAbsoluteHardwareKnob("Knob-" + row + "-" + col);
                knob.setAdjustValueMatcher(midiIn.createAbsoluteCCValueMatcher(0, CC_KNOBS[row][col]));
                knob.disableTakeOver();
                hwKnobs[row][col] = knob;
            }
        }

        cursorTrack = host.createCursorTrack(NUM_SENDS, 0);
        cursorTrack.volume().markInterested();
        final PinnableCursorDevice cursorDevice = cursorTrack.createCursorDevice();
        drumPadBank = cursorDevice.createDrumPadBank(NUM_CHANNELS);

        cursorDevice.hasDrumPads().addValueObserver(this::onDrumPadsChanged);

        deviceButtonControls = cursorDevice.createCursorRemoteControlsPage("Buttons", NUM_CHANNELS, "");
        deviceButtonControls.pageCount().addValueObserver(count ->
        {
            if (count > 1)
            {
                deviceButtonControls.selectedPageIndex().set(1);
            }
        });
        for (int i = 0; i < NUM_CHANNELS; i++)
        {
            deviceButtonControls.getParameter(i).markInterested();
            final int bi = i;
            deviceButtonControls.getParameter(i).value().addValueObserver(value ->
            {
                buttonState[bi] = value > 0.5;
                sendLed(NOTE_REC_ARM[bi], buttonState[bi] ? ON : OFF);
            });
        }

        final DrumPadBank monitorBank = cursorDevice.createDrumPadBank(TOTAL_PADS);
        monitorBank.setIndication(false);
        for (int i = 0; i < TOTAL_PADS; i++)
        {
            final int pi = i;
            monitorBank.getItemAt(i).exists().addValueObserver(exists ->
            {
                padExists[pi] = exists;
                updateBankLeds();
            });
        }

        drumPadBank.scrollPosition().addValueObserver(pos ->
        {
            scrollPosition = pos;
            updateBankLeds();
        });

        for (int i = 0; i < NUM_CHANNELS; i++)
        {
            final DrumPad pad = drumPadBank.getItemAt(i);
            pad.volume().markInterested();
            pad.mute().markInterested();
            pad.exists().markInterested();

            for (int s = 0; s < NUM_SENDS; s++)
            {
                chainSends[s][i] = pad.sendBank().getItemAt(s);
            }

            final DeviceBank padDeviceBank = pad.createDeviceBank(1);
            final Device firstDevice = padDeviceBank.getDevice(0);
            chainRemoteControls[i] = firstDevice.createCursorRemoteControlsPage("Knobs", NUM_KNOB_ROWS, "");
            chainRemoteControls[i].selectedPageIndex().set(0);

            hwKnobs[0][i].setBinding(chainRemoteControls[i].getParameter(1));
            hwKnobs[1][i].setBinding(chainSends[2][i]);
            hwKnobs[2][i].setBinding(chainSends[0][i]);

            final int ni = i;
            pad.name().addValueObserver(name ->
            {
                chainName[ni] = name.toLowerCase();
                updateKnobBinding(ni);
            });

            final int idx = i;
            pad.exists().addValueObserver(exists ->
            {
                chainActive[idx] = exists;
                updateMuteLed(idx);
            });
            pad.mute().addValueObserver(muted ->
            {
                chainMuted[idx] = muted;
                updateMuteLed(idx);
                updateSoloLed();
            });
        }
    }

    private void onDrumPadsChanged(final boolean hasPads)
    {
        hasDrumPads = hasPads;
    }

    private void onMidi(final int status, final int data1, final int data2)
    {
        final int type = status & 0xF0;

        if (type == Midi.CC)
        {
            handleCC(data1, data2);
        }
        else if (type == Midi.NOTE_ON)
        {
            handleNoteOn(data1);
        }
    }

    private void handleCC(final int cc, final int value)
    {
        if (cc == CC_MASTER_FADER)
        {
            cursorTrack.volume().set(value / 127.0 * VOLUME_0DB);
            return;
        }

        for (int i = 0; i < NUM_CHANNELS; i++)
        {
            if (cc == CC_FADERS[i])
            {
                if (hasDrumPads)
                {
                    drumPadBank.getItemAt(i).volume().set(value / 127.0 * VOLUME_0DB);
                }
                return;
            }
        }
    }

    private void handleNoteOn(final int note)
    {
        for (int i = 0; i < NUM_CHANNELS; i++)
        {
            if (note == NOTE_MUTE[i] || note == NOTE_SOLO_MUTE[i])
            {
                drumPadBank.getItemAt(i).mute().toggle();
                return;
            }
        }

        for (int i = 0; i < NUM_CHANNELS; i++)
        {
            if (note == NOTE_REC_ARM[i])
            {
                final double target = buttonState[i] ? 0.0 : 1.0;
                deviceButtonControls.getParameter(i).set(target);
                return;
            }
        }

        if (note == NOTE_SOLO)
        {
            drumPadBank.clearMutedPads();
            return;
        }

        if (note == NOTE_BANK_LEFT)
        {
            drumPadBank.scrollPageForwards();
            return;
        }
        if (note == NOTE_BANK_RIGHT)
        {
            drumPadBank.scrollPageBackwards();
        }
    }

    private void updateKnobBinding(final int channel)
    {
        if (matchesRow2Override(channel))
        {
            hwKnobs[2][channel].setBinding(chainSends[1][channel]);
        }
        else
        {
            hwKnobs[2][channel].setBinding(chainSends[0][channel]);
        }
    }

    private boolean matchesRow2Override(final int channel)
    {
        final String name = chainName[channel];
        if (name == null) return false;
        for (final String pattern : ROW2_OVERRIDE_PATTERNS)
        {
            if (name.contains(pattern)) return true;
        }
        return false;
    }

    private boolean pageHasContent(final int startIndex)
    {
        if (startIndex < 0 || startIndex >= TOTAL_PADS) return false;
        for (int i = startIndex; i < startIndex + NUM_CHANNELS && i < TOTAL_PADS; i++)
        {
            if (padExists[i]) return true;
        }
        return false;
    }

    private void updateBankLeds()
    {
        sendLed(NOTE_BANK_LEFT, pageHasContent(scrollPosition + NUM_CHANNELS) ? ON : OFF);
        sendLed(NOTE_BANK_RIGHT, pageHasContent(scrollPosition - NUM_CHANNELS) ? ON : OFF);
    }

    private void updateMuteLed(final int idx)
    {
        final int led = chainActive[idx] && !chainMuted[idx] ? ON : OFF;
        sendLed(NOTE_MUTE[idx], led);
        sendLed(NOTE_SOLO_MUTE[idx], led);
    }

    private void updateSoloLed()
    {
        boolean anyMuted = false;
        for (boolean muted : chainMuted)
        {
            if (muted)
            {
                anyMuted = true;
                break;
            }
        }
        sendLed(NOTE_SOLO, anyMuted ? ON : OFF);
    }

    private void sendLed(final int note, final int value)
    {
        midiOut.sendMidi(Midi.NOTE_ON, note, value);
    }

    @Override
    public void exit()
    {
        for (int i = 0; i < NUM_CHANNELS; i++)
        {
            sendLed(NOTE_MUTE[i], OFF);
            sendLed(NOTE_REC_ARM[i], OFF);
        }
    }

    @Override
    public void flush()
    {
    }
}
