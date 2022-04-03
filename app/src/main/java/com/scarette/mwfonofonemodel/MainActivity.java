package com.scarette.mwfonofonemodel;

import android.Manifest;
import android.annotation.TargetApi;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Vector;

import nl.igorski.mwengine.MWEngine;
import nl.igorski.mwengine.core.*;

public class MainActivity extends AppCompatActivity {

    /**
     * IMPORTANT : when creating native layer objects through JNI it
     * is important to remember that when the Java references go out of scope
     * (and thus are finalized by the garbage collector), the SWIG interface
     * will invoke the native layer destructors. As such we hold strong
     * references to JNI Objects during the application lifetime
     */

    private FragmentManager mFragmentManager;

    private TracksViewModel viewModel;


    private MWEngineManager     mAudioEngine;

    private AppCompatImageButton mixerButton;
    private boolean             isMixerOpen = false;

    private boolean _inited           = false;

    private Vector<MWEngineManager.Track> tracks;

    // AAudio is only supported from Android 8/Oreo onwards.
    private boolean _supportsAAudio     = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O;
    private Drivers.types  _audioDriver = _supportsAAudio ? Drivers.types.AAUDIO : Drivers.types.OPENSL;


    private static String LOG_TAG = "MWENGINE"; // logcat identifier
    private static int PERMISSIONS_CODE = 8081981;

    private static final String DEBUG_TAG = "MWFon-MainActivity";
    private static View mainView;
    private static AppCompatActivity activity;

    public static final InstallCallbackInterface installCallbackInterface = new InstallCallbackInterface() {
        private PopupWindow popupWindow;
        private TextView itemUpdateView;
        @Override
        public void onInstall() {
            // inflate the layout of the popup window
            Log.d( DEBUG_TAG, "CallbackInterface onInstall() ");

            LayoutInflater inflater = (LayoutInflater)
                    activity.getSystemService(LAYOUT_INFLATER_SERVICE);
            View popupView = inflater.inflate(R.layout.popup_window, null);

            // create the popup window
            int width = LinearLayout.LayoutParams.WRAP_CONTENT;
            int height = LinearLayout.LayoutParams.WRAP_CONTENT;
            boolean focusable = false; //
            popupWindow = new PopupWindow(popupView, width, height, focusable);
            popupWindow.setFocusable(false);
            popupWindow.setTouchable(false);
            popupWindow.setOutsideTouchable(false);
            itemUpdateView = popupView.findViewById(R.id.num_item_installed_view);

            // show the popup window
            // which view you pass in doesn't matter, it is only used for the window tolken
            mainView.post(new Runnable() {
                @Override
                public void run() {
                    popupWindow.showAtLocation(mainView, Gravity.CENTER, 0, 0);
                }
            });
        }

        @Override
        public void onItemUpdate(String numOfNum) {
            mainView.post(new Runnable() {
                @Override
                public void run() {
                    itemUpdateView.setText(numOfNum);
                }
            });
        }

        @Override
        public void onInstallFinished() {
            Log.d( DEBUG_TAG, "CallbackInterface.onInstallFinished() called" );
            mainView.post(new Runnable() {
                @Override
                public void run() {
                    popupWindow.dismiss();
                }
            });
            Log.d( DEBUG_TAG, "CallbackInterface.onInstallFinished() finished" );
        }
    };

    /* public methods */

    /**
     * Called when the activity is created. This also fires
     * on screen orientation changes.
     */
    @Override
    public void onCreate( Bundle savedInstanceState ) {
        super.onCreate( savedInstanceState );
        activity = this; // needed for the popup message in the InstallCallback

        // Create Repository if not created - that will install the assetes if first time
        Repository repository = Repository.getInstance();
        repository.init(getApplication());

        setContentView( R.layout.activity_main );

        // these may not necessarily all be required for your use case (e.g. if you're not recording
        // from device audio inputs or reading/writing files) but are here for self-documentation

        if ( android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M ) {
            String[] PERMISSIONS = {
                    Manifest.permission.RECORD_AUDIO, // RECORD_AUDIO must be granted prior to engine.start()
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            };
            // Check if we have all the necessary permissions, if not: prompt user
            int permission = checkSelfPermission( Manifest.permission.RECORD_AUDIO );
            if ( permission == PackageManager.PERMISSION_GRANTED )
                init();
            else
                requestPermissions( PERMISSIONS, PERMISSIONS_CODE );
        }


        // We do not record hence we do not need permission
//        init();  // perhaps we need....
    }

