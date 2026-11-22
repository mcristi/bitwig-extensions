package com.bitwig.extensions.util;

import com.bitwig.extension.controller.api.Application;
import com.bitwig.extension.controller.api.Clip;
import com.bitwig.extension.controller.api.ClipLauncherSlot;
import com.bitwig.extension.controller.api.ClipLauncherSlotBank;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.CursorTrack;
import com.bitwig.extension.controller.api.DetailEditor;
import com.bitwig.extension.controller.api.SourceSelector;
import com.bitwig.extension.controller.api.Track;
import com.bitwig.extension.controller.api.TrackBank;
import com.bitwig.extension.controller.api.Transport;
import com.bitwig.extensions.Globals;

import java.util.ArrayList;
import java.util.List;

public class RecordUtils
{
    private static final int RECORDING_END_POLL_INTERVAL = 100;
    private static final int RECORDING_END_MAX_POLLS = 100; // give up waiting after 10 seconds

    public static void recordClip(ControllerHost host, Application application,
                                  TrackBank trackBank, DetailEditor detailEditor, Transport transport,
                                  Clip cursorClip, boolean quantizeClipLengthAfterRecord) {
        // Stop every recording clip, not just the selected one
        final List<ClipLauncherSlot> recordingSlots = new ArrayList<>();
        for (int i = 0; i < trackBank.getSizeOfBank(); i++) {
            final ClipLauncherSlotBank slotBank = trackBank.getItemAt(i).clipLauncherSlotBank();
            for (int j = 0; j < slotBank.getSizeOfBank(); j++) {
                final ClipLauncherSlot slot = slotBank.getItemAt(j);
                if (slot.isRecording().get()) {
                    slot.launch();
                    recordingSlots.add(slot);
                }
            }
        }

        if (!recordingSlots.isEmpty()) {
            ApplicationUtils.showEditLayout(application);
            host.scheduleTask(detailEditor::zoomToFitSelectionOrAll, Globals.VISUAL_FEEDBACK_TIMEOUT);

            if (quantizeClipLengthAfterRecord) {
                quantizeWhenStopped(host, detailEditor, transport, cursorClip, recordingSlots, RECORDING_END_MAX_POLLS);
            }
            return;
        }

        // Only switch to the edit layout when a recording actually starts
        boolean layoutShown = false;
        for (int i = 0; i < Globals.NUMBER_OF_TRACKS; i++) {
            final Track track = trackBank.getItemAt(i);

            if (track.arm().get()) {
                if (!layoutShown) {
                    ApplicationUtils.showEditLayout(application);
                    layoutShown = true;
                }
                track.recordNewLauncherClip(0);

                // TODO: enable Follow Playback. No API currently
            }
        }
    }

    /** The clips keep recording until the launch quantization boundary, so wait for that before quantizing. */
    private static void quantizeWhenStopped(ControllerHost host, DetailEditor detailEditor, Transport transport,
                                            Clip cursorClip, List<ClipLauncherSlot> slots, int pollsLeft) {
        final boolean stillRecording = slots.stream().anyMatch(slot -> slot.isRecording().get());
        host.scheduleTask(() -> {
            if (stillRecording && pollsLeft > 0) {
                quantizeWhenStopped(host, detailEditor, transport, cursorClip, slots, pollsLeft - 1);
            } else {
                quantizeNext(host, detailEditor, transport, cursorClip, slots, 0);
            }
        }, RECORDING_END_POLL_INTERVAL);
    }

    /**
     * The loop length is only reachable through the cursor clip, so select each recorded slot in turn
     * and give the cursor clip time to follow before quantizing it.
     */
    private static void quantizeNext(ControllerHost host, DetailEditor detailEditor, Transport transport,
                                     Clip cursorClip, List<ClipLauncherSlot> slots, int index) {
        if (index >= slots.size()) {
            host.scheduleTask(detailEditor::zoomToFitSelectionOrAll, Globals.VISUAL_FEEDBACK_TIMEOUT);
            return;
        }

        slots.get(index).select();
        host.scheduleTask(() -> {
            quantizeClipLength(cursorClip, transport);
            quantizeNext(host, detailEditor, transport, cursorClip, slots, index + 1);
        }, Globals.VISUAL_FEEDBACK_TIMEOUT);
    }

    private static void quantizeClipLength(Clip clip, Transport transport) {
        String launchQuantization = transport.defaultLaunchQuantization().get();

        if (launchQuantization.equals("1/4")) {
            floorClipLength(clip, Globals.BEATS_PER_BAR); // whole bars
        } else if (launchQuantization.equals("1/8")) {
            floorClipLength(clip, 1); // whole beats
        }
    }

    /** Rounds the loop length down to a multiple of unit. Clips shorter than one bar are left as recorded. */
    private static void floorClipLength(Clip clip, double unit) {
        double clipLength = clip.getLoopLength().get();
        if (clipLength < Globals.BEATS_PER_BAR) {
            return;
        }
        clip.getLoopLength().set(Math.floor(clipLength / unit) * unit);
    }

    public static void switchSource(ControllerHost host, TrackBank trackBank, CursorTrack cursorTrack) {
        int trackPosition = cursorTrack.position().get();
        Track track = trackBank.getItemAt(trackPosition);
        SourceSelector inputSelector = track.sourceSelector();
        host.showPopupNotification("Has input: " + inputSelector.hasAudioInputSelected().get());
        // TODO: set input source. No API currently
    }
}
