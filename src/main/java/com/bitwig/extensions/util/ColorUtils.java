package com.bitwig.extensions.util;

import com.bitwig.extension.api.Color;
import com.bitwig.extension.controller.api.ClipLauncherSlot;
import com.bitwig.extension.controller.api.DrumPad;

public class ColorUtils {
    private static final Color[] colors = {
        Color.fromHex("#d92e22"), // red
        Color.fromHex("#ff5704"), // orange
        Color.fromHex("#d99d0e"), // yellow
        Color.fromHex("#0099d7"), // dark blue
        Color.fromHex("#44c8fe"), // light blue
        Color.fromHex("#3eb960"), // dark green
        Color.fromHex("#43d2b7"), // light green
        Color.fromHex("#9549cb"), // dark purple
        Color.fromHex("#d1b9db"), // light purple
    };

   public static float[] rgbToHsb(final int red, final int green, final int blue, final float[] hsb)
   {
      final int max = Math.max(red, Math.max(green, blue));
      final int min = Math.min(red, Math.min(green, blue));
      final float saturation = max == 0 ? 0 : (max - min) / (float)max;
      float hue = 0;


      if (saturation != 0)
      {
         final float range = max - min;
         final float r = (max - red) / range;
         final float g = (max - green) / range;
         final float b = (max - blue) / range;

         if (red == max)
            hue = b - g;
         else if (green == max)
            hue = 2.0f + r - b;
         else
            hue = 4.0f + g - r;

         hue = hue / 6.0f;

         if (hue < 0)
            hue = hue + 1.0f;
      }

      hsb[0] = hue;
      hsb[1] = saturation;
      hsb[2] = max / 255.0f;
      return hsb;
   }

    public static Color getColor(ClipLauncherSlot slot) {
        return getNextColor(slot.color().get());
    }

    public static Color getColor(DrumPad pad) {
        return getNextColor(pad.color().get());
    }

    private static Color getNextColor(Color currentColor) {
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
