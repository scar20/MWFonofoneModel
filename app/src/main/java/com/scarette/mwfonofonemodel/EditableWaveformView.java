package com.scarette.mwfonofonemodel;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import androidx.core.content.ContextCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;


import com.newventuresoftware.waveform.WaveformView;

public class EditableWaveformView extends WaveformView {

    private static final String DEBUG_TAG = "MWEditableWaveformView";

    // Added interface
    public interface WaveformListener {
        //        public void onWaveformControlChanged(float x, float y);
        public void onSelectionStartChanged(float start);
        public void onSelectionEndChanged(float end);
    };

    private WaveformListener mListener;

    private Paint mRecordStrokePaint;
    private int mMode, mAudioLength;

    private Rect drawRect;

    private int width, height;
    private float xStep, centerY;

    // Added for selection start - end
    private int mSelectionStart;
    private int mSelectionEnd;
    private int mSampleLength, mMarkerPosition;
    private int[] mMarkersPositions = new int[32]; // hard coded value, need something more flexible
    private float mStartPos = 0;
    private float mEndPos = 1;
    private Rect mSelectionRect;
    private int mSelectionColor, mMarkerColor, mMainMarkerColor;
    private int mBackgroundColor;
    private Paint mSelectionPaint, mMarkerPaint, mMainMarkerPaint;
    private GestureDetector mGestureDetector;
    private ScaleGestureDetector mScaleGestureDetector;
    //    private WaveformListener mListener;
    private float mInitialScaleSpan;
    private Path mWaveform;
    private float mDensity;
    private int mOffsetL;
    private int mOffsetR;
    private float mOffset;

    public EditableWaveformView(Context context) {
        super(context);
        init(context, null, 0);
    }

