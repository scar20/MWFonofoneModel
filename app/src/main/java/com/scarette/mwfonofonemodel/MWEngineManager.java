package com.scarette.mwfonofonemodel;

import android.app.Activity;
import android.content.Context;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Vector;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import nl.igorski.mwengine.MWEngine;
import nl.igorski.mwengine.core.*;


public class MWEngineManager {
    /**
     * IMPORTANT : when creating native layer objects through JNI it
     * is important to remember that when the Java references go out of scope
     * (and thus are finalized by the garbage collector), the SWIG interface
     * will invoke the native layer destructors. As such we hold strong
     * references to JNI Objects during the application lifetime
     */
    private Limiter _limiter;
    private LPFHPFilter _lpfhpf;
    private final Vector<Track> tracks = new Vector<>();
    private static final HashMap<Integer, Track> tracksMap = new HashMap<>();
    private final int numOfTrack = 3;

    private final MWEngine _engine;
    private SequencerController _sequencerController;

    private boolean _isRecording = false;
    private boolean _inited = false;
    public boolean isAnyPlaying = false;

    // AAudio is only supported from Android 8/Oreo onwards.
    private final boolean _supportsAAudio = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O;
    private final Drivers.types _audioDriver = _supportsAAudio ? Drivers.types.AAUDIO : Drivers.types.OPENSL;

    private int SAMPLE_RATE;
    private int BUFFER_SIZE;
    private final int OUTPUT_CHANNELS = 2; // 1 = mono, 2 = stereo


    private static final String LOG_TAG = "MWENGINE"; // logcat identifier


    private static final MWEngineManager MWEngineInstance = new MWEngineManager();

    static MWEngineManager getInstance() {
        return MWEngineInstance;
    }

    private MWEngineManager() {
        _engine = new MWEngine(new StateObserver());
    }

    public void initAudioEngine(Activity activity) {

        if (_inited)
            return;

//        Log.d(LOG_TAG, "initing MWEngine body");

        // STEP 1 : preparing the native audio engine

        // optimize activity
        MWEngine.optimizePerformance(activity);

        // get the recommended buffer size for this device (NOTE : lower buffer sizes may
        // provide lower latency, but make sure all buffer sizes are powers of two of
        // the recommended buffer size (overcomes glitching in buffer callbacks )
        // getting the correct sample rate upfront will omit having audio going past the system
        // resampler reducing overall latency

        BUFFER_SIZE = MWEngine.getRecommendedBufferSize(activity.getApplicationContext());
        SAMPLE_RATE = MWEngine.getRecommendedSampleRate(activity.getApplicationContext());

        _engine.createOutput(SAMPLE_RATE, BUFFER_SIZE, OUTPUT_CHANNELS, _audioDriver);

        // cache some of the engines properties

        final ProcessingChain masterBus = _engine.getMasterBusProcessors();

        // create a lowpass filter to catch all low rumbling and a limiter to prevent clipping of output :)

        _lpfhpf = new LPFHPFilter((float) MWEngine.SAMPLE_RATE, 55, OUTPUT_CHANNELS);
        _limiter = new Limiter();

        masterBus.addProcessor(_lpfhpf);
//        masterBus.addProcessor(_limiter);


        // STEP 2 : start your engine!
        // Starts engines render thread (NOTE: sequencer is still paused)
        // this ensures that audio will be output as appropriate (e.g. when
        // playing live events / starting sequencer and playing the song)

        _engine.start();

        // create track
        for (int i = 0; i < numOfTrack; i++) {
            Track track = new Track();
            tracks.add(track);
        }

        _inited = true;
    }

    /* public access method */

    public boolean isSupportingAAudio() {
        return _supportsAAudio;
    }

    public int getSampleRate() {
        return SAMPLE_RATE;
    }

    public void destroy() {
        flushTracks();        // free memory allocated by song
        _engine.dispose();  // dispose the engine
        _inited = false;
        if (isAnyPlaying) isAnyPlaying = false;
    }

    public void start() {
        _engine.start();
    }

    public void stop() {
        _engine.stop();
    }

    public int getSampleLength(String name) {
        return SampleManager.getSampleLength(name);
    }

    protected class Track extends SampledInstrument {

