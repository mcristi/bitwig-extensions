package com.bitwig.extensions.util;

import com.bitwig.extension.controller.api.Clip;

public class ClipUtils {

    private ClipUtils() {
    }

    public static void delete(Clip cursorClip) {
        cursorClip.clipLauncherSlot().deleteObject();
    }
}
