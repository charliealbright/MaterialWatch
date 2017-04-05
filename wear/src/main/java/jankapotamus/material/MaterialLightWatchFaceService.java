package jankapotamus.material;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Created by Charlie on 6/27/15.
 */
public class MaterialLightWatchFaceService extends CanvasWatchFaceService {

    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    @Override
    public Engine onCreateEngine() {
        return new WPEngine();
    }

    private class WPEngine extends CanvasWatchFaceService.Engine {

        static final int MSG_UPDATE_TIME = 0;
        static final float BITMAP_DIMEN = 20f;




        // Paint Elements
        Paint mBackgroundPaint;
        Paint mBlackPaint;
        Paint mHourPaint;
        Paint mMinutePaint;
        Paint mSecondPaint;
        Paint mCenterPaint;
        Paint mLBAPaint;
        Paint mLBAMinutePaint;
        Paint mLBAHourPaint;
        Paint mBatteryPaint;
        Paint mBatteryLowPaint;
        Paint mBatteryVeryLowPaint;
        Paint mTextPaint;
        Paint mIconPaint;
        Paint mBatteryBackgroundPaint;

        // Path for drawing date text
        Path mDateTextPath;

        // Bitmap variables for phone and watch icons
        Bitmap mPhoneBitmap;
        Bitmap mPhoneScaledBitmap;
        Bitmap mWatchBitmap;
        Bitmap mWatchScaledBitmap;

        // Battery variables
        float mPhoneBatteryLevel;
        float mWatchBatteryLevel;

        // API Variables
        private GoogleApiClient mClient;
        NodeApi.NodeListener mNodeListener;
        MessageApi.MessageListener mMessageListener;
        private String mNodeID;

        // Other variables
        boolean mIsDarkTheme;
        boolean mMute;
        boolean mRegisteredTimeZoneReceiver = false;
        boolean mLowBitAmbient;
        boolean mBurnInProtection;
        boolean firstCheck = true;

        Calendar mCalendar;

