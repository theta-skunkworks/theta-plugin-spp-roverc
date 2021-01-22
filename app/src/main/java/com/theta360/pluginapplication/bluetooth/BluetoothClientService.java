package com.theta360.pluginapplication.bluetooth;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothClass.Device;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;
import com.theta360.pluginlibrary.values.LedColor;
import com.theta360.pluginlibrary.values.LedTarget;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

public class BluetoothClientService extends Service {
    private static final String TAG = "BluetoothClientService";

    //SerialPort Service UUID
    private static final UUID BT_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothDeviceReceiver mBluetoothDeviceReceiver;
    private Context mContext;
    private static BluetoothSocket mBluetoothSocket = null;
    private BluetoothDeviceReceiver.Callback mBluetoothDeviceReceiverCallback = new BluetoothDeviceReceiver.Callback() {

        @Override
        public void onDiscoveryStarted() {
            //LED3点灯
            Intent intentLedShow = new Intent("com.theta360.plugin.ACTION_LED_SHOW");
            intentLedShow.putExtra("color", LedColor.YELLOW.toString());
            intentLedShow.putExtra("target", LedTarget.LED3.toString());
            mContext.sendBroadcast(intentLedShow);
        }

        @Override
        public void onDiscoveryFinished() {
            //LED3点灯
            Intent intentLedShow = new Intent("com.theta360.plugin.ACTION_LED_SHOW");
            intentLedShow.putExtra("color", LedColor.BLUE.toString());
            intentLedShow.putExtra("target", LedTarget.LED3.toString());
            mContext.sendBroadcast(intentLedShow);
        }

        @Override
        public void onFound(BluetoothDevice bluetoothDevice,
                BluetoothClass bluetoothClass,
                int rssi) {
            String name = bluetoothDevice.getName();
            Log.d(TAG, "name" + name);
            if (name != null) {
                int type = bluetoothDevice.getType();
                if (type == bluetoothDevice.DEVICE_TYPE_CLASSIC) {
                    int classNo = bluetoothClass.getDeviceClass();
                    Log.d(TAG, "class" + classNo);
                    if (classNo
                            == Device.COMPUTER_HANDHELD_PC_PDA) {
                        stopClassicScan();
                        bluetoothDevice.createBond();
                    }
                }
            }
        }

        @Override
        public void onAclConnected(BluetoothDevice bluetoothDevice) {
            Log.d(TAG, "onAclConnected");
            //LED3点灯
            Intent intentLedShow = new Intent("com.theta360.plugin.ACTION_LED_SHOW");
            intentLedShow.putExtra("color", LedColor.WHITE.toString());
            intentLedShow.putExtra("target", LedTarget.LED3.toString());
            mContext.sendBroadcast(intentLedShow);

            Intent intentSound = new Intent("com.theta360.plugin.ACTION_AUDIO_MOVSTART");
            mContext.sendBroadcast(intentSound);
        }

        @Override
        public void onAclDisconnected(BluetoothDevice bluetoothDevice) {
            Log.d(TAG, "onAclDisconnected");
            //LED3点灯
            Intent intentLedShow = new Intent("com.theta360.plugin.ACTION_LED_SHOW");
            intentLedShow.putExtra("color", LedColor.BLUE.toString());
            intentLedShow.putExtra("target", LedTarget.LED3.toString());
            mContext.sendBroadcast(intentLedShow);
        }

        public void onBondStateChanged(BluetoothDevice bluetoothDevice, int bondState) {
            Log.d(TAG, "onBondStateChanged");
            if (bondState == BluetoothDevice.BOND_BONDED) {
                connect(bluetoothDevice);
            }
        }

        public void onConnectionStateChanged(int connectionState) {
            Log.d(TAG, "onConnectionStateChanged");
            //LED3点灯
            Intent intentLedShow = new Intent("com.theta360.plugin.ACTION_LED_SHOW");
            if (connectionState == BluetoothAdapter.STATE_DISCONNECTED) {
                intentLedShow.putExtra("color", LedColor.BLUE.toString());
            } else if (connectionState == BluetoothAdapter.STATE_CONNECTED) {
                intentLedShow.putExtra("color", LedColor.WHITE.toString());

                Intent intentSound = new Intent("com.theta360.plugin.ACTION_AUDIO_MOVSTART");
                mContext.sendBroadcast(intentSound);
            }
            intentLedShow.putExtra("target", LedTarget.LED3.toString());
            mContext.sendBroadcast(intentLedShow);

        }

    };

