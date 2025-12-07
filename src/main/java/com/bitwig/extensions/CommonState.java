package com.bitwig.extensions;

import java.util.HashMap;
import java.util.Map;

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
    private final Map<Integer, Integer> recordingStatusMap = new HashMap<>();


    public boolean isQuantizeClipLengthAfterRecord()
    {
        return quantizeClipLengthAfterRecord;
    }

    public void toggleQuantizeClipLengthAfterRecord()
    {
        quantizeClipLengthAfterRecord = !quantizeClipLengthAfterRecord;
    }

    public Integer getTrackRecordingClipIndex(int trackIndex) {
        return recordingStatusMap.get(trackIndex);
    }

    public void setTrackRecordingClipIndex(int trackIndex, int clipIndex, boolean isRecording)
    {
        if (isRecording) {
            recordingStatusMap.put(trackIndex, clipIndex);
        } else {
            recordingStatusMap.remove(trackIndex);
        }
    }
}