    @Override
    public void setContentView(int layoutResID) {
        Log.d( DEBUG_TAG, "setContentView() called" );
        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        mainView = inflater.inflate(R.layout.activity_main, null);
        super.setContentView(mainView);
    }

    @TargetApi( Build.VERSION_CODES.M )
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != PERMISSIONS_CODE) return;
        for (int i = 0; i < permissions.length; i++) {
            String permission = permissions[i];
            int grantResult = grantResults[i];
            if (permission.equals(Manifest.permission.RECORD_AUDIO) && grantResult == PackageManager.PERMISSION_GRANTED) {
                init();
            } else {
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSIONS_CODE);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // first parameter is the file for icon and second one is menu
        getMenuInflater().inflate(R.menu.info_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        final InfoDialog dialog = InfoDialog.newInstance();
        dialog.show(getSupportFragmentManager(), null);
        return super.onOptionsItemSelected(item);
    }


    /**
     * Called when the activity is destroyed. This also fires
     * on screen orientation changes, hence the override as we need
     * to watch the engines memory allocation outside of the Java environment
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    private void init() {

//        Log.d( LOG_TAG, "initing MWEngineActivity" );

        // get instance of native audio engine
        mAudioEngine = MWEngineManager.getInstance();
        // optimize activity
        MWEngine.optimizePerformance( this );
        // init engine - will only reset activity pointer if engine is already initialized
        mAudioEngine.initAudioEngine(this);
        tracks = mAudioEngine.getTracks();

        // set up view model
        viewModel = new ViewModelProvider(this).get(TracksViewModel.class);

        // init fragments - this also allow the creation of each track in viewModel
        mFragmentManager = getSupportFragmentManager();
        TrackFragment track1 = TrackFragment.newInstance(0);
        TrackFragment track2 = TrackFragment.newInstance(1);
        TrackFragment track3 = TrackFragment.newInstance(2);
        FragmentTransaction transaction = mFragmentManager.beginTransaction();
        transaction.add(R.id.track1_container, track1, null);
        transaction.add(R.id.track2_container, track2, null);
        transaction.add(R.id.track3_container, track3, null);
        transaction.commitNow();

        // set mixer view
        LinearLayoutCompat mixerView = findViewById(R.id.mixer_view);
        mixerView.setVisibility(View.GONE);
        mixerButton = findViewById(R.id.mixer_button);
        mixerButton.setOnClickListener(view -> {
            isMixerOpen = !isMixerOpen;
            mixerView.setVisibility(isMixerOpen ? View.VISIBLE : View.GONE);
        });

        SeekBar mixerVol1 = findViewById(R.id.MixerVolumeSlider1);
        viewModel.getTrack(0).getVolume().observe(this, aFloat ->
                mixerVol1.setProgress((int)(aFloat * 100)));

        mixerVol1.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                viewModel.getTrack(0).setVolume(seekBar.getProgress() / 100f);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        SeekBar mixerVol2 = findViewById(R.id.MixerVolumeSlider2);
        viewModel.getTrack(1).getVolume().observe(this, aFloat ->
                mixerVol2.setProgress((int)(aFloat * 100)));
        mixerVol2.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                viewModel.getTrack(1).setVolume(seekBar.getProgress() / 100f);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        SeekBar mixerVol3 = findViewById(R.id.MixerVolumeSlider3);
        viewModel.getTrack(2).getVolume().observe(this, aFloat ->
                mixerVol3.setProgress((int)(aFloat * 100)));
        mixerVol3.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                viewModel.getTrack(2).setVolume(seekBar.getProgress() / 100f);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        _inited = true;
    }

    @Override
    public void onWindowFocusChanged( boolean hasFocus ) {
//        Log.d( LOG_TAG, "window focus changed for MWEngineActivity, has focus > " + hasFocus );

        if ( !hasFocus ) {
            // suspending the app - halt audio rendering in MWEngine Thread to save CPU cycles
            if ( mAudioEngine != null )
                mAudioEngine.stop();
        }
        else {
            // returning to the app
            if ( !_inited )
                init();          // initialize this example application
            else
                mAudioEngine.start(); // resumes audio rendering
        }
    }

}