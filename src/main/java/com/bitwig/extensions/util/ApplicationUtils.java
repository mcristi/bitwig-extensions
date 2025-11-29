package com.bitwig.extensions.util;

import com.bitwig.extension.controller.api.Application;
import static com.bitwig.extension.controller.api.Application.PANEL_LAYOUT_EDIT;
import static com.bitwig.extension.controller.api.Application.PANEL_LAYOUT_MIX;

public class ApplicationUtils
{
    public static void showDetailEditorSubPanel(Application application) {
        application.getAction("Select sub panel 1").invoke();
    }

    public static void showDeviceSubPanel(Application application) {
        application.getAction("Select sub panel 3").invoke();
    }

    public static void showEditPanelLayout(Application application) {
        application.setPanelLayout(PANEL_LAYOUT_EDIT);
    }

    public static void showMixPanelLayout(Application application) {
        application.setPanelLayout(PANEL_LAYOUT_MIX);
    }
}
