package com.bitwig.extensions;

public class CommonState
{
    private static final CommonState INSTANCE = new CommonState();

    public static CommonState getInstance()
    {
        return INSTANCE;
    }

    private CommonState()
    {
    }

    private boolean quantizeClipLengthAfterRecord = true;


    public boolean isQuantizeClipLengthAfterRecord()
    {
        return quantizeClipLengthAfterRecord;
    }

    // Set by a controller whose remote control waits for a parameter in Bitwig's mapping mode (the
    // APC40's USER + select). The other controllers then "touch" the remote controls their knobs
    // turn, as Bitwig only maps a parameter that is touched.
    private volatile boolean remoteMappingWaiting = false;

    public boolean isRemoteMappingWaiting()
    {
        return remoteMappingWaiting;
    }

    public void setRemoteMappingWaiting(final boolean waiting)
    {
        remoteMappingWaiting = waiting;
    }

    public void toggleQuantizeClipLengthAfterRecord()
    {
        quantizeClipLengthAfterRecord = !quantizeClipLengthAfterRecord;
    }
}
