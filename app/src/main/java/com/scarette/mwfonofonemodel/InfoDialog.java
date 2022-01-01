package com.scarette.mwfonofonemodel;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.DialogFragment;

import nl.igorski.mwengine.MWEngine;

public class InfoDialog extends DialogFragment {

    public static final String LOG_TAG = "MWE_infoFragment";

    public InfoDialog() {
    }

    public static InfoDialog newInstance() {
        InfoDialog dialog = new InfoDialog();
        return dialog;
    }



    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    @SuppressLint("InflateParams")
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP_MR1)
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {

        LayoutInflater inflater = getLayoutInflater();
        View view = inflater.inflate(R.layout.info_dialog, null);
        TextView infoView = view.findViewById(R.id.info_text);
        infoView.setText(getInfo());

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert);
        builder.setView(view);
        Dialog dialog = builder.create();

        dialog.setCanceledOnTouchOutside(false);
        TextView discardButton = view.findViewById(R.id.discard_button);
        discardButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
            }
        });

        return dialog;
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        setShowsDialog(true);
    }

    public String getInfo() {

        AudioManager audioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);

        String rate = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
        int SAMPLE_RATE = Integer.parseInt(rate);
        String str1 = "PROPERTY_OUTPUT_SAMPLE_RATE: " + rate + "\n\n";

        String size = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER);
        String str2 = "PROPERTY_OUTPUT_FRAMES_PER_BUFFER: " + size + "\n\n";

        int playMinBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        String str3 = "AudioTrack minBufferSize mono PCM16bit: " + playMinBufferSize + "\n\n";

        int playMinBufferSizeFloat = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_FLOAT);
        String str4 = "AudioTrack minBufferSize mono PCMFloat: " + playMinBufferSizeFloat + "\n\n";

        int MWEngineRecSampleRate = MWEngine.getRecommendedSampleRate(getContext());
        String str5 = "MWEngineRecommendedSampleRate: " + MWEngineRecSampleRate + "\n\n";

        int MWEngineRecBufferSize = MWEngine.getRecommendedBufferSize(getContext());
        String str6 = "MWEngineRecommendedBufferSize: " + MWEngineRecBufferSize + "\n\n";

//        int recMinBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
//        Log.d(LOG_TAG, "AudioRecord getMinBufferSize: " + recMinBufferSize);

        String unprocessed = audioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED);
        String str7 = "PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED: " + unprocessed + "\n\n";

        PackageManager packageManager = getActivity().getPackageManager();
        boolean hasLowLatencyFeature = packageManager.hasSystemFeature(PackageManager.FEATURE_AUDIO_LOW_LATENCY);
        String str8 = "FEATURE_AUDIO_LOW_LATENCY: " + hasLowLatencyFeature + "\n\n";

        boolean hasProFeature = packageManager.hasSystemFeature(PackageManager.FEATURE_AUDIO_PRO);
        String str9 = "FEATURE_AUDIO_PRO: " + hasProFeature + "\n\n";

        return str1 + str2 + str3 + str4 + str5 + str6 + str7 + str8 + str9;
    }
}

