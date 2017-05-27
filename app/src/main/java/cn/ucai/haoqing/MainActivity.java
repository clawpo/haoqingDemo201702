package cn.ucai.haoqing;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.constraint.ConstraintLayout;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import static cn.ucai.haoqing.GattAttributes.DEVICE_NAME_YUNMAI_WEIGHT;
import static cn.ucai.haoqing.GattAttributes.SCAN_PERIOD;

public class MainActivity extends AppCompatActivity {

    private BluetoothAdapter mBluetoothAdapter;
    private boolean mScanning,isConnected;
    private Handler mHandler;
    private static final int REQUEST_ENABLE_BT = 1;

    TextView mtvStatus,mtvData;
    ProgressBar dialog;
    ConstraintLayout layout;
    BluetoothDevice yunmaiDevice;

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

    private void connectionYunMai() {

    }
}
