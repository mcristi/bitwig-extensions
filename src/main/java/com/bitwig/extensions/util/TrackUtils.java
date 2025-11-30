package com.bitwig.extensions.util;

import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.CursorTrack;
import com.bitwig.extension.controller.api.PinnableCursorDevice;
import com.bitwig.extension.controller.api.Track;
import com.bitwig.extension.controller.api.TrackBank;
import com.bitwig.extensions.Globals;


public class TrackUtils {

    private TrackUtils() {
    }

    public static void setSend(TrackBank trackBank, int trackIndex, int sendIndex, int value) {
        trackBank.getItemAt(trackIndex).sendBank().getItemAt(sendIndex).set(value, 128);
    }

    public static void setVolume(TrackBank trackBank, CursorTrack cursorTrack, int value) {
        int trackPosition = cursorTrack.position().get();
        trackBank.getItemAt(trackPosition).volume().set(value, 128);
    }

    public static void setPan(TrackBank trackBank, CursorTrack cursorTrack, int value) {
        int trackPosition = cursorTrack.position().get();
        trackBank.getItemAt(trackPosition).pan().set(value, 128);
    }

    public static void stop(TrackBank trackBank, CursorTrack cursorTrack) {
        int trackPosition = cursorTrack.position().get();
        trackBank.getItemAt(trackPosition).stop();
    }

    public static void unarmAll(TrackBank trackBank) {
        for (int i = 0; i < trackBank.itemCount().get(); i++) {
            try {
                Track track = trackBank.getItemAt(i);
                if (track.exists().get()) {
                    track.arm().set(false);
                }
            } catch (Exception e) {
                break; // Stop iterating if we hit an invalid index
            }
        }
    }

    public static void activate(TrackBank trackBank, int index) {
        trackBank.getItemAt(index).isActivated().set(true);
    }

    public static void deactivate(TrackBank trackBank, int index) {
        Track track = trackBank.getItemAt(index);
        track.arm().set(false);
        track.isActivated().set(false);
    }

    public static void arm(ControllerHost host, TrackBank trackBank, CursorTrack cursorTrack, PinnableCursorDevice cursorDevice, int index, boolean openWindow) {
        TrackUtils.unarmAll(trackBank);

        Track track = trackBank.getItemAt(index);
        track.arm().set(true);
        track.selectInEditor();

        cursorTrack.selectChannel(track);

        cursorDevice.selectFirst();
        // NOTE: isWindowOpen().set(false) is not working without scheduling
        host.scheduleTask(() -> cursorDevice.isWindowOpen().set(openWindow), Globals.VISUAL_FEEDBACK_TIMEOUT);
    }
}
