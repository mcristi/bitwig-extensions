package com.bitwig.extensions.util;

import com.bitwig.extension.controller.api.Application;

public class ApplicationUtils
{
    public static void showDetailEditorPanel(Application application) {
        application.getAction("Select sub panel 1").invoke();
    }

    public static void showDevicePanel(Application application) {
        application.getAction("Select sub panel 3").invoke();
    }
}
