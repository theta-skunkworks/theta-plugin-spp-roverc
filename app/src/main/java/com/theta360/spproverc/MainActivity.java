/**
 * Copyright 2018 Ricoh Company, Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.theta360.spproverc;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.os.Bundle;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import android.view.KeyEvent;

import com.theta360.pluginapplication.bluetooth.BluetoothClientService;
import com.theta360.pluginapplication.task.EnableBluetoothClassicTask;
import com.theta360.pluginlibrary.activity.PluginActivity;
import com.theta360.pluginlibrary.callback.KeyCallback;
import com.theta360.pluginlibrary.receiver.KeyReceiver;
import com.theta360.pluginapplication.task.TakePictureTask;
import com.theta360.pluginapplication.task.TakePictureTask.Callback;
import com.theta360.pluginapplication.task.GetLiveViewTask;
import com.theta360.pluginapplication.task.MjisTimeOutTask;
import com.theta360.pluginapplication.view.MJpegInputStream;
import com.theta360.pluginapplication.oled.Oled;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;
import android.graphics.RectF;


public class MainActivity extends PluginActivity implements ServiceConnection {
    private static final String TAG = "360Tracking";

    // load native library
    static {
        System.loadLibrary("rotation_equi");
    }

    // native functions
    public native String version();
    public native byte[] rotateEqui(boolean reverseOrder, double yaw, double pitch, double roll,
                                    int width, int height, byte[] src);
    public native byte[] rotateEqui2(boolean reverseOrder1, double yaw1, double pitch1, double roll1,
                                     boolean reverseOrder2, double yaw2, double pitch2, double roll2,
                                     int width, int height, byte[] src);


    //Button Resorce
    private boolean onKeyDownModeButton = false;
    private boolean onKeyLongPressWlan = false;
    private boolean onKeyLongPressFn = false;

    //Preview Resorce
    private int previewFormatNo;
    GetLiveViewTask mGetLiveViewTask;
    private byte[]		latestLvFrame;
    private byte[]		latestFrame_Result;

    //Preview Timeout Resorce
    private static final long FRAME_READ_TIMEOUT_MSEC  = 1000;
    MjisTimeOutTask mTimeOutTask;
    MJpegInputStream mjis;

    //WebServer Resorce
    private Context context;
    private WebServer webServer;

    //OLED Dislay Resorce
    Oled oledDisplay = null;
    private boolean mFinished;

    //Attitude
    private SensorManager sensorManager;
    private Attitude attitude;

    private TakePictureTask.Callback mTakePictureTaskCallback = new Callback() {
        @Override
        public void onTakePicture(String fileUrl) {
            startPreview(mGetLiveViewTaskCallback, previewFormatNo);
        }
    };

    private boolean mServiceEnable = false;
    private EnableBluetoothClassicTask.Callback mEnableBluetoothClassicTask = new EnableBluetoothClassicTask.Callback() {
        @Override
        public void onEnableBluetoothClassic(String result) {
            if( result.equals("OK") ) {
                mServiceEnable = true;

                getApplicationContext()
                        .startService(
                                new Intent(getApplicationContext(), BluetoothClientService.class));

                getApplicationContext()
                        .bindService(
                                new Intent(getApplicationContext(), BluetoothClientService.class),
                                MainActivity.this,
                                Context.BIND_AUTO_CREATE);
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d(TAG, "OpenCV version: " + version());

        new EnableBluetoothClassicTask(getApplicationContext(), mEnableBluetoothClassicTask).execute();

        // Set enable to close by pluginlibrary, If you set false, please call close() after finishing your end processing.
        setAutoClose(true);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        //init OLED
        oledDisplay = new Oled(getApplicationContext());
        oledDisplay.brightness(100);
        oledDisplay.clear(oledDisplay.black);
        oledDisplay.draw();

        //init Attitude
        sensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        attitude = new Attitude(sensorManager);

        // Set a callback when a button operation event is acquired.
        setKeyCallback(new KeyCallback() {
            @Override
            public void onKeyDown(int keyCode, KeyEvent event) {
                switch (keyCode) {
                    case KeyReceiver.KEYCODE_CAMERA :
                        stopPreview();
                        new TakePictureTask(mTakePictureTaskCallback).execute();
                        break;
                    case KeyReceiver.KEYCODE_MEDIA_RECORD :
                        // Disable onKeyUp of startup operation.
                        onKeyDownModeButton = true;
                        break;
                    default:
                        break;
                }
            }

            @Override
            public void onKeyUp(int keyCode, KeyEvent event) {

                switch (keyCode) {
                    case KeyReceiver.KEYCODE_WLAN_ON_OFF :
                        if (onKeyLongPressWlan) {
                            onKeyLongPressWlan=false;
                        } else {

                            //reset Object detection dir
                            lastDetectYaw = equiW/2; // Front
                            lastDetectPitch = equiH/2;

                        }

                        break;
                    case KeyReceiver.KEYCODE_MEDIA_RECORD :
                        if (onKeyDownModeButton) {
                            if (mGetLiveViewTask!=null) {
                                stopPreview();
                            } else {
                                startPreview(mGetLiveViewTaskCallback, previewFormatNo);
                            }
                            onKeyDownModeButton = false;
                        }
                        break;
                    case KeyEvent.KEYCODE_FUNCTION :
                        if (onKeyLongPressFn) {
                            onKeyLongPressFn=false;
                        } else {

                            //reset Object detection dir
                            lastDetectYaw = 0/*(equiW/2)*/; //Back
                            lastDetectPitch = equiH/2;

                        }

                        break;
                    default:
                        break;
                }

            }

            @Override
            public void onKeyLongPress(int keyCode, KeyEvent event) {
                switch (keyCode) {
                    case KeyReceiver.KEYCODE_WLAN_ON_OFF:
                        onKeyLongPressWlan=true;

                        //NOP : KEYCODE_WLAN_ON_OFF

                        break;
                    case KeyEvent.KEYCODE_FUNCTION :
                        onKeyLongPressFn=true;

                        //NOP : KEYCODE_FUNCTION

                        break;
                    default:
                        break;
                }

            }
        });

        this.context = getApplicationContext();
        this.webServer = new WebServer(this.context, mWebServerCallback);
        try {
            this.webServer.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (isApConnected()) {

        }

        //Start LivePreview
        previewFormatNo = GetLiveViewTask.FORMAT_NO_1024_8FPS;
        startPreview(mGetLiveViewTaskCallback, previewFormatNo);

        //Start OLED thread
        mFinished = false;
        imageProcessingThread();
    }

    @Override
    protected void onPause() {
        // Do end processing
        //close();

        //Stop Web server
        this.webServer.stop();

        //Stop LivePreview
        stopPreview();

        //Stop OLED thread
        mFinished = true;

        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (mServiceEnable) {
            getApplicationContext().unbindService(MainActivity.this );
            getApplicationContext().stopService(new Intent(getApplicationContext(), BluetoothClientService.class));
        }

        super.onDestroy();
        if (this.webServer != null) {
            this.webServer.stop();
        }
    }

    private Messenger _messenger;

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        Log.d(TAG, "サービスに接続しました");
        _messenger = new Messenger(service);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        Log.d(TAG, "サービスから切断しました");
        _messenger = null;
    }

    private void startPreview(GetLiveViewTask.Callback callback, int formatNo){
        if (mGetLiveViewTask!=null) {
            stopPreview();

            try {
                Thread.sleep(400);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        mGetLiveViewTask = new GetLiveViewTask(callback, formatNo);
        mGetLiveViewTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void stopPreview(){
        //At the intended stop, timeout monitoring also stops.
        if (mTimeOutTask!=null) {
            mTimeOutTask.cancel(false);
            mTimeOutTask=null;
        }

        if (mGetLiveViewTask!=null) {
            mGetLiveViewTask.cancel(false);
            mGetLiveViewTask = null;
        }
    }


    /**
     * GetLiveViewTask Callback.
     */
    private GetLiveViewTask.Callback mGetLiveViewTaskCallback = new GetLiveViewTask.Callback() {

        @Override
        public void onGetResorce(MJpegInputStream inMjis) {
            mjis = inMjis;
        }

        @Override
        public void onLivePreviewFrame(byte[] previewByteArray) {
            latestLvFrame = previewByteArray;

            //Update timeout monitor
            if (mTimeOutTask!=null) {
                mTimeOutTask.cancel(false);
                mTimeOutTask=null;
            }
            mTimeOutTask = new MjisTimeOutTask(mMjisTimeOutTaskCallback, FRAME_READ_TIMEOUT_MSEC);
            mTimeOutTask.execute();
        }

        @Override
        public void onCancelled(Boolean inTimeoutOccurred) {
            mGetLiveViewTask = null;
            latestLvFrame = null;

            if (inTimeoutOccurred) {
                startPreview(mGetLiveViewTaskCallback, previewFormatNo);
            }
        }

    };


    /**
     * MjisTimeOutTask Callback.
     */
    private MjisTimeOutTask.Callback mMjisTimeOutTaskCallback = new MjisTimeOutTask.Callback() {
        @Override
        public void onTimeoutExec(){
            if (mjis!=null) {
                try {
                    // Force an IOException to `mjis.readMJpegFrame()' in GetLiveViewTask()
                    mjis.close();
                } catch (IOException e) {
                    Log.d(TAG, "[timeout] mjis.close() IOException");
                    e.printStackTrace();
                }
                mjis=null;
            }
        }
    };

    /**
     * WebServer Callback.
     */
    private WebServer.Callback mWebServerCallback = new WebServer.Callback() {
        @Override
        public void execStartPreview(int format) {
            previewFormatNo = format;
            startPreview(mGetLiveViewTaskCallback, format);
        }

        @Override
        public void execStopPreview() {
            stopPreview();
        }

        @Override
        public boolean execGetPreviewStat() {
            if (mGetLiveViewTask==null) {
                return false;
            } else {
                return true;
            }
        }

        @Override
        public byte[] getLatestFrame() {
            //return latestLvFrame;
            return latestFrame_Result;
        }
    };

    //==============================================================
    // Image processing Thread
    //==============================================================
    private static final String TF_OD_API_MODEL_FILE = "ssd_mobilenet_v1_1_metadata_1.tflite";
    private static final String TF_OD_API_LABELS_FILE = "labelmap.txt";
    private static final int TF_OD_API_INPUT_SIZE = 300;
    private static final boolean TF_OD_API_IS_QUANTIZED = true;

    //Object Detection dir
    private int equiW = 0;
    private int equiH = 0;

    boolean detectFlag = false;
    private int lastDetectYaw=512;
    private int lastDetectPitch=256;
    private int lastDetectArea = 0;

    public void imageProcessingThread() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                int outFps=0;
                long startTime = System.currentTimeMillis();
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);

                ///////////////////////////////////////////////////////////////////////
                // TFLite Initial detector
                ///////////////////////////////////////////////////////////////////////
                Detector detector=null;
                try {
                    Log.d(TAG, "### TFLite Initial detector ###");
                    detector = TFLiteObjectDetectionAPIModel.create(
                            getApplicationContext(),
                            TF_OD_API_MODEL_FILE,
                            TF_OD_API_LABELS_FILE,
                            TF_OD_API_INPUT_SIZE,
                            TF_OD_API_IS_QUANTIZED);
                } catch (final IOException e) {
                    e.printStackTrace();
                    Log.d(TAG, "IOException:" + e);
                    mFinished = true;
                }

                //set detection area offset
                int offsetX=0;
                int offsetY=0;


                while (mFinished == false) {
                    detectFlag = false;

                    //set detection area offset
                    if ( (previewFormatNo==GetLiveViewTask.FORMAT_NO_640_8FPS) ||
                            (previewFormatNo==GetLiveViewTask.FORMAT_NO_640_30FPS) ) {
                        offsetX = 170;
                        offsetY = 10;
                        equiW = 640;
                    } else if ( (previewFormatNo==GetLiveViewTask.FORMAT_NO_1024_8FPS) ||
                            (previewFormatNo==GetLiveViewTask.FORMAT_NO_1024_30FPS) ) {
                        offsetX = 362;
                        offsetY = 106;
                        equiW = 1024;
                    } else if ( (previewFormatNo==GetLiveViewTask.FORMAT_NO_1920_8FPS) ) {
                        offsetX = 810;
                        offsetY = 330;
                        equiW = 1920;
                    } else {
                        offsetX = 170;
                        offsetY = 10;
                        equiW = 640;
                    }
                    equiH = equiW/2;


                    byte[] jpegFrame = latestLvFrame;
                    if ( jpegFrame != null ) {

                        //JPEG -> Bitmap
                        BitmapFactory.Options options = new  BitmapFactory.Options();
                        options.inMutable = true;
                        Bitmap bitmap = BitmapFactory.decodeByteArray(jpegFrame, 0, jpegFrame.length, options);

                        //rotation yaw
                        //bitmap = rotationYaw(lastDetectYaw, equiW, bitmap);

                        //-------------------------------------------------------------
                        //Rotation Equi
                        //-------------------------------------------------------------
                        double rotYaw = ( 360.0*(lastDetectYaw-(equiW/2))/equiW );
                        double rotPitch = -(180.0*( (equiW/2)-lastDetectPitch-(equiW/4) )/(equiW/2));

                        double corrAzimath = attitude.getDegAzimath();
                        double corrPitch = attitude.getDegPitch();
                        double corrRoll = attitude.getDegRoll();
                        Log.d(TAG, "corrAzimath = " + String.valueOf(corrAzimath) + ", corrPitch = " + String.valueOf(corrPitch) + ", corrRoll = " + String.valueOf(corrRoll) );

                        ByteBuffer byteBuffer = ByteBuffer.allocate(bitmap.getByteCount());
                        bitmap.copyPixelsToBuffer(byteBuffer);

                        //--- Orientation correction ---
                        //byte[] dst = rotateEqui(false, corrAzimath, corrPitch, corrRoll, bitmap.getWidth(), bitmap.getHeight(), byteBuffer.array());
                        //--- Zenith correction ---
                        //byte[] dst = rotateEqui(false, 0, corrPitch, corrRoll, bitmap.getWidth(), bitmap.getHeight(), byteBuffer.array());
                        //--- Local Tracking ---（ローカル座標系で追尾）
                        byte[] dst = rotateEqui(true, -rotYaw, -rotPitch, 0, bitmap.getWidth(), bitmap.getHeight(), byteBuffer.array());
                        //--- 姿勢補正&追尾 回転処理2回（遅い、ほぼ2fps）---
                        //byte[] dst1 = rotateEqui(false, corrAzimath, corrPitch, corrRoll, bitmap.getWidth(), bitmap.getHeight(), byteBuffer.array());
                        //byte[] dst = rotateEqui(true, -rotYaw, -rotPitch, 0, bitmap.getWidth(), bitmap.getHeight(), dst1);
                        //--- 姿勢補正&追尾 回転処理1回まとめ（ほぼ3fps）---
                        //byte[] dst = rotateEqui2(
                        //        false, corrAzimath, corrPitch, corrRoll,
                        //        true, -rotYaw, -rotPitch, 0,
                        //        bitmap.getWidth(), bitmap.getHeight(), byteBuffer.array() );

                        Bitmap dstBmp = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
                        dstBmp.copyPixelsFromBuffer(ByteBuffer.wrap(dst));
                        bitmap = dstBmp;


                        //crop detect area
                        Bitmap cropBitmap = Bitmap.createBitmap(bitmap, offsetX, offsetY, TF_OD_API_INPUT_SIZE, TF_OD_API_INPUT_SIZE, null, true);

                        //make result canvas
                        Canvas resultCanvas = new Canvas(bitmap);
                        Paint mPaint = new Paint();
                        mPaint.setStyle(Paint.Style.STROKE);
                        mPaint.setColor( Color.GREEN );
                        resultCanvas.drawRect(offsetX, offsetY, offsetX+TF_OD_API_INPUT_SIZE, offsetY+TF_OD_API_INPUT_SIZE, mPaint);

                        ///////////////////////////////////////////////////////////////////////
                        // TFLite Object detection
                        ///////////////////////////////////////////////////////////////////////
                        final List<Detector.Recognition> results = detector.recognizeImage(cropBitmap);
                        Log.d(TAG, "### TFLite Object detection [result] ###");
                        for (final Detector.Recognition result : results) {
                            drawDetectResult(result, resultCanvas, mPaint, offsetX, offsetY);
                        }

                        //set result image
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);
                        latestFrame_Result = baos.toByteArray();

                        outFps++;
                    } else {
                        try {
                            Thread.sleep(33);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                    //Dislpay Detect Dir to OLED
                    double lastDetectYawDig = (lastDetectYaw-equiW/2)*360.0/equiW;
                    double lastDetectPitchDig = (equiH/2-lastDetectPitch)*180/equiH;
                    displayResult(lastDetectYawDig, lastDetectPitchDig, detectFlag);

                    //Calc Control RoverC
                    //面積、ローカル座標系での仰角、方向
                    // 仰角が範囲内 = その方向への制御
                    //                 面積が閾値より小さい = その方向へ向かう 駆動時間どうしよね？
                    //                 面積が閾値より大きい = 反対方向へバック 駆動時間どうしようね？
                    // 仰角が範囲外 = 回転して正面を向くだけ
                    //                　回転方向と駆動時間で　どの姿勢になるかというだけ。ちょっとづつでもええよ。
                    String sppCmd = "";
                    if ( (detectFlag) && (lastDetectArea > 0) ) {
                        if ( lastDetectArea > (150*150) ) {
                            // 大きすぎるので離れる
                            int opposite = (int)(lastDetectYawDig + 180.5);
                            if (opposite>360){
                                opposite -= 360;
                            }
                            sppCmd = "dir " + String.valueOf( opposite ) +" 50";
                        } else {
                            //　近づける可能性あり
                            if ( (-30<=lastDetectPitchDig) && (lastDetectPitchDig<=30) ) {
                                //近づく
                                sppCmd = "dir " + String.valueOf( (int)lastDetectYawDig ) +" 50";
                            } else {
                                //回転して向きを整える
                                sppCmd = "rot " + String.valueOf( (int)lastDetectYawDig ) +" 50";
                            }
                        }
                    } else {
                        //みつからなかったので停止
                        //sppCmd = "dir 0 0";
                        sppCmd = "rot 0 0";
                    }

                    if (_messenger!=null) {
                        try {
                            _messenger.send(Message.obtain(null, 0, sppCmd));
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }

                    long curTime = System.currentTimeMillis();
                    long diffTime = curTime - startTime;
                    if (diffTime >= 1000 ) {
                        Log.d(TAG, "[OLED]" + String.valueOf(outFps) + "[fps]" );
                        startTime = curTime;
                        outFps =0;
                    }
                }
            }
        }).start();
    }


    private Bitmap rotationYaw(int inLastDetectYaw, int equiW, Bitmap inBitmap) {
        //Yaw axis rotation [Moving the detection frame]
        Log.d(TAG, "### Yaw axis rotation START [result] ###");

        //Yaw axis rotation [Image rotation]
        Bitmap rotationBmp = Bitmap.createBitmap(equiW, (equiW/2), Bitmap.Config.ARGB_8888);
        Canvas rotationCanvas = new Canvas(rotationBmp);
        if ( (equiW/2) < inLastDetectYaw ) {
            Log.d(TAG, "Case 1 [result]");

            int leftWidth = (equiW/2) + ( equiW - inLastDetectYaw ) ;

            Bitmap leftBmp = Bitmap.createBitmap(inBitmap, (inLastDetectYaw-(equiW/2)), 0, leftWidth, (equiW/2), null, true);
            Bitmap rightBmp = Bitmap.createBitmap(inBitmap, 0, 0, (inLastDetectYaw-(equiW/2)), (equiW/2), null, true);
            Paint mPaint = new Paint();
            rotationCanvas.drawBitmap(leftBmp, 0, 0, mPaint);
            rotationCanvas.drawBitmap(rightBmp, leftWidth, 0, mPaint);

        } else if ( inLastDetectYaw<(equiW/2) ) {
            Log.d(TAG, "Case 2 [result]");

            Bitmap leftBmp = Bitmap.createBitmap(inBitmap, (inLastDetectYaw+(equiW/2)), 0, ((equiW/2)-inLastDetectYaw), (equiW/2), null, true);
            Bitmap rightBmp = Bitmap.createBitmap(inBitmap, 0, 0, (inLastDetectYaw+(equiW/2)), (equiW/2), null, true);
            Paint mPaint = new Paint();
            rotationCanvas.drawBitmap(leftBmp, 0, 0, mPaint);
            rotationCanvas.drawBitmap(rightBmp, ((equiW/2)-inLastDetectYaw), 0, mPaint);

        } else {
            Log.d(TAG, "Case 3 [result]");

            Paint mPaint = new Paint();
            rotationCanvas.drawBitmap(inBitmap, 0, 0, mPaint);
        }
        Log.d(TAG, "### Yaw axis rotation END [result] ###");

        return rotationBmp;
    }


    private void drawDetectResult(Detector.Recognition inResult, Canvas inResultCanvas, Paint inPaint, int inOffsetX, int inOffsetY){
        double confidence = Double.valueOf(inResult.getConfidence());
        if ( confidence >= 0.54 ) {
            Log.d(TAG, "[result] Title:" + inResult.getTitle());
            Log.d(TAG, "[result] Confidence:" + inResult.getConfidence());
            Log.d(TAG, "[result] Location:" + inResult.getLocation());

            // draw result
            if (confidence >= 0.56) {
                String title = inResult.getTitle();
                if ( title.equals("apple")) {
                    inPaint.setColor( Color.RED );
                } else if ( title.equals("banana") ) {
                    inPaint.setColor( Color.YELLOW );

                    detectFlag = true;
                    updateDetectInfo(inResult, inOffsetX, inOffsetY);

                } else if ( title.equals("orange") ) {
                    inPaint.setColor(Color.CYAN );
                } else {
                    inPaint.setColor( Color.BLUE );
                }
            } else {
                inPaint.setColor( Color.DKGRAY );
            }
            RectF offsetRectF = new RectF(inResult.getLocation().left, inResult.getLocation().top, inResult.getLocation().right, inResult.getLocation().bottom);
            offsetRectF.offset( (float) inOffsetX, (float) inOffsetY );
            inResultCanvas.drawRect( offsetRectF, inPaint );
            inResultCanvas.drawText(inResult.getTitle() + " : " + inResult.getConfidence(), offsetRectF.left, offsetRectF.top, inPaint);
        }
    }

    private void updateDetectInfo(Detector.Recognition inResult, int inOffsetX, int inOffsetY){
        int tmp = lastDetectYaw;
        int curDetectYaw = (int)( inOffsetX + inResult.getLocation().left + ((inResult.getLocation().right-inResult.getLocation().left)/2) );
        if ( curDetectYaw <= (equiW/2) ) {
            lastDetectYaw -= ((equiW/2)-curDetectYaw);
        } else {
            lastDetectYaw += (curDetectYaw-(equiW/2));
        }
        if ( equiW < lastDetectYaw ) {
            lastDetectYaw -= equiW ;
        } else if (lastDetectYaw<0) {
            lastDetectYaw = equiW + lastDetectYaw;
        }
        Log.d(TAG, "[result] lastDetectYaw=" + String.valueOf(lastDetectYaw) + ", befor=" +String.valueOf(tmp) );


        int equiH = equiW/2;
        int curDitectPitch = (int)( inOffsetY + inResult.getLocation().top + ((inResult.getLocation().bottom-inResult.getLocation().top)/2) );
        if ( curDitectPitch <= (equiH/2) ) {
            lastDetectPitch -= (equiH/2) - curDitectPitch;
        } else {
            lastDetectPitch += (curDitectPitch - (equiH/2));
        }
        boolean adjustYaw = false;
        if ( equiH < lastDetectPitch ) {
            lastDetectPitch = equiH - (equiH-lastDetectPitch);
            adjustYaw = true;
        } else if ( lastDetectPitch < 0 ) {
            lastDetectPitch = -lastDetectPitch;
            adjustYaw = true;
        }
        if (adjustYaw) {
            lastDetectYaw += (equiW/2);
            if ( equiW < lastDetectYaw ) {
                lastDetectYaw -= equiW;
            }
        }

        //lastDetectArea = 150*150;
        lastDetectArea = (int) ( (inResult.getLocation().right - inResult.getLocation().left) * (inResult.getLocation().bottom - inResult.getLocation().top) );
        Log.d(TAG, "[result] lastDetectArea=" + String.valueOf(lastDetectArea) );

    }

    private void displayResult(double detectYawDig, double detectPitchDig, boolean inDetectFlag) {

        double lineLength = 10.0;
        double lineEndDig = detectYawDig-90.0;
        double lineEndX = lineLength * Math.cos( Math.toRadians( lineEndDig ) );
        double lineEndY = lineLength * Math.sin( Math.toRadians( lineEndDig ) );

        double arrowLength = 6.0;
        double arrowEndX1 = arrowLength * Math.cos( Math.toRadians( lineEndDig+210.0 ) );
        double arrowEndY1 = arrowLength * Math.sin( Math.toRadians( lineEndDig+210.0 ) );
        double arrowEndX2 = arrowLength * Math.cos( Math.toRadians( lineEndDig-210.0 ) );
        double arrowEndY2 = arrowLength * Math.sin( Math.toRadians( lineEndDig-210.0 ) );

        int centerX = 15;
        int centerY = 12;

        oledDisplay.clear();

        oledDisplay.circle(centerX, centerY, 11);
        oledDisplay.line(centerX, centerY, (int)(centerX+lineEndX+0.5), (int)(centerY+lineEndY+0.5));
        oledDisplay.line((int)(centerX+lineEndX+0.5), (int)(centerY+lineEndY+0.5), (int)(centerX+lineEndX+arrowEndX1+0.5), (int)(centerY+lineEndY+arrowEndY1+0.5) );
        oledDisplay.line((int)(centerX+lineEndX+0.5), (int)(centerY+lineEndY+0.5), (int)(centerX+lineEndX+arrowEndX2+0.5), (int)(centerY+lineEndY+arrowEndY2+0.5) );

        String line1Str = "";
        if (mGetLiveViewTask!=null) {
            if (inDetectFlag) {
                line1Str = "** Lock-On! **";
            } else {
                line1Str = "- can't find -";
            }
        } else {
            line1Str = "STOP Detection";
        }
        String line2Str = "Yaw   : " + String.valueOf( (int)detectYawDig );
        String line3Str = "Pitch : " + String.valueOf( (int)detectPitchDig );

        int textLine1 = 0;
        int textLine2 = 8;
        int textLine3 = 16;
        oledDisplay.setString(35, textLine1,line1Str);
        oledDisplay.setString(35, textLine2,line2Str);
        oledDisplay.setString(35, textLine3,line3Str);

        oledDisplay.draw();
    }

}