        private Vector<SampleEventRange> _sampleEvents;
        private SampleEventRange _sampleEvent;

/*        private Vector<SampleEvent> _sampleEvents;
        private SampleEvent _sampleEvent;*/
        private int _sampleLenght;

        private SampledInstrument _sampler;
        private Limiter sLimiter;
        private ABiquadLPFilter lpFilter;
        private ABiquadHPFilter hpFilter;
        private Limiter flimiter;
        private final FilterHandler filterHandler;
        private ReverbSM reverb;
        private final ReverbHandler reverbHandler;
        private ChannelGroup mainChannel;

        private int curStartPoint;
        private int curEndPoint;
        private float curPlaybackRate;
        private float lastReverbWet;
        private float lastReverbDry;
        private float lastReverbSize;

        public boolean isForward = true;
        public boolean isReverbOn = false;
        public boolean isMetroOn = false;
        public boolean isLooping = false;
        private boolean isPlaying = false;

        private boolean isSet = false;

        private final int maxMetroCount = 32;
        private int samplePlayCount = -1; // index for the cursorPos array, -1 = no sample playing
        private int smillis;

        private Metronome metronome;


        private PlaybackListener mPlaybackListener;
        private final SyncedRenderer renderer;
        private SyncedRenderer levelMonitor;

        private final int[] cursorPos = new int[maxMetroCount];



        // constructor
        Track() {

            // initialize cursorPos array
            for (int i : cursorPos) cursorPos[i] = -1;

            // put an hash id of this track to receive notification when sample stop
            tracksMap.put(this.hashCode(), this);

            // renderer for display playback updates - maybe replaced by call in waveformView onDraw()
            // play() & stop() start/stop the renderer
            renderer = new SyncedRenderer(aLong -> {
                if (isPlaying) {
                    for (int i = 0; i <= samplePlayCount; i++) {
                        cursorPos[i] = _sampleEvents.get(i).getReadPointer();
                    }
                    mPlaybackListener.onProgress(cursorPos);
                }
                // we need to call this one last time on stop because we are one frame late
                else mPlaybackListener.onProgress(cursorPos); // will it be reset? - YES
                return null;
            });

//            levelMonitor = new SyncedRenderer(new Function1<Long, Unit>() {
//                @Override
//                public Unit invoke(Long aLong) {
//                    float dbspl = LevelUtility.dBSPL(_oneshotSampler.getAudioChannel(), 0);
//                    Log.d(LOG_TAG, "dBSPL: " + dbspl);
//                    float rms = LevelUtility.RMS(oneShotChannel, 0);
//                    Log.d(LOG_TAG, "RMS: " + rms);
//                    float lin = LevelUtility.linear(_oneshotSampler.getAudioChannel(), 0);
//                    Log.d(LOG_TAG, "linear: " + lin);
//                    double max = LevelUtility.max(_oneshotSampler.getAudioChannel(), 0);
//                    Log.d(LOG_TAG, "max: " + max);
//                    return null;
//                }
//            });


            // sample set up, main sample is sample[0]
            sLimiter = new Limiter(0.5f, 1f, 1f);
            _sampleEvents = new Vector<>();
            _sampler = new SampledInstrument();
            for (int i=0; i<maxMetroCount; i++) {
                final  SampleEventRange ev = new SampleEventRange(_sampler);
//                final  SampleEvent ev = new SampleEvent(_sampler);
                _sampleEvents.add(ev);
            }
            _sampler.getAudioChannel().getProcessingChain().addProcessor(sLimiter);
            // keep a pointer to main sample
            _sampleEvent = _sampleEvents.get(0);
            _sampleEvent.setID(this.hashCode()); // associate this id to that sample

            metronome = new Metronome();

//            oneshotHandler = new OneShotHandler();

            // Fonofilter
            lpFilter = new ABiquadLPFilter(
                    10000, .0f, //( float ) ( Math.sqrt( 1 ) / 2 ),
                    20, 20000, OUTPUT_CHANNELS);
            hpFilter = new ABiquadHPFilter(
                    20, .0f, //( float ) ( Math.sqrt( 1 ) / 2 ),
                    20, 20000, OUTPUT_CHANNELS);
            flimiter = new Limiter(50f, 25f,
                    0.5f, true);
            filterHandler = new FilterHandler();
            _sampler.getAudioChannel().getProcessingChain().addProcessor(lpFilter);
            _sampler.getAudioChannel().getProcessingChain().addProcessor(hpFilter);
            _sampler.getAudioChannel().getProcessingChain().addProcessor(flimiter);


            // Reverb
            reverb = new ReverbSM();
            reverbHandler = new ReverbHandler();

            mainChannel = new ChannelGroup();
            mainChannel.addAudioChannel(_sampler.getAudioChannel());
//            mainChannel.getProcessingChain().addProcessor(reverb);

            _engine.addChannelGroup(mainChannel);

            isSet = true;

        }

