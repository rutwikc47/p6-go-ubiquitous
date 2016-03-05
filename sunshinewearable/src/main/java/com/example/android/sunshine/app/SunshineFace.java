/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.AsyncTaskLoader;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.bumptech.glide.Glide;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class SunshineFace extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    Bitmap mWeatherBitmap;

    public final String LOG_TAG = SunshineFace.class.getSimpleName();

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<SunshineFace.Engine> mWeakReference;

        public EngineHandler(SunshineFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunshineFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine implements GoogleApiClient.ConnectionCallbacks,
    GoogleApiClient.OnConnectionFailedListener,
    DataApi.DataListener{
        final Handler mUpdateTimeHandler = new EngineHandler(this);

        Time mTime;
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };

        float mXOffset;
        float mYOffset;
        float mrxof;
        float mryof;
        float colorpd;
        float mDXOffset;
        float mDYOffset;
        float mTXOffset;
        float mTYOffset;
        float mLTXOffset;
        float mBitXOffset;
        float mBitYOffset;

        boolean mRegisteredTimeZoneReceiver = false;
        boolean mAmbient;

        Paint mBackgroundPaint;
        Paint mTextPaint;
        Paint mDatePaint;
        Paint mHightpaint;
        Paint mLowtpaint;
        Paint mBaseBg;
        Paint mBitPaint;


        int mWeatherId;

        String high;
        String low;

        GoogleApiClient mGoogleApiClient;


        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());
            Resources resources = SunshineFace.this.getResources();

            mrxof=resources.getDimension(R.dimen.digital_rx_offset);
            mryof=resources.getDimension(R.dimen.digital_ry_offset);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.custom_bg));

            mBaseBg =new Paint();
            mBaseBg.setColor(resources.getColor(R.color.base_bg));

            mTextPaint = new Paint();
            mTextPaint = createTextPaint(resources.getColor(R.color.digital_text));

            mDatePaint =new Paint();
            mDatePaint = createdatePaint(resources.getColor(R.color.datetext));

            mHightpaint =new Paint();
            mHightpaint = createTempPaint(resources.getColor(R.color.highttext));

            mLowtpaint=new Paint();
            mLowtpaint=createTempPaint(resources.getColor(R.color.lowttext));

            mBitPaint=new Paint();

            mTime = new Time();

            /**
             * Write the code for Bitmap setter
             */

            mGoogleApiClient = new GoogleApiClient.Builder(SunshineFace.this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(Wearable.API)
                    .build();
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            colorpd =height*0.75f;
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        private Paint createdatePaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        private Paint createTempPaint(int textColor){
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
                mGoogleApiClient.connect();

            } else {
                unregisterReceiver();
                mGoogleApiClient.disconnect();
            }
            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            SunshineFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SunshineFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);


            // Load resources that have alternate values for round watches.
            Resources resources = SunshineFace.this.getResources();

            boolean isRound = insets.isRound();

            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            mDXOffset=resources.getDimension(isRound
                    ? R.dimen.digital_dx_offset_round : R.dimen.digital_dx_offset);
            mTXOffset=resources.getDimension(isRound
                    ? R.dimen.digital_tx_offset_round : R.dimen.digital_tx_offset);
            mLTXOffset=resources.getDimension(isRound
                    ? R.dimen.digital_ltx_offset_round : R.dimen.digital_ltx_offset);
            mBitXOffset=resources.getDimension(isRound
                    ? R.dimen.digital_bitx_offset_round : R.dimen.digital_bitx_offset);
            mYOffset = resources.getDimension(isRound
                    ? R.dimen.digital_y_offset_round : R.dimen.digital_y_offset);
            mDYOffset= resources.getDimension(isRound
                    ? R.dimen.digital_dy_offset_round : R.dimen.digital_dy_offset);

            mTYOffset= resources.getDimension(isRound
                    ? R.dimen.digital_ty_offset_round : R.dimen.digital_dy_offset);

            mBitYOffset= resources.getDimension(isRound
                    ? R.dimen.digital_bity_offset_round : R.dimen.digital_bity_offset);

            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);

            float dateSize= resources.getDimension(isRound?R.dimen.digital_datetext_round : R.dimen.digital_datetext);

            float tempSize= resources.getDimension(isRound?R.dimen.digital_temptext_round : R.dimen.digital_temptext);

            mTextPaint.setTextSize(textSize);
            mDatePaint.setTextSize(dateSize);
            mHightpaint.setTextSize(tempSize);
            mLowtpaint.setTextSize(tempSize);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTextPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0,bounds.top,bounds.width(),bounds.height(), mBaseBg);
                canvas.drawRect(0,bounds.top,bounds.width(), colorpd, mBackgroundPaint);
