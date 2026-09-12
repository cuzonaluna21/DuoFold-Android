package dev.duofold.bridge;
oneway interface ICaptureListener {
    void onFrame(long sequence, long timestampMs);
    void onError(String message);
}
