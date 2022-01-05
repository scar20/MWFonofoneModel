package com.scarette.mwfonofonemodel;

public interface PlaybackListener {
    void onProgress(int readP);
    void onCompletion();
}