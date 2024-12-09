package com.keenresearch.keenasr_android_poc;

import java.util.List;

import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Build;

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class BluetoothManager extends BroadcastReceiver {
    protected static final String TAG =MainActivity.class.getSimpleName();

    private static BluetoothManager instance;

    private Context mContext;
    private AudioManager mAudioManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothHeadset mBluetoothHeadset;
    private BluetoothDevice mBluetoothDevice;
    private BluetoothProfile.ServiceListener mProfileListener;
    private boolean isBluetoothConnected;
    private boolean isScoConnected;

    public static BluetoothManager getInstance(Context c) {
        if (instance == null) {
            instance = new BluetoothManager(c);
        }
        return instance;
    }

    public BluetoothManager() {
        isBluetoothConnected = false;
        if (!ensureInit()) {
            Log.w(TAG, "tried to init but XXX not ready yet...");
        }
        instance = this;
    }

    public BluetoothManager(Context c) {
        Log.i(TAG, "Creating BluetoothManager instance");
        isBluetoothConnected = false;
        if (!ensureInit()) {
            Log.w(TAG, "tried to init but XXX not ready yet...");
        }
        mContext = c;
        instance = this;
    }

    public void initBluetooth() {
        if (!ensureInit()) {
            Log.w(TAG, "tried to init bluetooth but XXX not ready yet...");
            return;
        }
        Log.i(TAG, "Initializing Bluetooth");
        IntentFilter filter = new IntentFilter();
//        filter.addCategory(BluetoothHeadset.VENDOR_SPECIFIC_HEADSET_EVENT_COMPANY_ID_CATEGORY + "." + BluetoothAssignedNumbers.PLANTRONICS);
        filter.addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED);
        filter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothHeadset.ACTION_VENDOR_SPECIFIC_HEADSET_EVENT);
        mContext.registerReceiver(this,  filter);
        Log.d(TAG, "Receiver started");

        startBluetooth();
    }

    private void startBluetooth() {
        Log.i(TAG, "Starting Bluetooth...");
        if (isBluetoothConnected) {
            Log.e(TAG, "Already started, skipping...");
            return;
        }

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled()) {
            if (mProfileListener != null) {
                Log.w(TAG , "profile was already opened, let's close it");
                mBluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, mBluetoothHeadset);
            }

            mProfileListener = new BluetoothProfile.ServiceListener() {
                public void onServiceConnected(int profile, BluetoothProfile proxy) {
                    if (profile == BluetoothProfile.HEADSET) {
                        Log.d(TAG, "Headset connected");
                        mBluetoothHeadset = (BluetoothHeadset) proxy;
                        isBluetoothConnected = true;
                        Log.i(TAG, "Routing audio to bluetooth");
                        routeAudioToBluetooth();
                        // TODO stop listening (if active), restart audio stack
                    }
                }
                public void onServiceDisconnected(int profile) {
                    if (profile == BluetoothProfile.HEADSET) {
                        mBluetoothHeadset = null;
                        isBluetoothConnected = false;
                        Log.d(TAG, "Headset disconnected");
                        // TODO stop listening (if active), restart audio stack
                    }
                }
            };
            boolean success = mBluetoothAdapter.getProfileProxy(mContext, mProfileListener, BluetoothProfile.HEADSET);
            if (!success) {
                Log.e(TAG, "getProfileProxy failed !");
            }
        } else {
            Log.w(TAG, "Interface disabled on device");
        }
    }

    private boolean ensureInit() {
        if (mBluetoothAdapter == null) {
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        }
        if (mContext == null) {
            return false;
        }
        if (mContext != null && mAudioManager == null) {
            mAudioManager = ((AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE));
            Log.i(TAG, "got AudioManager");
        }
        return true;
    }

    public boolean routeAudioToBluetooth() {
        ensureInit();

        if (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled() && mAudioManager != null && mAudioManager.isBluetoothScoAvailableOffCall()) {
            if (isBluetoothHeadsetAvailable()) {
                if (mAudioManager != null && !mAudioManager.isBluetoothScoOn()) {
                    Log.d(TAG, "SCO off, let's start it");
                    mAudioManager.setBluetoothScoOn(true);
                    mAudioManager.startBluetoothSco();
                }
            } else {
                return false;
            }

            // Hack to ensure bluetooth sco is really running
            boolean ok = isUsingBluetoothAudioRoute();
            int retries = 0;
            while (!ok && retries < 5) {
                retries++;

                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {}

                if (mAudioManager != null) {
                    mAudioManager.setBluetoothScoOn(true);
                    mAudioManager.startBluetoothSco();
                }

                ok = isUsingBluetoothAudioRoute();
            }
            if (ok) {
                if (retries > 0) {
                    Log.d(TAG, "Audio route ok after " + retries + " retries");
                } else {
                    Log.d(TAG, " Audio route ok");
                }
            } else {
                Log.d(TAG, "Audio route still not ok...");
            }

            return ok;
        }

        return false;
    }

    public boolean isUsingBluetoothAudioRoute() {
        return mBluetoothHeadset != null && mBluetoothHeadset.isAudioConnected(mBluetoothDevice) && isScoConnected;
    }

    public boolean isBluetoothHeadsetAvailable() {
        ensureInit();
        if (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled() && mAudioManager != null && mAudioManager.isBluetoothScoAvailableOffCall()) {
            boolean isHeadsetConnected = false;
            if (mBluetoothHeadset != null) {
                List<BluetoothDevice> devices = mBluetoothHeadset.getConnectedDevices();
                mBluetoothDevice = null;
                for (final BluetoothDevice dev : devices) {
                    if (mBluetoothHeadset.getConnectionState(dev) == BluetoothHeadset.STATE_CONNECTED) {
                        mBluetoothDevice = dev;
                        isHeadsetConnected = true;
                        break;
                    }
                }
                Log.d(TAG, isHeadsetConnected ? "Headset found, bluetooth audio route available" : "No headset found, bluetooth audio route unavailable");
            }
            return isHeadsetConnected;
        }

        return false;
    }

    public void disableBluetoothSCO() {
        if (mAudioManager != null && mAudioManager.isBluetoothScoOn()) {
            mAudioManager.stopBluetoothSco();
            mAudioManager.setBluetoothScoOn(false);

            // Hack to ensure bluetooth sco is really stopped
            int retries = 0;
            while (isScoConnected && retries < 10) {
                retries++;

                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {}

                mAudioManager.stopBluetoothSco();
                mAudioManager.setBluetoothScoOn(false);
            }
            Log.w(TAG, "SCO disconnected!");
        }
    }

    public void stopBluetooth() {
        Log.w(TAG,"Stopping...");
        isBluetoothConnected = false;

        disableBluetoothSCO();

        if (mBluetoothAdapter != null && mProfileListener != null && mBluetoothHeadset != null) {
            mBluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, mBluetoothHeadset);
            mProfileListener = null;
        }
        mBluetoothDevice = null;

        Log.w(TAG, " Stopped!");
    }

    public void destroy() {
        try {
            stopBluetooth();
            try {
                mContext.unregisterReceiver(this);
                Log.d(TAG, "Receiver stopped");
            } catch (Exception e) {}
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }

    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED.equals(action)) {
            int state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, 0);
            if (state == AudioManager.SCO_AUDIO_STATE_CONNECTED) {
                Log.d(TAG, "SCO state: connected");
                isScoConnected = true;
            } else if (state == AudioManager.SCO_AUDIO_STATE_DISCONNECTED) {
                Log.d(TAG,"SCO state: disconnected");
                isScoConnected = false;
            } else {
                Log.d(TAG, "SCO state: " + state);
            }
        }
        else if (BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED.equals(action)) {
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE, BluetoothAdapter.STATE_DISCONNECTED);
            if (state == 0) {
                Log.d(TAG, "State: disconnected");
                stopBluetooth();
            } else if (state == 2) {
                Log.d(TAG, "State: connected");
                startBluetooth();
            } else {
                Log.d(TAG, "State: " + state);
            }
        }
        else if (intent.getAction().equals(BluetoothHeadset.ACTION_VENDOR_SPECIFIC_HEADSET_EVENT)) {
            String command = intent.getExtras().getString(BluetoothHeadset.EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_CMD);

            Object[] args = (Object[]) intent.getExtras().get(BluetoothHeadset.EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_ARGS);
            String eventName = (String) args[0];

            if (eventName.equals("BUTTON") && args.length >= 3) {
                Integer buttonID = (Integer) args[1];
                Integer mode = (Integer) args[2];
                Log.d(TAG,"Event: " + command + " : " + eventName + ", id = " + buttonID + " (" + mode + ")");
            }
        }
    }
}