        // for playback display updates
        public void setPlaybackListener(PlaybackListener listener) {
            mPlaybackListener = listener;
        }

        public boolean isPlaying() {
            return isPlaying;
        }

        protected void flushTrack() {

            // if track haven't been created yet, return
            if (!isSet) {
//                Log.d(LOG_TAG, "!!!!!! track: " + curSampleName + " not set do not flush !!!!!!");
                return;
            }

//            Log.d(LOG_TAG, "!!!!!! track: " + curSampleName + " FLUSH !!!!!!");

            _engine.removeChannelGroup(mainChannel);
            _engine.stop();
            mainChannel.removeAudioChannel(_sampler.getAudioChannel());
            mainChannel.getProcessingChain().reset();
            // calling 'delete()' on a BaseAudioEvent invokes the
            // native layer destructor (and removes it from the sequencer)

//            Log.d(LOG_TAG, "!!!!!! track: " + curSampleName + " sampleEvent to flush: " + _sampleEvents.size());
            for (final BaseAudioEvent event : _sampleEvents) {
                event.delete();
            }

            _sampleEvent.delete();

            // clear Vectors so all references to the events are broken

            _sampleEvents.clear();
            _sampleEvents = null;

//            Log.d(LOG_TAG, "!!!!!! track: " + curSampleName + " metroEvent after clear: " + _metroEvents.size());
            // detach all processors from engine's master bus

            mainChannel.delete();
            mainChannel = null;

            // calling 'delete()' on all instruments invokes the native layer destructor
            // (and frees memory allocated to their resources, e.g. AudioChannels, Processors)

            _sampler.delete();

            // allow these to be garbage collected

            _sampler = null;

            _sampleEvent = null;

            // and these (garbage collection invokes native layer destructors, so we'll let
            // these processors be cleared lazily)

            _lpfhpf = null;
            // added just to make sure
            _limiter = null;


            lpFilter = null;
            hpFilter = null;
            flimiter = null;

            reverb.delete();
            reverb = null;
            sLimiter.delete();
            sLimiter = null;

            metronome.scheduler.shutdownNow();
            metronome = null;

        }

        public SampleEventRange getCurSample() {
            return _sampleEvent;
        }

        public int getSampleStart() {
            return curStartPoint;
        }

        // tag: whichtrack, sampleselect: filePath[i]
        public void setSample(String tag, int sampleSelection) {

            if (isPlaying) {
                isPlaying = false;
                _sampleEvent.stop();
                if (isMetroOn) metronome.stop();
            }

            // track parameters
            //        private float minFilterCutoff = 50.0f;
            //        private float maxFilterCutoff = (float) SAMPLE_RATE / 8;

            String filePath = Repository.FileUtil.filePaths.get(sampleSelection);
//            Log.d(LOG_TAG, "!!!!!! track: " + curSampleName + " filePath: " + filePath);
            if (SampleManager.hasSample(tag))
                SampleManager.removeSample(tag, true);
            JavaUtilities.createSampleFromFile(tag, filePath);
//            Log.d(LOG_TAG, "!!!!!! track: " + curSampleName + " haveSample: " + SampleManager.hasSample(tag));

            // this *MUST* be placed before setBufferRangeStart/End otherwise segfault at end of loop
            // - strangely, it do not happen if sample is backward. Need more investigation since
            // there have been changes in SampleEvent
//            _sampleEvent.setRangeBasedPlayback(true); // keep it as a reminder


            // needed for sample start/end progress setting
            _sampleLenght = SampleManager.getSampleLength(tag);
//            Log.d(LOG_TAG, "!!!!!! track: " + curSampleName + " sampleLenght: " + _sampleLenght);
            // get time in millis for probable usage in metronome
            smillis = BufferUtility.bufferToMilliseconds(_sampleLenght, SAMPLE_RATE);

//            for (SampleEvent ev : _sampleEvents) {
            for (SampleEventRange ev : _sampleEvents) {
                ev.setSample(SampleManager.getSample(tag));
                ev.setBufferRangeStart(curStartPoint);
                ev.setBufferRangeEnd(curEndPoint);
                ev.setPlaybackRate(curPlaybackRate);
                ev.setPlaybackDirection(isForward);
                ev.setLoopeable(false, 0);
            }
            // reset the pointer to main sample and allow looping setting
            _sampleEvent = _sampleEvents.get(0);
            _sampleEvent.setLoopeable(isLooping, 0);
//            Log.d(LOG_TAG, "!!!!!! track: " + curSampleName + "  !!!!!! _sampleEvents size:" + _sampleEvents.size());

        }