    public EditableWaveformView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs, 0);
    }

    public EditableWaveformView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context, attrs, defStyle);
    }

    private void init(Context context, AttributeSet attrs, int defStyle) {
        // Load attributes
        final TypedArray a = getContext().obtainStyledAttributes(
                attrs, R.styleable.EditableWaveformView ,defStyle, 0);

//        mMode = a.getInt(R.styleable.EditableWaveformView_mode, MODE_PLAYBACK);
        mMode = super.getMode();

        float strokeThickness = a.getFloat(com.newventuresoftware.waveform.R.styleable.WaveformView_waveformStrokeThickness, 1f);
        int mRecordColor = a.getColor(R.styleable.EditableWaveformView_waveformRecordColor,
                ContextCompat.getColor(context, R.color.default_record_waveform));
        // Added for selection start - end
        mBackgroundColor = a.getColor(R.styleable.EditableWaveformView_backgroundColor,
                ContextCompat.getColor(context, R.color.default_background));
        mSelectionColor = a.getColor(R.styleable.EditableWaveformView_selectionColor,
                ContextCompat.getColor(context, R.color.default_selection));
        mMarkerColor = a.getColor(R.styleable.EditableWaveformView_playbackIndicatorColor,
                ContextCompat.getColor(context, R.color.default_playback_indicator));
        mMainMarkerColor = a.getColor(R.styleable.EditableWaveformView_mainPlaybackIndicatorColor,
                ContextCompat.getColor(context, R.color.default_record_waveform));

        mRecordStrokePaint = new Paint();
        mRecordStrokePaint.setColor(mRecordColor);
        mRecordStrokePaint.setStyle(Paint.Style.STROKE);
        mRecordStrokePaint.setStrokeWidth(strokeThickness);
        mRecordStrokePaint.setAntiAlias(true);

        // Added for selection start - end
        mSelectionPaint = new Paint();
        mSelectionPaint.setStyle(Paint.Style.FILL);
        mSelectionPaint.setColor(mSelectionColor);

        mMarkerPaint = new Paint();
        mMarkerPaint.setStyle(Paint.Style.STROKE);
        mMarkerPaint.setStrokeWidth(0);
        mMarkerPaint.setAntiAlias(true);
        mMarkerPaint.setColor(mMarkerColor);

        mMainMarkerPaint = new Paint();
        mMainMarkerPaint.setStyle(Paint.Style.STROKE);
        mMainMarkerPaint.setStrokeWidth(2.f);
        mMainMarkerPaint.setAntiAlias(true);
        mMainMarkerPaint.setColor(mMainMarkerColor);

        if (mMode == MODE_RECORDING) {

        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        width = getMeasuredWidth();
        height = getMeasuredHeight();
//        xStep = width / (mAudioLength * 1.0f);
        xStep = width / (mSampleLength * 1.0f);
        centerY = height / 2f;
        drawRect = new Rect(0, 0, width, height);
        // Added important mSelectionRect initialization
        mSelectionRect = new Rect((int)(mStartPos * width), 0, (int)mEndPos * width, height);
        // and also this that will be useful to convert event pos to dpi
        mDensity = getContext().getResources().getDisplayMetrics().density;
        // end Added important mSelectionRect initialization

        if (mMode == MODE_PLAYBACK) {

        }

    }

    // Draw
    @Override
    protected void onDraw(Canvas canvas) {

        if (mMode == MODE_PLAYBACK) {
            // Added to handle selection rectangle
            canvas.drawColor(mBackgroundColor);
            canvas.drawRect(mSelectionRect, mSelectionPaint);
            // end Added to handle selection rectangle

        }
        // draw horizontal center line - only visible when there is no sample
        canvas.drawLine(0f, height/2f, (float)width, height/2f, mMarkerPaint );
        super.onDraw(canvas);

        // draw cursor
//        Log.d(DEBUG_TAG, "marker: " + (xStep * mMarkerPosition) );
        for ( int i = 0; i < mMarkersPositions.length; i++) {
            int pos = mMarkersPositions[i];
            if (pos > -1 && pos < mSampleLength)
                canvas.drawLine(xStep * pos, 0, xStep * pos, height,
                        i == 0 ? mMainMarkerPaint : mMarkerPaint);

        }
    }

    // Added for selection start - end
    @Override
    public boolean onTouchEvent(MotionEvent event) {

        switch(event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                float x = event.getX();
                if (x > mSelectionRect.left -32 && x < mSelectionRect.right + 32) {
                    mOffsetL = (int) x - mSelectionRect.left;
                    mOffsetR = (int) x - mSelectionRect.right;
                    mOffset = x; // remember lastpos
                } else {
                    return false;
                }
                break;
            case MotionEvent.ACTION_MOVE:
//                mListener.waveformTouchMove(event.getX());
                x = event.getX();
                if (x > mSelectionRect.left -32 && x < mSelectionRect.right + 32) {
                    updateSelectionRect(x);
                }
                mOffset = x;
//                Log.d(DEBUG_TAG,"x: " + event.getX() + " y: " + event.getY());
                break;
            case MotionEvent.ACTION_UP:
//                mListener.waveformTouchEnd();
                break;
        }
        return true;
    }

    // Added to handle selection rectangle
    public void updateSelectionRect(float pos) {

        int x = (int)(pos); // / mDensity);
        float dx = x - mOffset;

        if(Math.abs(mOffsetL) < 32) {
            mSelectionRect.left = (int) Math.max(0, Math.min(mSelectionRect.right-32, x));
            mSelectionRect.right = (int) Math.min(width, Math.max(mSelectionRect.right, x));
            mStartPos = mSelectionRect.left / (float)width;
            mEndPos = mSelectionRect.right / (float)width;
        } else if(Math.abs(mOffsetR) < 32) {
            mSelectionRect.right = (int) Math.min(width, Math.max(mSelectionRect.left+32, x));
            mSelectionRect.left = (int) Math.max(0, Math.min(mSelectionRect.left, x));
            mStartPos = mSelectionRect.left / (float)width;
            mEndPos = mSelectionRect.right / (float)width;
        } else {
            mSelectionRect.left = (int) Math.max(0, mSelectionRect.left + dx);
            mSelectionRect.right = (int) Math.min(width, mSelectionRect.right + dx);
            mStartPos = mSelectionRect.left / (float)width;
            mEndPos = mSelectionRect.right / (float)width;
//            Log.d(DEBUG_TAG,"x: " + x + " move both ");
        }
        // transform value for interface
        if( mListener != null ) {
//            float range = (mSelectionRect.right - mSelectionRect.left) / (float)width;
//            Log.d(DEBUG_TAG,"mSelectionRect.right: " + mSelectionRect.right + " - " +
//                    "mSelectionRect.left " + mSelectionRect.left + " = range: " + range);
//            float half = (mSelectionRect.right - mSelectionRect.left) / 2f;
//            float center = (mSelectionRect.left + half) / width;
//            mListener.onWaveformControlChanged(center, range);
            mListener.onSelectionStartChanged(mStartPos);
            mListener.onSelectionEndChanged(mEndPos);
        }
        invalidate();
    } // end Added to handle selection rectangle

    // Added to direct handling of selection start - end
    public void setSelectionStart(float pos) {
        if (mSelectionRect != null) {
            mSelectionRect.left = (int) (mStartPos = pos * width);
        }
        invalidate();
//        Log.d(DEBUG_TAG,"WaveformView.setSelectionStart(float pos) pos: " + pos);
    }
    public void setSelectionEnd(float pos) {
        if (mSelectionRect != null) {
            mSelectionRect.right = (int) (mEndPos = pos * width);
        }
        invalidate();
    }
    // end Added to direct handling of selection start - end

    // Added to set interface
    public void setWaveformListener(WaveformListener listener) {
        mListener = listener;
    }

    @Override
    public void setMarkerPosition(int markerPosition) {
        mMarkerPosition = markerPosition;
        postInvalidate();
    }

    public void setMarkerPosition(int[] markerPosition) {
        mMarkersPositions = markerPosition;
//        Log.d(DEBUG_TAG, "marker: " + mMarkersPositions.toString() );
        postInvalidate();
    }

    // override this method to invalidate the view on change
    @Override
    public void setSamples(short[] samples) {
        mSampleLength = samples.length; // use this value instead of mAudioLength in super
        xStep = width / (mSampleLength * 1.0f); // rescale xStep to adjust for new length
        super.setSamples(samples);
        invalidate();
    }
}