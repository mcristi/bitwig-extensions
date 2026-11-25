package com.bitwig.extensions.controllers.allenheath.xonek3;

import com.bitwig.extension.api.Color;
import com.bitwig.extension.controller.api.HardwareLightVisualState;
import com.bitwig.extension.controller.api.InternalHardwareLightState;

/** An LED colour of the Xone:K3: 7 bit red, green and blue plus a brightness. */
public final class XoneColor extends InternalHardwareLightState
{
    private static final int BRIGHT = 0x30;
    // the lowest brightness the unit shows; Bitwig's own script uses it for its faintest white
    private static final int DIM = 0x01;

    public static final XoneColor OFF = new XoneColor(0, 0, 0, 0);
    public static final XoneColor RED = new XoneColor(127, 0, 0, BRIGHT);
    public static final XoneColor ORANGE = new XoneColor(127, 20, 0, BRIGHT);
    public static final XoneColor YELLOW = new XoneColor(127, 127, 0, BRIGHT);
    public static final XoneColor GREEN = new XoneColor(0, 127, 0, BRIGHT);
    public static final XoneColor BLUE = new XoneColor(0, 20, 127, BRIGHT);
    public static final XoneColor LIGHT_BLUE = new XoneColor(20, 110, 127, BRIGHT);
    public static final XoneColor PURPLE = new XoneColor(50, 0, 127, BRIGHT);
    public static final XoneColor MAGENTA = new XoneColor(127, 1, 127, BRIGHT);
    public static final XoneColor WHITE = new XoneColor(127, 127, 127, BRIGHT);

    public final int red;
    public final int green;
    public final int blue;
    public final int brightness;

    private XoneColor(final int red, final int green, final int blue, final int brightness)
    {
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.brightness = brightness;
    }

    /** The same colour at low brightness, for a control that exists but is switched off. */
    public XoneColor dim()
    {
        return new XoneColor(red, green, blue, DIM);
    }

    @Override
    public HardwareLightVisualState getVisualState()
    {
        final double level = brightness / (double)BRIGHT;
        return HardwareLightVisualState.createForColor(
            Color.fromRGB(red / 127.0 * level, green / 127.0 * level, blue / 127.0 * level));
    }

    @Override
    public boolean equals(final Object other)
    {
        return other instanceof final XoneColor c && red == c.red && green == c.green && blue == c.blue
            && brightness == c.brightness;
    }

    @Override
    public int hashCode()
    {
        return red | green << 7 | blue << 14 | brightness << 21;
    }
}