//                canvas.drawBitmap(mBackgroundb, 0, 0, mBackgroundPaint);

            }

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            mTime.setToNow();
            String text = mAmbient
                    ? String.format("%d:%02d", mTime.hour, mTime.minute)
                    : String.format("%d:%02d", mTime.hour, mTime.minute);
            canvas.drawText(text, mXOffset, mYOffset, mTextPaint);

            SimpleDateFormat sdf = new SimpleDateFormat("EEE, MMM dd yyyy");
            String currentDateandDay = sdf.format(new Date());

            String datetext=currentDateandDay;
            canvas.drawText(datetext, mDXOffset, mDYOffset, mDatePaint);

            if (high!=null && low!=null){
                canvas.drawText(high,mTXOffset,mTYOffset, mHightpaint);
                canvas.drawText(low, mLTXOffset, mTYOffset, mLowtpaint);
            }else {
                canvas.drawText("-",mTXOffset,mTYOffset, mHightpaint);
                canvas.drawText("-", mLTXOffset, mTYOffset, mLowtpaint);
            }

            if (mWeatherBitmap!=null){
                canvas.drawBitmap(mWeatherBitmap,mBitXOffset,mBitYOffset,mBitPaint);
            }
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        @Override
        public void onConnected(Bundle bundle) {
            Log.e(LOG_TAG, "Api Connected!");
            Wearable.DataApi.addListener(mGoogleApiClient, Engine.this);
        }

        @Override
        public void onConnectionSuspended(int i) {

        }

        @Override
        public void onDataChanged(DataEventBuffer dataEventBuffer) {
            for (DataEvent dataEvent : dataEventBuffer){
                if (dataEvent.getType()==DataEvent.TYPE_CHANGED){
                    DataMap dataMap = DataMapItem.fromDataItem(dataEvent.getDataItem()).getDataMap();
                    String path=dataEvent.getDataItem().getUri().getPath();
                    if (path.equals("/step-counter")){
                        high=dataMap.getString("high");
                        low=dataMap.getString("low");
                        mWeatherId=dataMap.getInt("weatherid");
                        new getImage().execute(mWeatherId);
                        invalidate();
                    }
                }
            }
            Log.e(LOG_TAG,high);

        }

        public class getImage extends AsyncTask<Integer,Void,Void>{
            @Override
            protected Void doInBackground(Integer... params) {
                int weatherid= params[0];
                Resources resources = getApplicationContext().getResources();
                int artResourceId = Utility.getArtResourceForWeatherCondition(weatherid);
                String artUrl = Utility.getArtUrlForWeatherCondition(getApplicationContext(), weatherid);
                int IconWidth = resources.getDimensionPixelSize(R.dimen.mbitmap_width);
                int IconHeight = resources.getDimensionPixelSize(R.dimen.mbitmap_height);

                try {
                    mWeatherBitmap = Glide.with(getApplicationContext())
                            .load(artUrl)
                            .asBitmap()
                            .error(artResourceId)
                            .fitCenter()
                            .into(IconWidth, IconHeight).get();
                } catch (InterruptedException | ExecutionException e) {
                    Log.e(LOG_TAG, "Error retrieving icon from " + artUrl, e);
                    mWeatherBitmap = BitmapFactory.decodeResource(resources, artResourceId);
                }
                return null;
            }
        }


        @Override
        public void onConnectionFailed(ConnectionResult connectionResult) {

        }
    }


}
