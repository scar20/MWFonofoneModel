package com.scarette.mwfonofonemodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;

public class TracksViewModel extends ViewModel {

    private final ArrayList<TrackModel> tracks = new ArrayList<>();
    private final MWEngineManager mAudioEngine = MWEngineManager.getInstance();

    public TracksViewModel() {
        super();
        for (int i=0; i<2; i++) {
            tracks.add(new TrackModel(i));
        }
    }

    public TrackModel getTrack(int i) { return tracks.get(i); }

    @Override
    protected void onCleared() {
        super.onCleared();
        mAudioEngine.destroy();  // dispose the engine
//        Log.d( LOG_TAG, "MWEngineActivity destroyed" );
    }

    public class TrackModel {
        int id;
        private final MWEngineManager.Track audioTrack;
        TrackModel(int num) {
            id = num;
            audioTrack = mAudioEngine.getTrack(id);
        }

        public void setInitialValue() {
//            setSampleName("001");
            setSampleSelection(1);
            setSampleStart(0.0f);
            setSampleEnd(1.0f);
            setSampleSpeed(0.5f);
            setFilterCutoff(0.5f);
            setFilterResonance(0.0f);
            setReverb(0.0f);
            setMetroRate(0.2f);
            setVolume(0.7f);
        }

        private void resetValues() {
            setSampleStart(sampleStart.getValue());
            setSampleEnd(sampleEnd.getValue());
            setSampleSpeed(sampleSpeed.getValue());
            setFilterCutoff(filterCutoff.getValue());
            setFilterResonance(filterResonance.getValue());
            setReverb(reverb.getValue());
            setMetroRate(metroRate.getValue());
            setVolume(volume.getValue());
        }

        public void resetSampleLength() {
            setSampleStart(sampleStart.getValue());
            setSampleEnd(sampleEnd.getValue());
        }

        // for androfone/fonofone
        private final MutableLiveData<Integer> sampleSelection = new MutableLiveData<>(0);
        public LiveData<Integer> getSampleSelection() { return sampleSelection; }
        public void setSampleSelection(int sampleSelection) {
            this.sampleSelection.setValue(sampleSelection);
            audioTrack.setSample(String.valueOf(id), sampleSelection);
            resetValues();
        }

//        private final MutableLiveData<String> sampleName = new MutableLiveData<>("001");
//        public LiveData<String> getSampleName() { return sampleName; }
//        public void setSampleName(String sampleName) {
//            this.sampleName.setValue(sampleName);
//            audioTrack.setSample(sampleName);
//        }


        private final MutableLiveData<Boolean> isPlaying = new MutableLiveData<>(false);
        public MutableLiveData<Boolean> getIsPlaying() { return isPlaying; }
        public void setIsPlaying(boolean isPlaying) {
            this.isPlaying.setValue(isPlaying);
            if (isPlaying) audioTrack.play();
            else audioTrack.stop();
        }
        public void stopPlaying() {
            this.isPlaying.setValue(false);
            audioTrack.stop();
        }

        private final MutableLiveData<Boolean> isForward = new MutableLiveData<>(true);
        public MutableLiveData<Boolean> getIsForward() { return isForward; }
        public void setIsForward(boolean isForward) {
            this.isForward.setValue(isForward);
            audioTrack.setDirection(isForward);
        }

        private final MutableLiveData<Boolean> isLooping = new MutableLiveData<>(false);
        public MutableLiveData<Boolean> getIsLooping() { return isLooping; }
        public void setIsLooping(boolean isLooping) {
            this.isLooping.setValue(isLooping);
            audioTrack.setLooping(isLooping);
        }

        private final MutableLiveData<Float> sampleStart = new MutableLiveData<>(0.0f);
        public MutableLiveData<Float> getSampleStart() { return sampleStart; }
        public void setSampleStart(float sampleStart) {
            this.sampleStart.setValue(sampleStart);
            audioTrack.setSampleStart(sampleStart);
        }

        private final MutableLiveData<Float> sampleEnd = new MutableLiveData<>(1.0f);
        public MutableLiveData<Float> getSampleEnd() { return sampleEnd; }
        public void setSampleEnd(float sampleEnd) {
            this.sampleEnd.setValue(sampleEnd);
            audioTrack.setSampleEnd(sampleEnd);
        }

        private final MutableLiveData<Float> sampleSpeed = new MutableLiveData<>(0.5f);
        public MutableLiveData<Float> getSampleSpeed() { return sampleSpeed; }
        public void setSampleSpeed(float sampleSpeed) {
            this.sampleSpeed.setValue(sampleSpeed);
            audioTrack.setSampleSpeed(sampleSpeed);
        }

        private final MutableLiveData<Float> filterCutoff = new MutableLiveData<>(0.5f);
        public MutableLiveData<Float> getFilterCutoff() { return filterCutoff; }
        public void setFilterCutoff(float filterCutoff) {
            this.filterCutoff.setValue(filterCutoff);
            audioTrack.setFilterCutoff(filterCutoff);
        }

        private final MutableLiveData<Float> filterResonance = new MutableLiveData<>(0.0f);
        public MutableLiveData<Float> getFilterResonance() { return filterResonance; }
        public void setFilterResonance(float filterResonance) {
            this.filterResonance.setValue(filterResonance);
            audioTrack.setFilterQ(filterResonance);
        }

        private final MutableLiveData<Float> reverb = new MutableLiveData<>(0.0f);
        public MutableLiveData<Float> getReverb() { return reverb; }
        public void setReverb(float reverb) {
            this.reverb.setValue(reverb);
            audioTrack.setReverb(reverb);
        }

        private final MutableLiveData<Boolean> isReverbOn = new MutableLiveData<>(false);
        public MutableLiveData<Boolean> getIsReverbOn() { return isReverbOn; }
        public void setIsReverbOn(boolean isReverbOn) {
            this.isReverbOn.setValue(isReverbOn);
            audioTrack.setReverbOn(isReverbOn);
        }

        private final MutableLiveData<Float> metroRate = new MutableLiveData<>(0.1f);
        public MutableLiveData<Float> getMetroRate() { return metroRate; }
        public void setMetroRate(float metroRate) {
            this.metroRate.setValue(metroRate);
            audioTrack.setMetroRate(metroRate);
        }

        private final MutableLiveData<Boolean> isMetroOn = new MutableLiveData<>(false);
        public MutableLiveData<Boolean> getIsMetroOn() { return isMetroOn; }
        public void setIsMetroOn(boolean isMetroOn) {
            this.isMetroOn.setValue(isMetroOn);
            audioTrack.setMetroOn(isMetroOn);
        }

        private final MutableLiveData<Float> volume = new MutableLiveData<>(0.7f);
        public MutableLiveData<Float> getVolume() { return volume; }
        public void setVolume(float volume) {
            this.volume.setValue(volume);
            audioTrack.setVolume(volume);
        }
    }
}