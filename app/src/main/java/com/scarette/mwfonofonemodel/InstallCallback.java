package com.scarette.mwfonofonemodel;

import static android.content.Context.LAYOUT_INFLATER_SERVICE;

import android.annotation.SuppressLint;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class InstallCallback {

    private static final String DEBUG_TAG = "MWFon-InstallCB";

    private PopupWindow popupWindow;
    private TextView itemUpdateView;
    private final AppCompatActivity activity;
    private final View mainView;

    InstallCallback(AppCompatActivity activity, View view) {
        this.activity = activity;
        this.mainView = view;
    }

    public void onInstall() {
        // inflate the layout of the popup window
        Log.d( DEBUG_TAG, " onInstall() ");

        LayoutInflater inflater = (LayoutInflater)
                activity.getSystemService(LAYOUT_INFLATER_SERVICE);
        @SuppressLint("InflateParams")
        View popupView = inflater.inflate(R.layout.install_popup_window, null);

        // create the popup window
        int width = LinearLayout.LayoutParams.WRAP_CONTENT;
        int height = LinearLayout.LayoutParams.WRAP_CONTENT;
        popupWindow = new PopupWindow(popupView, width, height, false);
        popupWindow.setTouchable(false);
        popupWindow.setOutsideTouchable(false);
        itemUpdateView = popupView.findViewById(R.id.num_item_installed_view);

        // show the popup window
        // which view you pass in doesn't matter, it is only used for the window tolken
        mainView.post(() -> popupWindow.showAtLocation(mainView, Gravity.CENTER, 0, 0));
    }

    public void onItemUpdate(String numOfNum) {
        mainView.post(() -> itemUpdateView.setText(numOfNum));
    }

    public void onInstallFinished() {
        Log.d( DEBUG_TAG, "CallbackInterface.onInstallFinished() called" );
        mainView.post(() -> {
            popupWindow.dismiss();
            itemUpdateView = null;
            popupWindow = null;
        });
        Log.d( DEBUG_TAG, "CallbackInterface.onInstallFinished() finished" );
        ((MainActivity)activity).onInstallFinished();
    }
}
