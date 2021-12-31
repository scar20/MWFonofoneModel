package com.scarette.mwfonofonemodel;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import java.util.HashMap;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import nl.igorski.mwengine.MWEngine;
import nl.igorski.mwengine.core.ABiquadHPFilter;
import nl.igorski.mwengine.core.ABiquadLPFilter;
import nl.igorski.mwengine.core.BaseAudioEvent;
import nl.igorski.mwengine.core.ChannelGroup;
import nl.igorski.mwengine.core.Drivers;
import nl.igorski.mwengine.core.JavaUtilities;
import nl.igorski.mwengine.core.LPFHPFilter;
import nl.igorski.mwengine.core.Limiter;
import nl.igorski.mwengine.core.Notifications;
import nl.igorski.mwengine.core.ProcessingChain;
import nl.igorski.mwengine.core.ReverbSM;
import nl.igorski.mwengine.core.SWIGTYPE_p_AudioBuffer;
import nl.igorski.mwengine.core.SampleEvent;

import nl.igorski.mwengine.core.SampleEventRange;
import nl.igorski.mwengine.core.SampleManager;
import nl.igorski.mwengine.core.SampledInstrument;
import nl.igorski.mwengine.core.SequencerController;



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
    private Vector<Track> tracks = new Vector<>();
    private static HashMap<Integer, Track> tracksMap = new HashMap<Integer, Track>();
    private int numOfTrack = 2;

    private MWEngine _engine;
    private SequencerController _sequencerController;

    private boolean _sequencerPlaying = false;
    private boolean _isRecording = false;
    private boolean _inited = false;
    public boolean isAnyPlaying = false;

    // AAudio is only supported from Android 8/Oreo onwards.
    private boolean _supportsAAudio = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O;
    private Drivers.types _audioDriver = _supportsAAudio ? Drivers.types.AAUDIO : Drivers.types.OPENSL;

    private int SAMPLE_RATE;
    private int BUFFER_SIZE;
    private int OUTPUT_CHANNELS = 2; // 1 = mono, 2 = stereo


    private static int STEPS_PER_MEASURE = 32;  // amount of subdivisions within a single measure
    private static String LOG_TAG = "MWENGINE"; // logcat identifier

    private Activity curActivity;

    private static final MWEngineManager MWEngineInstance = new MWEngineManager();

    static MWEngineManager getInstance() {
        return MWEngineInstance;
    }

    private MWEngineManager() {
        _engine = new MWEngine(new StateObserver());
    }

    public void initAudioEngine(Activity activity) {

        // on config change activity will be reissued and we need to
        // store the current one for the StateObserver . Will also be
        // used on the two calls for BUFFER_SIZE and SAMPLE_RATE on initialization, and
        // by loadWAVAsset( String assetName, String sampleName ) private method.
        // Note that this reference to activity can be avoided if we find a substitute
        // for runOnUiThread in the observer; than we can just pass BUFFER_SIZE and SAMPLE_RATE
        // as parameter to initAudioEngine instead of passing activity and install loadWAVAsset
        // elsewhere (in mainActivity).
        curActivity = activity;
//        Log.d(LOG_TAG, "curActivity set, inited = " + _inited);

        if (_inited)
            return;

//        Log.d(LOG_TAG, "initing MWEngine body");

        // STEP 1 : preparing the native audio engine

        // get the recommended buffer size for this device (NOTE : lower buffer sizes may
        // provide lower latency, but make sure all buffer sizes are powers of two of
        // the recommended buffer size (overcomes glitching in buffer callbacks )
        // getting the correct sample rate upfront will omit having audio going past the system
        // resampler reducing overall latency

        BUFFER_SIZE = MWEngine.getRecommendedBufferSize(curActivity.getApplicationContext());
        SAMPLE_RATE = MWEngine.getRecommendedSampleRate(curActivity.getApplicationContext());

        _engine.createOutput(SAMPLE_RATE, BUFFER_SIZE, OUTPUT_CHANNELS, _audioDriver);

        // cache some of the engines properties

        final ProcessingChain masterBus = _engine.getMasterBusProcessors();

        // create a lowpass filter to catch all low rumbling and a limiter to prevent clipping of output :)

        _lpfhpf = new LPFHPFilter((float) MWEngine.SAMPLE_RATE, 55, OUTPUT_CHANNELS);
        _limiter = new Limiter();

        masterBus.addProcessor(_lpfhpf);
        masterBus.addProcessor(_limiter);


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
        private Vector<SampleEventRange> _oneShotEvents;
        private SampleEventRange _sampleEvent;
        private int _sampleLenght;
        private short[] shortBufffer;

        private SampledInstrument _sampler;
        private SampledInstrument _metroSampler1;
        private SampledInstrument _metroSampler2;
        private SampledInstrument _metroSampler3;
        private SampledInstrument _metroSampler4;
        private ABiquadLPFilter lpFilter;
        private ABiquadHPFilter hpFilter;
        private Limiter flimiter;
        private FilterHandler filterHandler;
        private ReverbSM reverb;
        private ReverbHandler reverbHandler;
        private ChannelGroup mainChannel;
        private Limiter sLimiter;
        private OneShotHandler oneshotHandler;

        // track parameters
        private float minFilterCutoff = 50.0f;
        private float maxFilterCutoff = (float) SAMPLE_RATE / 8;
        private String curSampleName;
        private int curStartPoint;
        private int curEndPoint;
        private float curPlaybackRate;
        private float lastReverbWet;
        private float lastReverbSize;

        public boolean isForward = true;
        public boolean isReverbOn = false;
        public boolean isMetroOn = false;
        public boolean isLooping = false;
        private boolean isPlaying = false;

        private int maxMetroCount = 24;

        private Metronome metronome = new Metronome();

        public SampleEvent oneShot;

        private PlaybackListener mPlaybackListener;
        private SyncedRenderer renderer;


        // constructor
        Track() {
            // vector for metronome and oneShot event
            _sampleEvents = new Vector<SampleEventRange>();
            _oneShotEvents = new Vector<>(); // for testing

            // put an hash id if this track to receive notification when sample stop
            tracksMap.put(this.hashCode(), this);

            // renderer for display playback updates - maybe replaced by call in waveformView onDraw()
            // play() & stop() start/stop the renderer
            renderer = new SyncedRenderer(new Function1<Long, Unit>() {
                @Override
                public Unit invoke(Long aLong) {
                    int bufPos = _sampleEvent.getPlaybackPosition();
                    int readPos = _sampleEvent.getReadPointer();
                    mPlaybackListener.onProgress(bufPos, readPos);
                    return null;
                }
            });


            // STEP 2 : let's create some instruments =D

            // Real-time sample events
            _sampler = new SampledInstrument();
            _metroSampler1 = new SampledInstrument();
            _metroSampler2 = new SampledInstrument();
            _metroSampler3 = new SampledInstrument();
            _metroSampler4 = new SampledInstrument();

            mainChannel = new ChannelGroup();
            _engine.addChannelGroup(mainChannel);

            // main sample
            _sampleEvent = new SampleEventRange(_sampler);
            _sampleEvent.setID(this.hashCode()); // associate this id to that sample

            // oneshot sample
            for (int i=0; i<maxMetroCount; i++) {
                final  SampleEventRange ev = new SampleEventRange(_sampler);
                _oneShotEvents.add(ev);
            }
            oneshotHandler = new OneShotHandler();


            _sampler.getAudioChannel().setVolume(0.7f); // set volume here
            mainChannel.addAudioChannel(_sampler.getAudioChannel());


            // metronome sample vector - maxMetroCount of samples divided in 4 sampled instrument
            // as an attempt to alleviate harshness and distortion in the playback when many samples
            // overlaps at high rate. But it doesn't look that is the problem...
            int halfCount = maxMetroCount / 2;
            SampledInstrument sampler;
            for (int i = 0; i < maxMetroCount; i++) {
                if (i < halfCount) {
                    if (i < halfCount/2 ) sampler = _metroSampler1;
                    else sampler = _metroSampler2;
                } else {
                    if (i < halfCount + (halfCount/2)) sampler = _metroSampler3;
                    else sampler = _metroSampler4;
                }
                final SampleEventRange ev = new SampleEventRange(sampler);
                _sampleEvents.add(ev);
            }


            // add a limiter to prevent clipping at the sample level - mainly to overcome metronome add-up
            sLimiter = new Limiter(5, 50, 0.5f, true);
            sLimiter.setSoftKnee(true);
            _metroSampler1.getAudioChannel().getProcessingChain().addProcessor(sLimiter);
            _metroSampler1.getAudioChannel().setVolume(0.7f);
            _metroSampler2.getAudioChannel().getProcessingChain().addProcessor(sLimiter);
            _metroSampler2.getAudioChannel().setVolume(0.7f);
            _metroSampler3.getAudioChannel().getProcessingChain().addProcessor(sLimiter);
            _metroSampler3.getAudioChannel().setVolume(0.7f);
            _metroSampler4.getAudioChannel().getProcessingChain().addProcessor(sLimiter);
            _metroSampler4.getAudioChannel().setVolume(0.7f);
            mainChannel.addAudioChannel(_metroSampler1.getAudioChannel());
            mainChannel.addAudioChannel(_metroSampler2.getAudioChannel());
            mainChannel.addAudioChannel(_metroSampler3.getAudioChannel());
            mainChannel.addAudioChannel(_metroSampler4.getAudioChannel());


            // Fonofilter
            filterHandler = new FilterHandler();
            lpFilter = new ABiquadLPFilter(
                    10000, .0f, //( float ) ( Math.sqrt( 1 ) / 2 ),
                    20, 20000, OUTPUT_CHANNELS);
            hpFilter = new ABiquadHPFilter(
                    20, .0f, //( float ) ( Math.sqrt( 1 ) / 2 ),
                    20, 20000, OUTPUT_CHANNELS);
            flimiter = new Limiter(50f, 25f,
                    0.5f, true);
            mainChannel.getProcessingChain().addProcessor(lpFilter);
            mainChannel.getProcessingChain().addProcessor(hpFilter);
            mainChannel.getProcessingChain().addProcessor(flimiter);
            filterHandler.setFilterCutoff(0.5f); // init cutoff until finding why its not set by viewmodel

            // Reverb
            reverbHandler = new ReverbHandler();
            reverb = new ReverbSM();
            reverb.setDamp(0.88f);
            reverb.setWidth(1f);
            reverb.setWet(0f);
            reverb.setDry(0.5f);
            reverb.setRoomSize(0.6f);
            mainChannel.getProcessingChain().addProcessor(reverb);

            // assign a sample
            // this is assigned through TrackViewModel
        }

        // for playback display updates
        public void setPlaybackListener(PlaybackListener listener) {
            mPlaybackListener = listener;
        }

        public boolean isPlaying() {
            return isPlaying;
        }

        protected void flushTrack() {
            // calling 'delete()' on a BaseAudioEvent invokes the
            // native layer destructor (and removes it from the sequencer)

            for (final BaseAudioEvent event : _sampleEvents) {
                event.getInstrument().delete();
                event.delete();
            }
            _sampleEvent.delete();

            // clear Vectors so all references to the events are broken

            _sampleEvents.clear();

            // detach all processors from engine's master bus

            _engine.getMasterBusProcessors().reset();

            // calling 'delete()' on all instruments invokes the native layer destructor
            // (and frees memory allocated to their resources, e.g. AudioChannels, Processors)

            _sampler.delete();

            // allow these to be garbage collected

            _sampler = null;

            _metroSampler1 = null;
            _metroSampler2 = null;
            _metroSampler3 = null;
            _metroSampler4 = null;

            // and these (garbage collection invokes native layer destructors, so we'll let
            // these processors be cleared lazily)

            _lpfhpf = null;
            // added just to make sure
            _limiter = null;

            sLimiter = null;
            lpFilter = null;
            hpFilter = null;
            flimiter = null;
        }

        public SampleEventRange getCurSample() {
            return _sampleEvent;
        }

        public int getSampleStart() {
            return curStartPoint;
        }

        public void setSample(String sampleName) {

            if (isPlaying) {
                isPlaying = false;
                _sampleEvent.stop();
                if (isMetroOn) metronome.stop();
            }

            SWIGTYPE_p_AudioBuffer sample;
            curSampleName = sampleName;
            sample = SampleManager.getSample(curSampleName);
            _sampleLenght = SampleManager.getSampleLength(curSampleName);
            _sampleEvent.setSample(sample);

            // this *MUST* be placed before setBufferRangeStart/End otherwise segfault at end of loop
            // - strangely, it do not happen if sample is backward. Need more investigation since
            // there have been changes in SampleEvent
//            _sampleEvent.setRangeBasedPlayback(true); // keep it as a reminder
            _sampleEvent.setBufferRangeStart(curStartPoint);
            _sampleEvent.setBufferRangeEnd(curEndPoint);
            _sampleEvent.setPlaybackRate(curPlaybackRate);
            _sampleEvent.setLoopeable(isLooping, 0);
            _sampleEvent.setPlaybackDirection(isForward);


            for (SampleEventRange ev : _sampleEvents) {
                ev.setSample(sample);
                ev.setBufferRangeStart(curStartPoint);
                ev.setBufferRangeEnd(curEndPoint);
                ev.setPlaybackRate(curPlaybackRate);
                ev.setPlaybackDirection(isForward);
                ev.setLoopeable(false, 0);
                ev.setVolume(0.7f);
            }

            for (SampleEventRange ev : _oneShotEvents) {
                ev.setSample(sample);
                ev.setBufferRangeStart(curStartPoint);
                ev.setBufferRangeEnd(curEndPoint);
                ev.setPlaybackRate(curPlaybackRate);
                ev.setPlaybackDirection(isForward);
                ev.setLoopeable(false, 0);
                ev.setVolume(0.7f);
            }
        }

        public void play() {

            isPlaying = true;
            _sampleEvent.play();
            if (mPlaybackListener != null) {
                renderer.start();
            }
            if (isMetroOn) {
                metronome.start();
            }
        }

        public void stop() {
            isPlaying = false;
            if (isMetroOn) {
                metronome.stop();
            }
            _sampleEvent.stop();
            renderer.stop();
        }

        public void oneShotTrigger() {
            oneshotHandler.trigger();
        }

        public void oneShotStop() {
            for (SampleEventRange ev : _oneShotEvents) ev.stop();
        }

        public void setDirection(boolean direction) {
            isForward = direction;
            _sampleEvent.setPlaybackDirection(isForward);

            for (SampleEventRange ev : _sampleEvents) ev.setPlaybackDirection(isForward);
            for (SampleEventRange ev : _oneShotEvents) ev.setPlaybackDirection(isForward);
        }

        public void setLooping(boolean looping) {
            isLooping = looping;
            _sampleEvent.setLoopeable(isLooping, 0);
        }

        public void setSampleStart(float progress) {
            curStartPoint = (int) (progress * (_sampleLenght - 1));
            _sampleEvent.setBufferRangeStart(curStartPoint);

            for (SampleEventRange ev : _sampleEvents) ev.setBufferRangeStart(curStartPoint);
            for (SampleEventRange ev : _oneShotEvents) ev.setBufferRangeStart(curStartPoint);
        }

        public void setSampleEnd(float progress) {
            curEndPoint = (int) (progress * (_sampleLenght - 1));
            _sampleEvent.setBufferRangeEnd(curEndPoint);

            for (SampleEventRange ev : _sampleEvents) ev.setBufferRangeEnd(curEndPoint);
            for (SampleEventRange ev : _oneShotEvents) ev.setBufferRangeEnd(curEndPoint);
        }

        public void setSampleSpeed(float progress) {
            float value = (progress * 4f) - 2f;
            float factor = (float) Math.pow(2.0, value);
            curPlaybackRate = factor;
            _sampleEvent.setPlaybackRate(factor);

            for (SampleEventRange ev : _sampleEvents) ev.setPlaybackRate(factor);
            for (SampleEventRange ev : _oneShotEvents) ev.setPlaybackRate(factor);
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
                reverb.setWet(lastReverbWet);
                reverb.setRoomSize(lastReverbSize);
            } else reverb.setWet(0f);
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
                    metronome.start(); // only start if playing
                }
            } else {
                metronome.stop();
            }
        }

        public void setVolume(float progress) {
            _sampler.getAudioChannel().setVolume(progress);
            _metroSampler1.getAudioChannel().setVolume(progress);
            _metroSampler2.getAudioChannel().setVolume(progress);
            _metroSampler3.getAudioChannel().setVolume(progress);
            _metroSampler4.getAudioChannel().setVolume(progress);
        }

        private class FilterHandler {

            private float minABiquadFilterCutoff = 20.0f;
            private float maxABiquadFilterCutoff = 10000.0f;
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
                lastReverbWet = progress;
                lastReverbSize = (progress * 0.333f) + 0.666f;
                if (isReverbOn) {
                    reverb.setWet(progress);
                    reverb.setRoomSize(lastReverbSize);
                }
            }
        }

        private class Metronome {
            private ScheduledThreadPoolExecutor scheduler;
            ScheduledFuture<?> task;
            private long delay = 1500;
            private int count = 0;
            private boolean isRunning = false;


            public void start() {
                count = 0;
                isRunning = true;
                scheduler = new ScheduledThreadPoolExecutor(0);
                scheduler.setRemoveOnCancelPolicy(true);
                cycle();
            }

            public void stop() {
                if (isRunning) {
                    isRunning = false;
                    List<Runnable> runnables = scheduler.shutdownNow();
                    task.cancel(true);
                    for (SampleEventRange ev : _sampleEvents) ev.stop();
                }
            }

            public void setTime(long time) {
                delay = time;
            }

            public void cycle() {
                _sampleEvents.get(count).play(); // pointers are reset on play

                task = scheduler.schedule(() -> {
                    count++;
                    count = count % maxMetroCount;
                    cycle();
                }, delay, TimeUnit.MILLISECONDS);
            }

            // curRangeInMillis / cycle time = num of repeat
            // if cycle time < thresh apply attenuation thresh time - min att to min time - max att

        }

        private class OneShotHandler {
            private int count = 0;
            public void trigger() {
                _oneShotEvents.get(count++).play();
                count = count % maxMetroCount;
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
                    // we can calculate the amount of samples pending until the next step position is reached
                    // which in turn allows us to calculate the engine latency

                    int sequencerPosition = _sequencerController.getStepPosition();
                    int elapsedSamples = _sequencerController.getBufferPosition();

//                    Log.d(LOG_TAG, "seq. position: " + sequencerPosition + ", buffer offset: " + aNotificationValue +
//                            ", elapsed samples: " + elapsedSamples);
                    break;
                case RECORDED_SNIPPET_READY:
                    curActivity.runOnUiThread(new Runnable() {
                        public void run() {
                            // we run the saving on a different thread to prevent buffer under runs while rendering audio
                            _engine.saveRecordedSnippet(aNotificationValue); // notification value == snippet buffer index
                        }
                    });
                    break;
                case RECORDED_SNIPPET_SAVED:
//                    Log.d(LOG_TAG, "Recorded snippet " + aNotificationValue + " saved to storage");
                    break;
                case MARKER_POSITION_REACHED:
//                    Log.d(LOG_TAG, "!!!!!! Marker Reached " + aNotificationValue);
                    if (aNotificationValue != 0) {
                        Track track = MWEngineManager.tracksMap.get(aNotificationValue);
                        if (track != null) track.mPlaybackListener.onCompletion();
                    }
                    break;
            }
        }
    }

    /* private methods */

    /**
     * convenience method to load WAV files packaged in the APK
     * and read their audio content into MWEngine's SampleManager
     *
     * @param assetName  {String} assetName filename for the resource in the /assets folder
     * @param sampleName {String} identifier for the files WAV content inside the SampleManager
     */
    private void loadWAVAsset(String assetName, String sampleName) {
        final Context ctx = curActivity.getApplicationContext();
        JavaUtilities.createSampleFromAsset(
                sampleName, ctx.getAssets(), ctx.getCacheDir().getAbsolutePath(), assetName
        );
    }

}