    private Messenger _messenger;
    static class SppHandler extends Handler {

        private Context _cont;

        public SppHandler(Context cont) {
            _cont = cont;
        }

        @Override
        public void handleMessage(Message msg) {

            switch(msg.what) {
                case 0:
                    String sendCmd = (String)msg.obj;
                    Log.d(TAG, "Received message :" + sendCmd);
                    sendSppCommand(sendCmd);
                    break;
                default:
                    Log.d(TAG, "Undefined message :" + msg);
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        _messenger = new Messenger(new SppHandler(getApplicationContext()));
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Binded");

        return _messenger.getBinder();
        //return null; //If you do not use the message, return null.
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mContext = getApplicationContext();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothDevice.ACTION_FOUND);
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        intentFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        intentFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        intentFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        intentFilter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);

        mBluetoothDeviceReceiver = new BluetoothDeviceReceiver(
                mBluetoothDeviceReceiverCallback);
        mContext.registerReceiver(mBluetoothDeviceReceiver, intentFilter);

        BluetoothManager bluetoothManager = mContext.getSystemService(BluetoothManager.class);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        scanStart();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        mContext.unregisterReceiver(mBluetoothDeviceReceiver);
        stopClassicScan();
        disconnect();
        clearBondedDevice();
    }

    private void scanStart() {
        startClassicScan();
    }

    private void startClassicScan() {
        if (mBluetoothAdapter.isDiscovering()) {
            mBluetoothAdapter.cancelDiscovery();
        }
        boolean isStartDiscovery = mBluetoothAdapter.startDiscovery();
        Log.d(TAG, "startClassicScan :" + isStartDiscovery);
    }

    private void stopClassicScan() {

        if (mBluetoothAdapter.isDiscovering()) {
            mBluetoothAdapter.cancelDiscovery();
        }
        Log.d(TAG, "stopClassicScan");
    }

    private void connect(BluetoothDevice device) {
        int type = device.getType();
        if (type == BluetoothDevice.DEVICE_TYPE_CLASSIC) {
            int classNo = device.getBluetoothClass().getDeviceClass();
            Log.d(TAG, "class" + classNo);
            if (classNo == Device.COMPUTER_HANDHELD_PC_PDA) {
                try {
                    mBluetoothSocket = device.createRfcommSocketToServiceRecord(BT_UUID);
                    mBluetoothSocket.connect();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void disconnect() {
        if (mBluetoothSocket != null) {
            boolean isConnected = mBluetoothSocket.isConnected();
            if (isConnected) {
                try {
                    mBluetoothSocket.close();
                    mBluetoothSocket = null;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                String name = device.getName();
                if (name != null) {
                    Log.d(TAG, "Bonded device name:" + name);
                }
            }
        }
    }

    private void clearBondedDevice() {
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice pairedDevice : pairedDevices) {
                try {
                    Method m = pairedDevice.getClass()
                            .getMethod("removeBond", (Class[]) null);
                    m.invoke(pairedDevice, (Object[]) null);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static  void sendSppCommand(String inSendCommand) {

        if (mBluetoothSocket != null) {
            boolean isConnected = mBluetoothSocket.isConnected();
            if (isConnected) {
                try {
                    OutputStream out = mBluetoothSocket.getOutputStream();
                    InputStream in = mBluetoothSocket.getInputStream();

                    //Write command
                    Log.d(TAG, "SPP send data :" + inSendCommand);
                    out.write(inSendCommand.getBytes());

                    //Read the response
                    byte[] incomingBuff = new byte[64];
                    int incomingBytes = in.read(incomingBuff);
                    byte[] buff = new byte[incomingBytes];
                    System.arraycopy(incomingBuff, 0, buff, 0, incomingBytes);
                    String s = new String(buff, StandardCharsets.UTF_8);
                    Log.d(TAG, "SPP receive data :" + s);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                Log.d(TAG, "Not connected to a Bluetooth SPP device.");
            }
        } else {
            Log.d(TAG, "Not ready for Bluetooth SPP communication.");
        }
    }
}