        public void play() {
            isPlaying = true;
            if (isMetroOn) {
                metronome.start(); // start from sampleEvents[0] thus include _sampleEvent
            } else {
                _sampleEvent.play();
                samplePlayCount++; // increment count, sample[0] playing
//                Log.d(LOG_TAG, "play() samplePlayCount: " + samplePlayCount);
            }
            if (mPlaybackListener != null) {
                renderer.start();
            }
        }

        public void stop() {
            isPlaying = false;
            if (isMetroOn) {
                metronome.stop(); // stop all events but event[0]
            }
            _sampleEvent.stop(); // must be stopped separately
            renderer.stop();
            resetCursorArray();
//            Log.d(LOG_TAG, "play() samplePlayCount: " + samplePlayCount);
            if (mPlaybackListener != null) {
                mPlaybackListener.onProgress(-1);
            }
        }

        private void resetCursorArray() {
            Arrays.fill(cursorPos, -1);
            if (isPlaying) samplePlayCount = 0;
            else samplePlayCount = -1;
        }


        public void setDirection(boolean direction) {
            isForward = direction;
            for (SampleEventRange ev : _sampleEvents) ev.setPlaybackDirection(isForward);
        }

        public void setLooping(boolean looping) {
            isLooping = looping;
            // only main sample is allowed to loop
            _sampleEvent.setLoopeable(isLooping, 0);
        }

        public void setSampleStart(float progress) {
            curStartPoint = (int) (progress * (_sampleLenght - 1));
            for (SampleEventRange ev : _sampleEvents) ev.setBufferRangeStart(curStartPoint);
//            for (SampleEvent ev : _sampleEvents) ev.setBufferRangeStart(curStartPoint);
        }

        public void setSampleEnd(float progress) {
            curEndPoint = (int) (progress * (_sampleLenght - 1));
            for (SampleEventRange ev : _sampleEvents) ev.setBufferRangeEnd(curEndPoint);
//            for (SampleEvent ev : _sampleEvents) ev.setBufferRangeEnd(curEndPoint);
        }

        public void setSampleSpeed(float progress) {
            float value = (progress * 4f) - 2f;
            float factor = (float) Math.pow(2.0, value);
            curPlaybackRate = factor;
            for (SampleEventRange ev : _sampleEvents) ev.setPlaybackRate(factor);
//            for (SampleEvent ev : _sampleEvents) ev.setPlaybackRate(factor);

        }

        public void setFilterCutoff(float progress) {
            filterHandler.setFilterCutoff(progress);
        }

        public void setFilterQ(float progress) {
            filterHandler.setResonance((float) Math.sqrt(progress));
        }

        public void setReverb(float progress) {
            reverbHandler.setReverbMix(progress);
        }

        public void setReverbOn(boolean isOn) {
            isReverbOn = isOn;
            if (isOn) {
                reverb.setDry(lastReverbDry);
                reverb.setWet(lastReverbWet);
                reverb.setRoomSize(lastReverbSize);
                _sampler.getAudioChannel().getProcessingChain().addProcessor(reverb);
            } else {
                _sampler.getAudioChannel().getProcessingChain().removeProcessor(reverb);
            }
        }

