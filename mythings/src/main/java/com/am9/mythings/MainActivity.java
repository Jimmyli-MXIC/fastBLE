package com.am9.mythings;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.List;

import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.am9.commlib.MISCALEConnectUtil;
import com.am9.commlib.OpenBlueTask;
import com.clj.fastble.BleManager;
import com.clj.fastble.callback.BleGattCallback;
import com.clj.fastble.callback.BleNotifyCallback;
import com.clj.fastble.callback.BleScanCallback;
import com.clj.fastble.data.BleDevice;
import com.clj.fastble.exception.BleException;
import com.clj.fastble.utils.HexUtil;
import com.google.android.things.contrib.driver.button.Button;

public class MainActivity extends Activity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int REQUEST_CODE_OPEN_GPS = 1;
    private static final int REQUEST_CODE_PERMISSION_LOCATION = 2;

    private static final String gpioButtonPinName = "BUS NAME";
    private Button mButton;
    TextView txt;

    private ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //setupButton();
        setContentView(R.layout.layout2);

        BleManager.getInstance().init(getApplication());
        BleManager.getInstance().enableBluetooth();

        BleManager.getInstance()
                .enableLog(true)
                .setReConnectCount(1, 5000)
                .setConnectOverTime(20000)
                .setOperateTimeout(5000);

        txt=findViewById(R.id.mtext);
        progressDialog = new ProgressDialog(this);

    }
    @Override
    protected void onPostResume() {
        super.onPostResume();


        Log.d(TAG, "onPostResume");
        Runnable runa = new Runnable() {
            public void run() {
                Log.d(TAG, "RUN=======");
                MISCALEConnectUtil.setScanRule("MI_SCALE", false);
                startScan();
            }
        };
        addText(txt,"OpenBlueTask");

        OpenBlueTask dTask = new OpenBlueTask(MainActivity.this, runa);
        dTask.execute(20);

    }


    void regNotify(boolean isStop, final BleDevice bleDevice, final BluetoothGattCharacteristic characteristic) {
        if (!isStop) {
            BleManager.getInstance().notify(
                    bleDevice,
                    characteristic.getService().getUuid().toString(),
                    characteristic.getUuid().toString(),
                    new BleNotifyCallback() {
                        @Override
                        public void onNotifySuccess() {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    addText(txt, "notify success, STAND ON the MI_SCALE");
                                    progressDialog.dismiss();
                                }
                            });
                        }

                        @Override
                        public void onNotifyFailure(final BleException exception) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    addText(txt, exception.toString());
                                }
                            });
                        }

                        @Override
                        public void onCharacteristicChanged(byte[] data) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    addText(txt, HexUtil.formatHexString(characteristic.getValue(), true));
                                }
                            });
                        }
                    });
        } else {
            BleManager.getInstance().stopNotify(
                    bleDevice,
                    characteristic.getService().getUuid().toString(),
                    characteristic.getUuid().toString());
            progressDialog.dismiss();

        }

    }

    private void connect(final BleDevice bleDevice) {
        BleManager.getInstance().connect(bleDevice, new BleGattCallback() {
            @Override
            public void onStartConnect() {
                addText(txt, "connect.onStartConnect");
            }

            @Override
            public void onConnectFail(BleDevice bleDevice, BleException exception) {
                addText(txt, "connect.onConnectFail");
                progressDialog.dismiss();
                Toast.makeText(MainActivity.this, getString(R.string.connect_fail), Toast.LENGTH_LONG).show();
            }

            @Override
            public void onConnectSuccess(BleDevice bleDevice, BluetoothGatt gatt, int status) {
                addText(txt, "connect.onConnectSuccess");
                progressDialog.dismiss();
                BluetoothGattCharacteristic characteristic = MISCALEConnectUtil.getCharacteristic(gatt);
                regNotify(false, bleDevice, characteristic);
            }

            @Override
            public void onDisConnected(boolean isActiveDisConnected, BleDevice bleDevice, BluetoothGatt gatt, int status) {
                progressDialog.dismiss();
                addText(txt, "connect.onDisConnected:isActiveDisConnected:"+isActiveDisConnected);
                if (isActiveDisConnected) {
                    Toast.makeText(MainActivity.this, getString(R.string.active_disconnected), Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(MainActivity.this, getString(R.string.disconnected), Toast.LENGTH_LONG).show();
                }

            }
        });
    }

    void addText(TextView txt, String text) {
        txt.append(text + "\n");

        progressDialog.setMessage(text);
        progressDialog.show();
        Log.d(TAG, text);


    }
    private void startScan() {
        BleManager.getInstance().scan(new BleScanCallback() {
            @Override
            public void onScanStarted(boolean success) {
                addText(txt, "scan.onScanStarted");
            }

            @Override
            public void onScanning(BleDevice bleDevice) {
                addText(txt, "scan.onScanning;name:" + bleDevice.getName() + "\n" + bleDevice.getMac());
                BleManager.getInstance().cancelScan();
                progressDialog.setMessage("scan.onScanning:" + bleDevice.getName());
                connect(bleDevice);
            }

            @Override
            public void onScanFinished(List<BleDevice> scanResultList) {

                addText(txt, "scan.onScanFinished:" + scanResultList.size());
                progressDialog.setMessage("scan.onScanFinished");

            }
        });
    }
}