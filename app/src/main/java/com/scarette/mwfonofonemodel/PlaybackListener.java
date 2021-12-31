package com.scarette.mwfonofonemodel;

public interface PlaybackListener {
    void onProgress(int buffer, int readP);
    void onCompletion();
}