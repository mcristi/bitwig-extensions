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

    public void toggleQuantizeClipLengthAfterRecord()
    {
        quantizeClipLengthAfterRecord = !quantizeClipLengthAfterRecord;
    }
}