        final Handler mUpdateTimeHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case MSG_UPDATE_TIME:
                        invalidate();
                        if (shouldTimerBeRunning()) {
                            long timeMs = System.currentTimeMillis();
                            long delayMs = INTERACTIVE_UPDATE_RATE_MS
                                    - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                            mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
                        }
                        break;
                }
            }
        };

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            mMessageListener = new MessageApi.MessageListener() {
                @Override
                public void onMessageReceived(MessageEvent messageEvent) {
                    Log.d("[GoogleApiClient]", "You have a message from " + messageEvent.getPath());
                    DataMap batteryInfo  = DataMap.fromByteArray(messageEvent.getData());
                    mPhoneBatteryLevel = batteryInfo.getFloat("batteryLevel");
                    invalidate();
                }
            };

            mNodeListener = new NodeApi.NodeListener() {
                @Override
                public void onPeerConnected(Node node) {
                    Log.d("[GoogleApiClient]", "A node is connected and its id: " + node.getId());
                    if (!node.getId().equals("cloud")) {
                        Log.d("[GoogleApiClient]", "Changing mNodeID to --> " + node.getId());
                        mNodeID = node.getId();
                        getPhoneBatteryLevel();
                    }
                }
                @Override
                public void onPeerDisconnected(Node node) {
                    Log.d("[GoogleApiClient]", "A node is disconnected and its id: " + node.getId());
                }
            };

            mClient = new GoogleApiClient.Builder(MaterialLightWatchFaceService.this).addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                @Override
                public void onConnected(Bundle bundle) {
                    Log.d("[GoogleApiClient]", "Connected");
                    Wearable.NodeApi.addListener(mClient, mNodeListener);
                    Wearable.MessageApi.addListener(mClient, mMessageListener);
                    getPhoneBatteryLevel();
                }

                @Override
                public void onConnectionSuspended(int i) {
                    Log.d("[GoogleApiClient]", "Suspended");
                }
            }).addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                @Override
                public void onConnectionFailed(ConnectionResult connectionResult) {
                    Log.d("[GoogleApiClient]", "Failed");
                }
            }).addApi(Wearable.API).build();

            Wearable.NodeApi.getConnectedNodes(mClient).setResultCallback(new ResultCallback<NodeApi.GetConnectedNodesResult>() {
                @Override
                public void onResult(NodeApi.GetConnectedNodesResult getConnectedNodesResult) {
                    if(getConnectedNodesResult.getNodes().size() > 0) {
                        mNodeID = getConnectedNodesResult.getNodes().get(0).getId();
                    }
                }
            });

            setWatchFaceStyle(new WatchFaceStyle.Builder(MaterialLightWatchFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setStatusBarGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL)
                    .setHotwordIndicatorGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL)
                    .setViewProtection(WatchFaceStyle.PROTECT_STATUS_BAR | WatchFaceStyle.PROTECT_HOTWORD_INDICATOR)
                    .build());

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setAntiAlias(true);

            mBlackPaint = new Paint();
            mBlackPaint.setColor(Color.BLACK);
            mBlackPaint.setAntiAlias(false);

            mHourPaint = new Paint();
            mHourPaint.setStrokeWidth(6f);
            mHourPaint.setAntiAlias(true);
            mHourPaint.setStrokeCap(Paint.Cap.ROUND);

            mMinutePaint = new Paint();
            mMinutePaint.setStrokeWidth(4f);
            mMinutePaint.setAntiAlias(true);
            mMinutePaint.setStrokeCap(Paint.Cap.ROUND);

            mSecondPaint = new Paint();
            mSecondPaint.setARGB(255, 231, 76, 60);
            mSecondPaint.setStrokeWidth(2f);
            mSecondPaint.setAntiAlias(true);
            mSecondPaint.setStrokeCap(Paint.Cap.ROUND);

            mCenterPaint = new Paint();
            mCenterPaint.setAntiAlias(true);

            mLBAPaint = new Paint();
            mLBAPaint.setARGB(255, 236, 240, 241);
            mLBAPaint.setStyle(Paint.Style.STROKE);
            mLBAPaint.setStrokeWidth(2f);

            mLBAMinutePaint = new Paint();
            mLBAMinutePaint.setARGB(255, 236, 240, 241);
            mLBAMinutePaint.setStyle(Paint.Style.STROKE);
            mLBAMinutePaint.setStrokeCap(Paint.Cap.ROUND);
            mLBAMinutePaint.setStrokeWidth(4f);

            mLBAHourPaint = new Paint();
            mLBAHourPaint.setARGB(255, 236, 240, 241);
            mLBAHourPaint.setStyle(Paint.Style.STROKE);
            mLBAHourPaint.setStrokeCap(Paint.Cap.ROUND);
            mLBAHourPaint.setStrokeWidth(6f);

            mBatteryPaint = new Paint();
            mBatteryPaint.setARGB(255, 39, 174, 96);
            mBatteryPaint.setAntiAlias(true);
            mBatteryPaint.setStyle(Paint.Style.STROKE);
            mBatteryPaint.setStrokeWidth(3f);
            mBatteryPaint.setStrokeCap(Paint.Cap.BUTT);

            mBatteryLowPaint = new Paint();
            mBatteryLowPaint.setARGB(255, 243, 156, 18);
            mBatteryLowPaint.setAntiAlias(true);
            mBatteryLowPaint.setStyle(Paint.Style.STROKE);
            mBatteryLowPaint.setStrokeWidth(3f);
            mBatteryLowPaint.setStrokeCap(Paint.Cap.BUTT);

            mBatteryVeryLowPaint = new Paint();
            mBatteryVeryLowPaint.setARGB(255, 192, 57, 43);
            mBatteryVeryLowPaint.setAntiAlias(true);
            mBatteryVeryLowPaint.setStyle(Paint.Style.STROKE);
            mBatteryVeryLowPaint.setStrokeWidth(3f);
            mBatteryVeryLowPaint.setStrokeCap(Paint.Cap.BUTT);

            mTextPaint = new Paint();
            mTextPaint.setAntiAlias(true);
            mTextPaint.setTextAlign(Paint.Align.CENTER);
            mTextPaint.setTextSize(20f);

            mIconPaint = new Paint();
            mIconPaint.setAntiAlias(true);

            mBatteryBackgroundPaint = new Paint();
            mBatteryBackgroundPaint.setAntiAlias(true);
            mBatteryBackgroundPaint.setStyle(Paint.Style.STROKE);
            mBatteryBackgroundPaint.setStrokeWidth(3f);
            mBatteryBackgroundPaint.setStrokeCap(Paint.Cap.BUTT);


            mBackgroundPaint.setARGB(255, 236, 240, 241);
            mHourPaint.setARGB(255, 94, 101, 102);
            mMinutePaint.setARGB(255, 94, 101, 102);
            mCenterPaint.setARGB(255, 94, 101, 102);
            mTextPaint.setARGB(255, 127, 140, 141);
            mIconPaint.setColorFilter(new PorterDuffColorFilter(getResources().getColor(R.color.icon_tint_dark), PorterDuff.Mode.MULTIPLY));
            mBatteryBackgroundPaint.setARGB(70, 0, 0, 0);

            mDateTextPath = new Path();

            Resources resources = MaterialLightWatchFaceService.this.getResources();
            Drawable phoneDrawable = resources.getDrawable(R.drawable.ic_smartphone, null);
            mPhoneBitmap = ((BitmapDrawable) phoneDrawable).getBitmap();
            Drawable watchDrawable = resources.getDrawable(R.drawable.ic_watch, null);
            mWatchBitmap = ((BitmapDrawable) watchDrawable).getBitmap();

            mCalendar = Calendar.getInstance();

            getWatchBatteryLevel();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            mBurnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);

        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();

            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);

            if (!inAmbientMode) {
                if (!mClient.isConnected()) {
                    Log.d("[onAmbientModeChanged]", "API Client found disconnected, reestablishing connection...");
                    mClient.connect();
                }
                getWatchBatteryLevel();
                getPhoneBatteryLevel();
            }

            invalidate();
            updateTimer();
        }

        @Override
        public void onInterruptionFilterChanged(int interruptionFilter) {
            super.onInterruptionFilterChanged(interruptionFilter);
            boolean inMuteMode = (interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE);
            if (mMute != inMuteMode) {
                mMute = inMuteMode;
                mHourPaint.setAlpha(inMuteMode ? 100 : 255);
                mMinutePaint.setAlpha(inMuteMode ? 100 : 255);
                mCenterPaint.setAlpha(inMuteMode ? 100 : 255);
                invalidate();
            }
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {

            final float TWO_PI = (float) Math.PI * 2f;
            mCalendar.setTimeInMillis(System.currentTimeMillis());

            int width = bounds.width();
            int height = bounds.height();

            float centerX = width / 2f;
            float centerY = height / 2f;

            float watchCenterX = centerX/2f;
            float watchCenterY = centerY;

            float phoneCenterX = centerX + centerX/2f;
            float phoneCenterY = centerY;

            float dateCenterX = centerX;
            float dateCenterY = centerY + centerY/2f;

            float seconds = mCalendar.get(Calendar.SECOND) + mCalendar.get(Calendar.MILLISECOND) / 1000f;
            float minutes = mCalendar.get(Calendar.MINUTE) + seconds / 60f;
            float hours = mCalendar.get(Calendar.HOUR) + minutes / 60f;

            float secRot = seconds / 60f * TWO_PI;
            float minRot = minutes / 60f * TWO_PI;
            float hrRot = hours / 12f * TWO_PI;

            float secLength = centerX - 10;
            float minLength = centerX - 10;
            float hrLength = centerX - 60;

            if (isInAmbientMode()) {
                // Draw black background to save battery in Ambient Mode
                canvas.drawRect(0f, 0f, width, height, mBlackPaint);

                // Draw Minute hand
                float minX = (float) Math.sin(minRot) * minLength;
                float minY = (float) -Math.cos(minRot) * minLength;
                canvas.drawLine(centerX, centerY, centerX + minX, centerY + minY, mLBAMinutePaint);

                // Draw Hour hand
                float hrX = (float) Math.sin(hrRot) * hrLength;
                float hrY = (float) -Math.cos(hrRot) * hrLength;
                canvas.drawLine(centerX, centerY, centerX + hrX, centerY + hrY, mLBAHourPaint);

                canvas.drawCircle(centerX, centerY, 10f, mBlackPaint);
                canvas.drawArc(centerX - 10f, centerY - 10f, centerX + 10f, centerY + 10f, 0f, 360f, false, mLBAPaint);

            } else {
                // Draw color background
                canvas.drawRect(0f, 0f, width, height, mBackgroundPaint);

                // Draw round battery bars
                RectF watchBatteryRect = new RectF(watchCenterX-30f, watchCenterY-30f, watchCenterX+30f, watchCenterY+30f);
                RectF phoneBatteryRect = new RectF(phoneCenterX-30f, phoneCenterY-30f, phoneCenterX+30f, phoneCenterY+30f);
                canvas.drawCircle(watchCenterX, watchCenterY, 30f, mBatteryBackgroundPaint);
                canvas.drawCircle(phoneCenterX, phoneCenterY, 30f, mBatteryBackgroundPaint);

                if (mWatchBatteryLevel >= 0f && mWatchBatteryLevel <= 15f) {
                    canvas.drawArc(watchBatteryRect, 270f, mWatchBatteryLevel / 100f * 360f, false, mBatteryVeryLowPaint);
                } else if (mWatchBatteryLevel > 15f && mWatchBatteryLevel <= 30f) {
                    canvas.drawArc(watchBatteryRect, 270f, mWatchBatteryLevel / 100f * 360f, false, mBatteryLowPaint);
                } else {
                    canvas.drawArc(watchBatteryRect, 270f, mWatchBatteryLevel / 100f * 360f, false, mBatteryPaint);
                }

                if (mPhoneBatteryLevel >= 0f && mPhoneBatteryLevel <= 15f) {
                    canvas.drawArc(phoneBatteryRect, 270f, mPhoneBatteryLevel / 100f * 360f, false, mBatteryVeryLowPaint);
                } else if (mPhoneBatteryLevel > 15f && mPhoneBatteryLevel <= 30f) {
                    canvas.drawArc(phoneBatteryRect, 270f, mPhoneBatteryLevel / 100f * 360f, false, mBatteryLowPaint);
                } else {
                    canvas.drawArc(phoneBatteryRect, 270f, mPhoneBatteryLevel / 100f * 360f, false, mBatteryPaint);
                }

                // Draw watch and phone icons
                if (mWatchScaledBitmap == null
                        || mWatchScaledBitmap.getWidth() != 40f
                        || mWatchScaledBitmap.getHeight() != 40f) {
                    mWatchScaledBitmap = Bitmap.createScaledBitmap(mWatchBitmap,
                            40, 40, true);
                }
                if (mPhoneScaledBitmap == null
                        || mPhoneScaledBitmap.getWidth() != 40f
                        || mPhoneScaledBitmap.getHeight() != 40f) {
                    mPhoneScaledBitmap = Bitmap.createScaledBitmap(mPhoneBitmap,
                            40, 40, true);
                }
                canvas.drawBitmap(mWatchScaledBitmap, watchCenterX-BITMAP_DIMEN, watchCenterY-BITMAP_DIMEN, mIconPaint);
                canvas.drawBitmap(mPhoneScaledBitmap, phoneCenterX-BITMAP_DIMEN, phoneCenterY-BITMAP_DIMEN, mIconPaint);

                // Draw Date
                String month = getMonth(mCalendar.get(Calendar.MONTH));
                int day = mCalendar.get(Calendar.DAY_OF_MONTH);
                String date = String.valueOf(day);
                int daysInMonth = mCalendar.getActualMaximum(Calendar.DAY_OF_MONTH);
                mDateTextPath.moveTo(dateCenterX-25f, dateCenterY);
                mDateTextPath.lineTo(dateCenterX+25f, dateCenterY);
                canvas.drawTextOnPath(month, mDateTextPath, 0, -2f, mTextPaint);
                canvas.drawTextOnPath(date, mDateTextPath, 0, 18f, mTextPaint);

                RectF dateRect = new RectF(dateCenterX-30f, dateCenterY-30f, dateCenterX+30f, dateCenterY+30f);
                canvas.drawCircle(dateCenterX, dateCenterY, 30f, mBatteryBackgroundPaint);
                canvas.drawArc(dateRect, 270f, (float)day/(float)daysInMonth * 360f, false, mBatteryPaint);

                // Draw Minute hand
                float minX = (float) Math.sin(minRot) * minLength;
                float minY = (float) -Math.cos(minRot) * minLength;
                canvas.drawLine(centerX, centerY, centerX + minX, centerY + minY, mMinutePaint);

                // Draw Hour hand
                float hrX = (float) Math.sin(hrRot) * hrLength;
                float hrY = (float) -Math.cos(hrRot) * hrLength;
                canvas.drawLine(centerX, centerY, centerX + hrX, centerY + hrY, mHourPaint);

                // Draw Second hand
                float secX = (float) Math.sin(secRot) * secLength;
                float secY = (float) -Math.cos(secRot) * secLength;
                canvas.drawLine(centerX, centerY, centerX + secX, centerY + secY, mSecondPaint);

                // Draw center Dot to cover ugly hands in center
                canvas.drawCircle(centerX, centerY, 10f, mCenterPaint);
            }
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);
            Log.d("[onVisibilityChanged]", "visible=" + visible);
            if (visible) {
                mClient.connect();
                registerTimeZoneReceiver();
                mCalendar.setTimeZone(TimeZone.getDefault());
                getPhoneBatteryLevel();
                getWatchBatteryLevel();
            } else {
                if (mClient != null && mClient.isConnected()) {
                    Wearable.NodeApi.removeListener(mClient, mNodeListener);
                    Wearable.MessageApi.removeListener(mClient, mMessageListener);
                    mClient.disconnect();
                }
                unregisterTimeZoneReceiver();
            }
            updateTimer();
        }

        private void registerTimeZoneReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            MaterialLightWatchFaceService.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterTimeZoneReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            MaterialLightWatchFaceService.this.unregisterReceiver(mTimeZoneReceiver);
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

        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        private void getWatchBatteryLevel() {
            IntentFilter intentFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = registerReceiver(null, intentFilter);
            mWatchBatteryLevel = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        }

        private void getPhoneBatteryLevel() {
            if (firstCheck) {
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        Wearable.MessageApi.sendMessage(mClient, mNodeID, "/Battery", null);
                    }
                }, 1000);
                firstCheck = false;
            } else {
                Wearable.MessageApi.sendMessage(mClient, mNodeID, "/Battery", null);
            }
        }

        private String getMonth(int m) {
            switch (m) {
                case 0:
                    return "JAN";
                case 1:
                    return "FEB";
                case 2:
                    return "MAR";
                case 3:
                    return "APR";
                case 4:
                    return "MAY";
                case 5:
                    return "JUN";
                case 6:
                    return "JUL";
                case 7:
                    return "AUG";
                case 8:
                    return "SEP";
                case 9:
                    return "OCT";
                case 10:
                    return "NOV";
                case 11:
                    return "DEC";
                default:
                    return null;
            }
        }
    }
}

