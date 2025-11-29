package com.bitwig.extensions.util;

import java.util.ArrayList;
import java.util.List;

import com.bitwig.extension.controller.api.Application;
import com.bitwig.extension.controller.api.Clip;
import com.bitwig.extension.controller.api.ClipLauncherSlot;
import com.bitwig.extension.controller.api.ClipLauncherSlotBank;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.CursorTrack;
import com.bitwig.extension.controller.api.DetailEditor;
import com.bitwig.extension.controller.api.Project;
import com.bitwig.extension.controller.api.SceneBank;
import com.bitwig.extension.controller.api.SourceSelector;
import com.bitwig.extension.controller.api.Track;
import com.bitwig.extension.controller.api.TrackBank;
import com.bitwig.extension.controller.api.Transport;
import com.bitwig.extensions.Globals;

public class RecordUtils
{
    public static void recordClip(ControllerHost host, Application application,
                                  TrackBank trackBank, SceneBank sceneBank,
                                  Project project, DetailEditor detailEditor, Transport transport,
                                  Clip cursorClip, boolean quantizeClipLengthAfterRecord) {
        List<ClipLauncherSlotBank> armedSlotBanks = new ArrayList<>();
        for (int i = 0; i < Globals.NUMBER_OF_TRACKS; i++) {
            Track track = trackBank.getItemAt(i);
            if (track.arm().get()) {
                armedSlotBanks.add(track.clipLauncherSlotBank());
            }
        }

        boolean multiTrackRecord = armedSlotBanks.size() > 1;
        for (ClipLauncherSlotBank slotBank : armedSlotBanks) {
            host.scheduleTask(() -> recordClipOnTrack(
                host, application, slotBank, sceneBank, project, detailEditor,
                transport, cursorClip, quantizeClipLengthAfterRecord, multiTrackRecord
            ), 0);
        }
    }

    private static void recordClipOnTrack(ControllerHost host, Application application,
                                          ClipLauncherSlotBank slotBank, SceneBank sceneBank,
                                          Project project, DetailEditor detailEditor, Transport transport,
                                          Clip cursorClip, boolean quantizeClipLengthAfterRecord, boolean multiTrackRecord) {
        for (int i = 0; i < Globals.NUMBER_OF_SCENES; i++) {
            if (!sceneBank.getScene(i).exists().get()) {
                project.createScene();
                host.showPopupNotification("New scene created");
            }

            ClipLauncherSlot clip = slotBank.getItemAt(i);
            if (clip.isRecording().get()) {
                clip.launch();

                // Cannot quantize clip length when multiple tracks are armed because ClipLauncherSlot interface how not expose getLoopLength(),
                //  only Clip (cursor) - which cannot select multiple clips at a time
                if (!multiTrackRecord) {
                    clip.select();
                    clip.showInEditor();

                    host.scheduleTask(detailEditor::zoomToFit, Globals.VISUAL_FEEDBACK_TIMEOUT);

                    if (quantizeClipLengthAfterRecord) {
                        host.scheduleTask(() -> { // Delay length quantization to ensure the clip is launched
                            quantizeClipLength(host, cursorClip, transport);
                        }, 1000);
                    }
                }

                break; // Stop the loop
            } else if (!clip.hasContent().get()) {
                clip.record();

                if (!multiTrackRecord) {
                    ApplicationUtils.showEditPanelLayout(application);

                    clip.select();
                    clip.showInEditor();
                    // TODO: enable Follow Playback. No API currently
                }

                break; // Stop the loop
            }
        }
    }

    private static void quantizeClipLength(ControllerHost host, Clip clip, Transport transport) {
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
