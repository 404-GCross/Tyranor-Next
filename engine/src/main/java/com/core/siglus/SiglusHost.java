package com.core.siglus;

/** Shared game interaction; presentation ownership belongs to the selected host. */
interface SiglusHost {
    interface Listener {
        void onCreated(boolean success, boolean cancelled, int windowGeneration);
        void onConnected(long request, boolean success, int windowGeneration);
        void onStepped(int status, boolean imeNeeded, int[] imeArea);
        void onBlocked();
        void onFailure(RuntimeException failure);
    }
    boolean matchesHandle(long value);
    boolean step(int dtMs);
    void touch(int phase, double x, double y);
    void keyEvent(int keyCode, String text, boolean repeat);
    void textInput(String text);
    void imePreedit(String text, int start, int end);
    void submitMessagebox(long engineHandle, long requestId, long value);
    void close(Runnable afterStopped);
}
