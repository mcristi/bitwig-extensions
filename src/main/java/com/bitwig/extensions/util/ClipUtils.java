package com.bitwig.extensions.util;

import com.bitwig.extension.api.Color;
import com.bitwig.extension.controller.api.Application;
import com.bitwig.extension.controller.api.Clip;
import com.bitwig.extension.controller.api.ClipLauncherSlot;
import static com.bitwig.extension.controller.api.Application.PANEL_LAYOUT_MIX;

public class ClipUtils {

    private ClipUtils() {
    }

    public static void delete(Application application, Clip cursorClip) {
        cursorClip.clipLauncherSlot().deleteObject();
        application.setPanelLayout(PANEL_LAYOUT_MIX);
    }

    public static Color getSlotColor(ClipLauncherSlot slot) {
        Color[] colors = {
            Color.fromHex("#d92e24"), // red
            Color.fromHex("#ff5706"), // orange
            Color.fromHex("#d99d10"), // yellow
            Color.fromHex("#0099d9"), // dark blue
            Color.fromHex("#44c8ff"), // light blue
            Color.fromHex("#3ebb62"), // dark green
            Color.fromHex("#009d47"), // light green
            Color.fromHex("#5761c6"), // dark purple
            Color.fromHex("#bc76f0"), // light purple
            Color.fromHex("#c9c9c9"), // white
        };
        Color currentColor = slot.color().get();
        int colorIndex = 0;
        for (int i = 0; i < colors.length; i++) {
            if (
                // NOTE: this is a workaround for V6 color issues
                Math.abs(colors[i].getRed255() - currentColor.getRed255()) < 10 &&
                Math.abs(colors[i].getGreen255() - currentColor.getGreen255()) < 10 &&
                Math.abs(colors[i].getBlue255() - currentColor.getBlue255()) < 10
            ) {
                colorIndex = i + 1;
            }
        }
        if (colorIndex == colors.length) {
            colorIndex = 0;
        }
        return colors[colorIndex];
    }
}
