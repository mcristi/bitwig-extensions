package com.bitwig.extensions.util;

import com.bitwig.extension.controller.api.SceneBank;
import com.bitwig.extension.controller.api.TrackBank;
import com.bitwig.extensions.Globals;


public class SceneUtils {

    private SceneUtils() {
    }

    public static void launchNext(SceneBank sceneBank, TrackBank trackBank) {
        int currentSceneIndex = SceneUtils.getCurrentSceneIndex(trackBank);
        sceneBank.launchScene(currentSceneIndex == Globals.NUMBER_OF_SCENES - 1 ? currentSceneIndex : currentSceneIndex + 1);
    }

    public static void launchPrev(SceneBank sceneBank, TrackBank trackBank) {
        int currentSceneIndex = SceneUtils.getCurrentSceneIndex(trackBank);
        sceneBank.launchScene(currentSceneIndex == 0 ? 0 : currentSceneIndex - 1);
    }

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

        return 0;
    }

}