        public void setMetroRate(float progress) {
            final float minTempo = 5f;     // minimum allowed tempo is 5 BPM
            final float maxTempo = 1000f;    // maximum allowed tempo is 1000 BPM
            final float newTempo = (float) (progress * progress) * (maxTempo - minTempo) + minTempo;
            long millis = (long) (60000f / newTempo);
            //           Log.d(LOG_TAG, "Metro rate: " + newTempo + "  millis: " + millis);
            metronome.setTime(millis);
        }

        public void setMetroOn(boolean metroOn) {
            isMetroOn = metroOn;
            if (isMetroOn) {
                if (isPlaying) {
                    metronome.startAfter(); // only start if playing
                    // prevent looping while metro is on
                    _sampleEvent.setLoopeable(false, 0);
                }
            } else {
                metronome.stop();
                // revert looping to its current state
                _sampleEvent.setLoopeable(isLooping, 0);
                // in case the main sample was not in the playing array,
                // we need to reset its position so the loop can continue if playing
                if (isPlaying) _sampleEvent.play(); // this will reset the pointer to curStart
                resetCursorArray();
            }
        }

        public void setVolume(float progress) {
            mainChannel.setVolume(progress);
        }

        private class FilterHandler {

            private float curABiquadFilterCutoff;
            private float curABiquadFilterQ;

            // public access methods

            // Those two could be replaced by a compound setFilterParameters(Point pt)
            // where pt.x is cutoff and pt.y is resonance
            protected void setFilterCutoff(float cutoff) {
                curABiquadFilterCutoff = cutoff;
                recalcParameters();
            }

            protected void setResonance(float res) {
                curABiquadFilterQ = res;
                recalcParameters();
            }

            private void recalcParameters() {
                // res values: 0..1 * 25db
                hpFilter.setResonance(curABiquadFilterQ * 25.0f);
                lpFilter.setResonance(curABiquadFilterQ * 25.0f);

                // offset modulated by amount of q: q=0 -> full offset, q=1 -> no offset
                float offset = 0.5f * (float) Math.pow((1 - curABiquadFilterQ), 2);

                // further modulation for lo-pass offset according to freq to "push" a bit more in the low end
                float lpoffset = (float) Math.min(curABiquadFilterCutoff + 0.5, 1) * offset;

                // set min and max filter cutoff range
                float minABiquadFilterCutoff = 20.0f;
                float maxABiquadFilterCutoff = 10000.0f;
                // clamp lo-pass cutoff to 1 so it wont go beyond maxfreq and remap to freq range
                float lpcut = (float) (Math.pow(Math.min(curABiquadFilterCutoff + lpoffset, 1), 2) *
                        (maxABiquadFilterCutoff - minABiquadFilterCutoff)) + minABiquadFilterCutoff;
                lpFilter.setCutoff(lpcut);

                // clamp hi-pass to 0 so it wont become negative and climb up and remap to freq range
                float hpcut = (float) (Math.pow(Math.max(curABiquadFilterCutoff - offset, 0.0f), 2) *
                        (maxABiquadFilterCutoff - minABiquadFilterCutoff) + minABiquadFilterCutoff);
                hpFilter.setCutoff(hpcut);
            }

        }

        private class ReverbHandler {

            protected void setReverbMix(float progress) {
                float sqrtProgress = (float)Math.sqrt(progress);
                lastReverbDry = ((1 - sqrtProgress) * 0.3f) + 0.2f;
                lastReverbWet = sqrtProgress * 0.3f;
                lastReverbSize = (progress * 0.333f) + 0.666f;
                if (isReverbOn) {
                    reverb.setDry(lastReverbDry);
                    reverb.setWet(lastReverbWet);
                    reverb.setRoomSize(lastReverbSize);
                }
            }
        }

        private class Metronome {

            private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
            ScheduledFuture<?> task;
            private long delay = 1500;
            private int count = 0;
            private boolean isRunning = false;

            public void setTime(long time) {
                delay = time;
            }
            public boolean isRunning() { return isRunning; }

            public void start() {
                count = 0; // for when metro already on, start from main sample
                isRunning = true;
                cycle();
            }
            // First sample (main) already started; start subsequent sound_bank
            public void startAfter() { // metro switched on after sample already playing
                count = 1; // always start from 2d sample, first is main sample already playing
                isRunning = true;
                cycle();
            }

            public void stop() {
                if (isRunning) {
                    isRunning = false;
                    if(task != null) task.cancel(true);
                    cycle();
                }
            }

