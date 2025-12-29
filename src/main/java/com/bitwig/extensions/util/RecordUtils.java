package com.bitwig.extensions.util;

import com.bitwig.extension.controller.api.Application;
import com.bitwig.extension.controller.api.Clip;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.CursorTrack;
import com.bitwig.extension.controller.api.DetailEditor;
import com.bitwig.extension.controller.api.SourceSelector;
import com.bitwig.extension.controller.api.Track;
import com.bitwig.extension.controller.api.TrackBank;
import com.bitwig.extension.controller.api.Transport;
import com.bitwig.extensions.CommonState;
import com.bitwig.extensions.Globals;

public class RecordUtils
{
    public static void recordClip(ControllerHost host, Application application,
                                  TrackBank trackBank, DetailEditor detailEditor, Transport transport,
                                  Clip cursorClip, boolean quantizeClipLengthAfterRecord) {
        ApplicationUtils.showEditLayout(application);

        for (int i = 0; i < Globals.NUMBER_OF_TRACKS; i++) {
            final Track track = trackBank.getItemAt(i);
            Integer recordingClipIndex = CommonState.getInstance().getTrackRecordingClipIndex(i);

            if (recordingClipIndex == null && track.arm().get()) {
                track.recordNewLauncherClip(0);

                // TODO: enable Follow Playback. No API currently
            } else if (recordingClipIndex != null) {
                track.clipLauncherSlotBank().launch(recordingClipIndex);

                host.scheduleTask(detailEditor::zoomToFit, Globals.VISUAL_FEEDBACK_TIMEOUT);
                if (quantizeClipLengthAfterRecord) {
                    host.scheduleTask(() -> { // Delay length quantization to ensure the clip is launched
                        quantizeClipLength(cursorClip, transport);
                    }, 1000);
                }
            }
        }
    }

    private static void quantizeClipLength(Clip clip, Transport transport) {
        String launchQuantization = transport.defaultLaunchQuantization().get();
        double clipLength = clip.getLoopLength().get();

        if (launchQuantization.equals("1/4")) {
            // Calculate the nearest bar
            clip.getLoopLength().set(Math.floor(clipLength / Globals.BEATS_PER_BAR) * Globals.BEATS_PER_BAR);
        } else if (launchQuantization.equals("1/8")) {
            // Calculate the nearest beat
            clip.getLoopLength().set(Math.floor(clipLength));
        }
    }

    public static void switchSource(ControllerHost host, TrackBank trackBank, CursorTrack cursorTrack) {
        int trackPosition = cursorTrack.position().get();
        Track track = trackBank.getItemAt(trackPosition);
        SourceSelector inputSelector = track.sourceSelector();
        host.showPopupNotification("Has input: " + inputSelector.hasAudioInputSelected().get());
        // TODO: set input source. No API currently
    }
}
