package com.theta360.pluginapplication.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.util.Log;

public class BluetoothDeviceReceiver extends BroadcastReceiver {
    private static final String TAG = "BluetoothDeviceReceiver";

    private Callback mCallback;

    public BluetoothDeviceReceiver(@NonNull Callback callback) {
        mCallback = callback;
    }

    @Override
    public void onReceive(Context context, Intent intent) {

        final String action = intent.getAction();
        Log.d(TAG, action);

        if (action.equals(BluetoothAdapter.ACTION_DISCOVERY_STARTED)) {
            mCallback.onDiscoveryStarted();
        } else if (action.equals(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)) {
            mCallback.onDiscoveryFinished();
        } else if (action.equals(BluetoothDevice.ACTION_FOUND)) {
            BluetoothDevice bluetoothDevice = intent
                    .getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            BluetoothClass bluetoothClass = intent
                    .getParcelableExtra(BluetoothDevice.EXTRA_CLASS);
            short rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE);
            mCallback.onFound(bluetoothDevice, bluetoothClass, rssi);
        } else if (action.equals(BluetoothDevice.ACTION_ACL_CONNECTED)) {
            BluetoothDevice device = intent
                    .getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            mCallback.onAclConnected(device);
        } else if (action.equals(BluetoothDevice.ACTION_ACL_DISCONNECTED)) {
            BluetoothDevice device = intent
                    .getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            mCallback.onAclDisconnected(device);
        } else if (action.equals(BluetoothDevice.ACTION_BOND_STATE_CHANGED)) {
            BluetoothDevice device = intent
                    .getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            int bondState = intent
                    .getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE);
            mCallback.onBondStateChanged(device, bondState);
        } else if (action.equals(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)) {
            int connectionState = intent
                    .getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE,
                            BluetoothAdapter.STATE_DISCONNECTED);
            Log.d(TAG, "connectionState:" + connectionState);
            mCallback.onConnectionStateChanged(connectionState);
        }
    }

    public interface Callback {
        void onDiscoveryStarted();

        void onDiscoveryFinished();

        void onFound(BluetoothDevice device, BluetoothClass bluetoothclass, int rssi);

        void onAclConnected(BluetoothDevice bluetoothDevice);

        void onAclDisconnected(BluetoothDevice bluetoothDevice);

        void onBondStateChanged(BluetoothDevice bluetoothDevice, int bondState);

        void onConnectionStateChanged(int connectionState);
    }

}




