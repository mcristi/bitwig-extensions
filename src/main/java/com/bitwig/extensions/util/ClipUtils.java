package com.bitwig.extensions.util;

import com.bitwig.extension.controller.api.Application;
import com.bitwig.extension.controller.api.Clip;
import static com.bitwig.extension.controller.api.Application.PANEL_LAYOUT_MIX;

public class ClipUtils {

    private ClipUtils() {
    }

    public static void delete(Application application, Clip cursorClip) {
        cursorClip.clipLauncherSlot().deleteObject();
        application.setPanelLayout(PANEL_LAYOUT_MIX);
    }
}
