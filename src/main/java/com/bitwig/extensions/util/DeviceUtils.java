package com.bitwig.extensions.util;

import com.bitwig.extension.controller.api.CursorRemoteControlsPage;


public class DeviceUtils {

    private DeviceUtils() {
    }

    public static void setPage(CursorRemoteControlsPage cursorRemoteControlsPage, int page) {
        cursorRemoteControlsPage.selectedPageIndex().set(page);
    }

    public static void setParameter(CursorRemoteControlsPage cursorRemoteControlsPage, int index, int value) {
        cursorRemoteControlsPage.getParameter(index).set(value, 128);
    }

}
