package com.bitwig.extensions.util;

import com.bitwig.extension.controller.api.Application;
import com.bitwig.extension.controller.api.SceneBank;
import com.bitwig.extension.controller.api.TrackBank;
import com.bitwig.extensions.Globals;
import static com.bitwig.extension.controller.api.Application.PANEL_LAYOUT_MIX;


public class SceneUtils {

    private SceneUtils() {
    }

    public static void launchNext(Application application, SceneBank sceneBank, TrackBank trackBank) {
        // -1 when nothing is playing, so the first press launches the first scene
        int nextSceneIndex = SceneUtils.getCurrentSceneIndex(trackBank) + 1;
        if (nextSceneIndex < sceneBank.getSizeOfBank() && sceneBank.getScene(nextSceneIndex).exists().get()) {
            sceneBank.launchScene(nextSceneIndex);
        }
        application.setPanelLayout(PANEL_LAYOUT_MIX);
    }

    public static void launchPrev(Application application, SceneBank sceneBank, TrackBank trackBank) {
        int currentSceneIndex = SceneUtils.getCurrentSceneIndex(trackBank);
        sceneBank.launchScene(Math.max(0, currentSceneIndex - 1));
        application.setPanelLayout(PANEL_LAYOUT_MIX);
    }

    /** Index of the first scene with a playing clip, or -1 if nothing is playing. */
    private static int getCurrentSceneIndex(TrackBank trackBank) {
        for (int i = 0; i < Globals.NUMBER_OF_SCENES; i++) {
            boolean isScenePlaying = false;
            for (int t = 0; t < trackBank.getSizeOfBank(); t++) {
                if (trackBank.getItemAt(t).clipLauncherSlotBank().getItemAt(i).isPlaying().get()) {
                    isScenePlaying = true;
                    break;
                }
            }

            if (isScenePlaying) {
                return i;
            }
        }

        return -1;
    }

}
