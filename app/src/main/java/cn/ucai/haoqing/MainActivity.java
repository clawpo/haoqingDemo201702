package cn.ucai.haoqing;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.constraint.ConstraintLayout;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import static cn.ucai.haoqing.GattAttributes.DEVICE_NAME_YUNMAI_WEIGHT;
import static cn.ucai.haoqing.GattAttributes.SCAN_PERIOD;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private BluetoothAdapter mBluetoothAdapter;
    private boolean mScanning,isConnected;
    private Handler mHandler;
    private static final int REQUEST_ENABLE_BT = 1;

    TextView mtvStatus,mtvData;
    ProgressBar dialog;
    ConstraintLayout layout;
    BluetoothDevice yunmaiDevice;
    private BluetoothLeService mBluetoothLeService;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
            new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
        mHandler = new Handler();
        checkBLESupported();
        setListener();

    }

    private void setListener() {
        layout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scanLeDevice(true);
            }
        });
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
    }

    private void initView() {
        mtvStatus = (TextView) findViewById(R.id.tv_status);
        mtvData = (TextView) findViewById(R.id.tv_data);
        dialog = (ProgressBar) findViewById(R.id.scan);
        layout = (ConstraintLayout) findViewById(R.id.layout);
    }

    private void checkBLESupported() {
        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!mBluetoothAdapter.isEnabled()) {
            if (!mBluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }

        scanLeDevice(true);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onPause() {
        super.onPause();
        scanLeDevice(false);
    }

    private void scanLeDevice(final boolean enable) {
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                    updateStatus();
                }
            }, SCAN_PERIOD);

            mScanning = true;
            mBluetoothAdapter.startLeScan(mLeScanCallback);
        } else {
            mScanning = false;
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }
        updateStatus();
    }

    private void updateStatus() {
        dialog.setVisibility(mScanning? View.VISIBLE:View.GONE);
        mtvStatus.setText(mScanning?R.string.connectioning:R.string.disconnected);
        if (!mScanning && !isConnected){
            mtvData.setText(R.string.rescan);
            layout.setEnabled(true);
        }else{
            mtvData.setText("");
            layout.setEnabled(false);
        }
    }


    // Device scan callback.
    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {

                @Override
                public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (device!=null) {
                                Log.e("scan", "mLeScanCallback,device:" + device.getName() + "," + device.getAddress());
                                if (device.getName()!=null && device.getName().equals(DEVICE_NAME_YUNMAI_WEIGHT)) {
                                    Log.e("scan", "scan yunmai,aotu connection...");
                                    yunmaiDevice = device;
                                    gotoDeviceControl();
                                }
                            }
                        }
                    });
                }
            };


    private void gotoDeviceControl() {
        Log.e("scan","gotoDeviceControl,deivce="+yunmaiDevice.getName()+","+yunmaiDevice.getAddress());
        if (yunmaiDevice == null) return;
        if (mScanning) {
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
            mScanning = false;
            updateStatus();
        }
        connectionYunMai();
    }

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(yunmaiDevice.getAddress());
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    private void connectionYunMai() {
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(yunmaiDevice.getAddress());
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            Log.e(TAG,"mGattUpdateReceiver,action="+action);
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                isConnected = true;
                updateConnectionState(R.string.connected);
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                isConnected = false;
                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
                clearUI();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                loadGattServices(mBluetoothLeService.getSupportedGattServices());
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                Log.e(TAG,"mGattUpdateReceiver,BluetoothLeService.ACTION_DATA_AVAILABLE");
                displayWeightData(intent.getStringExtra(BluetoothLeService.DEVICE_DATA));
            }
        }
    };

    private void displayWeightData(String stringExtra) {
        mtvData.setText(stringExtra);
    }

    private void loadGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {

            List<BluetoothGattCharacteristic> gattCharacteristics =
                    gattService.getCharacteristics();
            ArrayList<BluetoothGattCharacteristic> charas =
                    new ArrayList<BluetoothGattCharacteristic>();

            // Loops through available Characteristics.
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                charas.add(gattCharacteristic);
            }
            mGattCharacteristics.add(charas);
        }
        autodisplayData();
    }
    private void autodisplayData() {
        int groupPosition = 2;
        int childPosition = 0;
        Log.e(TAG,"autodisplayData,groupPosition="+groupPosition+",childPosition="+childPosition);
        if (mGattCharacteristics != null) {
            final BluetoothGattCharacteristic characteristic =
                    mGattCharacteristics.get(groupPosition).get(childPosition);
            Log.e(TAG,"characteristic="+characteristic);
            final int charaProp = characteristic.getProperties();
            Log.e(TAG,"charaProp="+charaProp);
            if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                // If there is an active notification on a characteristic, clear
                // it first so it doesn't update the data field on the user interface.
//                if (mNotifyCharacteristic != null) {
//                    mBluetoothLeService.setCharacteristicNotification(
//                            mNotifyCharacteristic, false);
//                    mNotifyCharacteristic = null;
//                }
                Log.e(TAG,"mBluetoothLeService.readCharacteristic(characteristic)..");
                mBluetoothLeService.readCharacteristic(characteristic);
            }
            if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
//                mNotifyCharacteristic = characteristic;
                Log.e(TAG,"mBluetoothLeService.setCharacteristicNotification(characteristic)..");
                mBluetoothLeService.setCharacteristicNotification(
                        characteristic, true);
            }
        }
    }

    private void clearUI() {
        mtvData.setText(R.string.no_data);
    }

    private void updateConnectionState(int connected) {
        mtvStatus.setText(connected);
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mServiceConnection!=null) {
            unbindService(mServiceConnection);
            mBluetoothLeService = null;
        }
        if (mGattUpdateReceiver!=null) {
            unregisterReceiver(mGattUpdateReceiver);
        }
    }

    private void connectionDevices(boolean connection){
        if (connection){
            mBluetoothLeService.connect(yunmaiDevice.getAddress());
        }else{
            mBluetoothLeService.disconnect();
        }
    }
}