            public void cycle() {
                if (isRunning) {
//                Log.d(LOG_TAG, "count: " + count);
                    _sampleEvents.get(count).play(); // pointers are reset on play
                    count++;
                    count = count % maxMetroCount;
                    // update samplePlayCount up to maxMetroCount-1
                    samplePlayCount = Math.min(++samplePlayCount, maxMetroCount-1);
//                    Log.d(LOG_TAG, "metro::cycle() samplePlayCount: " + samplePlayCount);
                    task = scheduler.schedule(this::cycle, delay, TimeUnit.MILLISECONDS);
                } else {
                    if(task != null) task.cancel(true); // maybe overcautious...
//                for (SampleEvent ev : _sampleEvents) ev.stop();
                    for (int i = 1; i < maxMetroCount; i++) { // leave main sample alone
                        _sampleEvents.get(i).stop();
                    }
                }
            }

            // attenuation scheme
            // curRangeInMillis / cycle time = num of overlap
            // if overlap > thresh apply attenuation overlap * attenuation
            // max overlap = maxSampleCount
            public void calcOverlap() {
                float overlap = smillis / (float)delay;
//                Log.d(LOG_TAG, "sample millis: " + smillis + " cycle time: " + delay + " overlap: " + overlap);
            }

        }

    }

    public Vector<Track> getTracks() {
        return tracks;
    }

    public Track getTrack(int which) {
        return tracks.get(which);
    }

    public static Track getHash(int key) { return tracksMap.get(key); }


    // clean up
    protected void flushTracks() {
        // this ensures that Song resources currently in use by the engine are released

        _engine.stop();

        // call flushTrack on each tracks
        for (Track t : tracks) t.flushTrack();
        _engine.getMasterBusProcessors().reset();

        // flush sample memory allocated in the SampleManager
        SampleManager.flushSamples();
    }

    /* state change message listener */

    private class StateObserver implements MWEngine.IObserver {
        private final Notifications.ids[] _notificationEnums = Notifications.ids.values(); // cache the enumerations (from native layer) as int Array

        public void handleNotification(final int aNotificationId) {
            switch (_notificationEnums[aNotificationId]) {
                case ERROR_HARDWARE_UNAVAILABLE:
//                    Log.d(LOG_TAG, "ERROR : received driver error callback from native layer");
                    _engine.dispose();
                    break;
                case MARKER_POSITION_REACHED:
//                    Log.d(LOG_TAG, "Marker position has been reached");
                    break;
                case RECORDING_COMPLETED:
//                    Log.d(LOG_TAG, "Recording has completed");
                    break;
            }
        }

        public void handleNotification(final int aNotificationId, final int aNotificationValue) {
            switch (_notificationEnums[aNotificationId]) {
                case SEQUENCER_POSITION_UPDATED:

                    // for this notification id, the notification value describes the precise buffer offset of the
                    // engine when the notification fired (as a value in the range of 0 - BUFFER_SIZE). using this value
                    // we can calculate the amount of sound_bank pending until the next step position is reached
                    // which in turn allows us to calculate the engine latency

                    int sequencerPosition = _sequencerController.getStepPosition();
                    int elapsedSamples = _sequencerController.getBufferPosition();

//                    Log.d(LOG_TAG, "seq. position: " + sequencerPosition + ", buffer offset: " + aNotificationValue +
//                            ", elapsed sound_bank: " + elapsedSamples);
                    break;
                case RECORDED_SNIPPET_READY:
                    Executors.newSingleThreadScheduledExecutor().execute(() -> {
                        // we run the saving on a different thread to prevent buffer under runs while rendering audio
                        _engine.saveRecordedSnippet(aNotificationValue); // notification value == snippet buffer index
                    });
                    break;
                case RECORDED_SNIPPET_SAVED:
//                    Log.d(LOG_TAG, "Recorded snippet " + aNotificationValue + " saved to storage");
                    break;
                case MARKER_POSITION_REACHED:
//                    Log.d(LOG_TAG, "!!!!!! Marker Reached " + aNotificationValue);
                    if (aNotificationValue != 0) {
                        Track track = MWEngineManager.tracksMap.get(aNotificationValue);
                        if (track != null ) track.mPlaybackListener.onCompletion();
                    }
                    break;
            }
        }
    }

}