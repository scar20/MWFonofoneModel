package com.scarette.mwfonofonemodel;

import android.annotation.SuppressLint;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.Objects;

import nl.igorski.mwengine.core.JavaUtilities;
import nl.igorski.mwengine.core.SampleManager;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link TrackFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class TrackFragment extends Fragment {

    private static final String ARG_PARAM1 = "param1";


    private static final String LOG_TAG = "MWENGINE_FRAG"; // logcat identifier

    private MWEngineManager     mAudioEngine;

    private int whichTrack;

    private boolean isPlaying = false;
    private boolean isForward = true;
    private boolean isLooping;
    private boolean isReverbOn;
    private boolean isMetroOn;

    private TextView mReadPLabel;
    private TextView mReadPValue;
    private EditableWaveformView mWaveformView;

    private TracksViewModel viewModel;
    private TracksViewModel.TrackModel trackModel;


    public TrackFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @return A new instance of fragment Track1Fragment.
     */
    public static TrackFragment newInstance(int param1) {
        TrackFragment fragment = new TrackFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_PARAM1, param1);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            whichTrack = getArguments().getInt(ARG_PARAM1);
        }
        mAudioEngine = MWEngineManager.getInstance();

        viewModel = new ViewModelProvider(requireActivity()).get(TracksViewModel.class);
        trackModel = viewModel.getTrack(whichTrack);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_track, container, false);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // init waveformview
        mWaveformView = view.findViewById(R.id.waveformview);
        mWaveformView.setSampleRate(mAudioEngine.getSampleRate());
        mWaveformView.setChannels(1);
        mWaveformView.setShowTextAxis(false);

        mReadPLabel = view.findViewById(R.id.readP_label);
        mReadPValue = view.findViewById(R.id.readP_value);
        mAudioEngine.getTrack(whichTrack).setPlaybackListener(new PlaybackListener() {
            @Override
            public void onProgress(int readP) {
                mReadPValue.setText(String.valueOf(readP));
                mWaveformView.setMarkerPosition(readP);
            }

            @Override
            public void onProgress(int[] cursorPos) {
                mReadPValue.setText(String.valueOf(cursorPos[0]));
                mWaveformView.setMarkerPosition(cursorPos);
            }

            @Override
            public void onCompletion() {
//                Log.d(LOG_TAG, "//////// Completed ////////");
                if (!isLooping) // ignore the signal when looping :
                    // running metro cause main loop to temporary set looping off
                    Objects.requireNonNull(getActivity()).runOnUiThread(() -> trackModel.setIsPlaying(false));
                // will call engine track stop
            }
        });

        Spinner spinner =  view.findViewById( R.id.SampleSpinner);
        spinner.setOnItemSelectedListener( new SoundChangeHandler() );

        // set spinner and waveformview display

        trackModel.getSampleSelection().observe(this, new Observer<Integer>() {
            @Override
            public void onChanged(Integer integer) {
                short[] buf = FileUtil.shortBuffers.get(integer);
                mWaveformView.setSamples(buf);
//                mAudioEngine.getTrack(whichTrack).setSample(String.valueOf(whichTrack), integer);
                spinner.setSelection(integer);
            }
        });

        mWaveformView.setWaveformListener(new EditableWaveformView.WaveformListener() {
            @Override
            public void onSelectionStartChanged(float start) {
                trackModel.setSampleStart(start);
            }
            @Override
            public void onSelectionEndChanged(float end) {
                trackModel.setSampleEnd(end);
            }
        });
        trackModel.getSampleStart().observe(this, mWaveformView::setSelectionStart);
        trackModel.getSampleEnd().observe(this, mWaveformView::setSelectionEnd);


        Button playButton = view.findViewById(R.id.PlayPauseButton);
        playButton.setOnTouchListener((view15, motionEvent) -> {
            if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                trackModel.setIsPlaying(!isPlaying);
                return true;
            }
            return false;
        });

        Button piqueButton = view.findViewById( R.id.piqueButton );
        piqueButton.setOnTouchListener((view1, motionEvent) -> {
            int action = motionEvent.getAction();
            if (action == MotionEvent.ACTION_DOWN && !isPlaying) {
                trackModel.setIsPlaying(true);
                return true;
            }
            else if (action == MotionEvent.ACTION_UP) {
                trackModel.setIsPlaying(false);
                return true;
            }
            return false;
        });

        // observe current playing state and reset UI Play & Pique on change
        trackModel.getIsPlaying().observe(this, aBoolean -> {
            isPlaying = aBoolean;
            playButton.setActivated(aBoolean);
            piqueButton.setActivated(aBoolean);
            // others play button on the ui
            if (!isPlaying) mWaveformView.setMarkerPosition(-1);
        });

        Button directionButton = view.findViewById( R.id.direction_button );
        directionButton.setOnTouchListener((view14, motionEvent) -> {
            if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                trackModel.setIsForward(!isForward);
                return true;
            }
            return false;
        });
        trackModel.getIsForward().observe(this, aBoolean -> {
            isForward = aBoolean;
            directionButton.setText( isForward ? R.string.sample_forward : R.string.sample_backward);
        });

        Button loopButton = view.findViewById( R.id.loop_button );
        loopButton.setOnTouchListener((view12, motionEvent) -> {
            if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                trackModel.setIsLooping(!isLooping);
                return true;
            }
            return false;
        });
        trackModel.getIsLooping().observe(this, aBoolean -> {
            isLooping = aBoolean;
            loopButton.setActivated(isLooping);
        });

        SeekBar start = view.findViewById( R.id.sample_start );
        start.setOnSeekBarChangeListener( new SampleStartChangeHandler());
        trackModel.getSampleStart().observe(this, aFloat ->
                start.setProgress((int) (aFloat * 100)));

        SeekBar end = view.findViewById( R.id.sample_end );
        end.setOnSeekBarChangeListener( new SampleEndChangeHandler());
        trackModel.getSampleEnd().observe(this, aFloat ->
                end.setProgress((int) (aFloat * 100)));

        SeekBar speed = view.findViewById( R.id.speed );
        speed.setOnSeekBarChangeListener( new SampleSpeedChangeHandler());
        trackModel.getSampleSpeed().observe(this, aFloat ->
                speed.setProgress((int) (aFloat * 100)));

        SeekBar cutoff = view.findViewById( R.id.FilterCutoffSlider );
        cutoff.setOnSeekBarChangeListener( new FilterCutoffChangeHandler());
        trackModel.getFilterCutoff().observe(this, aFloat ->
                cutoff.setProgress((int) (aFloat * 100)));

        SeekBar res = view.findViewById( R.id.FilterQ );
        res.setOnSeekBarChangeListener( new FilterQChangeHandler());
        trackModel.getFilterResonance().observe(this, aFloat ->
                res.setProgress((int) (aFloat * 100)));

        SeekBar reverb = view.findViewById( R.id.reverb );
        reverb.setOnSeekBarChangeListener( new ReverbChangeHandler());
        trackModel.getReverb().observe(this, aFloat ->
                reverb.setProgress((int) (aFloat * 100)));

        SwitchCompat revSwitch = view.findViewById( R.id.reverb_switch );
        revSwitch.setOnClickListener(v -> trackModel.setIsReverbOn(((SwitchCompat) v).isChecked()));
        trackModel.getIsReverbOn().observe(this, revSwitch::setChecked);

        SeekBar metro = view.findViewById( R.id.metronome );
        metro.setOnSeekBarChangeListener( new MetronomeChangeHandler());
        trackModel.getMetroRate().observe(this, aFloat ->
                metro.setProgress((int) (aFloat * 100)));

        SwitchCompat metroSwitch = view.findViewById( R.id.metronome_switch );
        metroSwitch.setOnClickListener(v -> trackModel.setIsMetroOn(((SwitchCompat)v).isChecked()));
        trackModel.getIsMetroOn().observe(this, metroSwitch::setChecked);

        SeekBar vol = view.findViewById( R.id.VolumeSlider );
        vol.setOnSeekBarChangeListener( new VolumeChangeHandler() );
        trackModel.getVolume().observe(this, aFloat ->
                vol.setProgress((int) (aFloat * 100)));

        view.findViewById(R.id.oneshot_button).setOnClickListener(v ->
                mAudioEngine.getTrack(whichTrack).oneShotTrigger());

        view.findViewById(R.id.oneshot_button2).setOnClickListener(v -> {
            mAudioEngine.getTrack(whichTrack).oneShotStop();
//            Log.d(LOG_TAG, "//////// STOP ONE SHOT ////////");
        });

        trackModel.setInitialValue();

    }


    /* event handlers */

    private class SoundChangeHandler implements AdapterView.OnItemSelectedListener {
        public void onItemSelected(AdapterView<?> parent, View view, int pos,long id) {
//            Log.d(LOG_TAG, "//////// SoundChangeHandler pos: " + pos);
            String selectedValue = parent.getItemAtPosition(pos).toString();
            String name = "";
            if (selectedValue.toLowerCase().equals("bach")) {
                name = "001";
            } else if (selectedValue.toLowerCase().equals("bonjour")) {
                name = "000";
            }
            else if (selectedValue.toLowerCase().equals("sin 1000hz 0db")) {
                name = "002";
            }
            else if (selectedValue.toLowerCase().equals("sin 1000hz -3db")) {
                name = "003";
            }
            if ( name != "") {
                trackModel.setSampleSelection(pos);
//                trackModel.setSampleName(name);
                trackModel.resetSampleLength();
                mWaveformView.setMarkerPosition(mAudioEngine.getTrack(whichTrack).getSampleStart());
            }

        }
        @Override
        public void onNothingSelected(AdapterView<?> arg0) {}
    }

    private class SampleStartChangeHandler implements SeekBar.OnSeekBarChangeListener {
        public void onProgressChanged( SeekBar seekBar, int progress, boolean fromUser ) {
            trackModel.setSampleStart(  progress / 100f  );
        }
        public void onStartTrackingTouch( SeekBar seekBar ) {}
        public void onStopTrackingTouch ( SeekBar seekBar ) {}
    }

    private class SampleEndChangeHandler implements SeekBar.OnSeekBarChangeListener {
        public void onProgressChanged( SeekBar seekBar, int progress, boolean fromUser ) {
            trackModel.setSampleEnd(  progress / 100f  );
        }
        public void onStartTrackingTouch( SeekBar seekBar ) {}
        public void onStopTrackingTouch ( SeekBar seekBar ) {}
    }

    private class SampleSpeedChangeHandler implements SeekBar.OnSeekBarChangeListener {
        public void onProgressChanged( SeekBar seekBar, int progress, boolean fromUser ) {
            trackModel.setSampleSpeed(  progress / 100f  );
        }
        public void onStartTrackingTouch( SeekBar seekBar ) {}
        public void onStopTrackingTouch ( SeekBar seekBar ) {}
    }

    private class FilterCutoffChangeHandler implements SeekBar.OnSeekBarChangeListener {
        public void onProgressChanged( SeekBar seekBar, int progress, boolean fromUser ) {
            trackModel.setFilterCutoff(  progress / 100f  );
        }
        public void onStartTrackingTouch( SeekBar seekBar ) {}
        public void onStopTrackingTouch ( SeekBar seekBar ) {}
    }

    private class FilterQChangeHandler implements SeekBar.OnSeekBarChangeListener {
        public void onProgressChanged( SeekBar seekBar, int progress, boolean fromUser ) {
            trackModel.setFilterResonance(  progress / 100f  );
        }
        public void onStartTrackingTouch( SeekBar seekBar ) {}
        public void onStopTrackingTouch ( SeekBar seekBar ) {}
    }

    private class ReverbChangeHandler implements SeekBar.OnSeekBarChangeListener {
        public void onProgressChanged( SeekBar seekBar, int progress, boolean fromUser ) {
            trackModel.setReverb(  progress / 100f  );
        }
        public void onStartTrackingTouch( SeekBar seekBar ) {}
        public void onStopTrackingTouch ( SeekBar seekBar ) {}
    }

    private class MetronomeChangeHandler implements SeekBar.OnSeekBarChangeListener {
        public void onProgressChanged( SeekBar seekBar, int progress, boolean fromUser ) {
            trackModel.setMetroRate(  progress / 100f  );
        }
        public void onStartTrackingTouch( SeekBar seekBar ) {}
        public void onStopTrackingTouch ( SeekBar seekBar ) {}
    }

    private class VolumeChangeHandler implements SeekBar.OnSeekBarChangeListener {
        public void onProgressChanged( SeekBar seekBar, int progress, boolean fromUser ) {
            trackModel.setVolume(  progress / 100f  );
        }
        public void onStartTrackingTouch( SeekBar seekBar ) {}
        public void onStopTrackingTouch ( SeekBar seekBar ) {}
    }
}