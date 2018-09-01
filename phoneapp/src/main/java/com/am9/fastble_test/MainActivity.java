package com.am9.fastble_test;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
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

import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    static String s_uuid = "0000181d-0000-1000-8000-00805f9b34fb", c_uuid = "00002a9d-0000-1000-8000-00805f9b34fb";
    String TAG = "BLE_TEST";
    TextView txt;
    private ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        BleManager.getInstance().init(getApplication());
        BleManager.getInstance()
                .enableLog(true)
                .setReConnectCount(1, 5000)
                .setConnectOverTime(20000)
                .setOperateTimeout(5000);
        txt = findViewById(R.id.mtext);
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

    void addText(TextView txt, String text) {
        txt.append(text + "\n");

        progressDialog.setMessage(text);
        progressDialog.show();
        Log.d(TAG, text);


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

    private void listServices(final BluetoothGatt bluetoothGatt) {
        //0000181d-0000-1000-8000-00805f9b34fb
        // bluetoothGatt.getService()
        //00002a9d-0000-1000-8000-00805f9b34fb
        List<BluetoothGattService> serviceList = bluetoothGatt.getServices();
        for (BluetoothGattService service : serviceList) {
            UUID uuid_service = service.getUuid();
            Log.d(TAG, "service:" + uuid_service);

            List<BluetoothGattCharacteristic> characteristicList = service.getCharacteristics();
            for (BluetoothGattCharacteristic characteristic : characteristicList) {
                UUID uuid_chara = characteristic.getUuid();

                Log.d(TAG, "characteristic:" + uuid_chara);
                List<BluetoothGattDescriptor> DescriptorList = characteristic.getDescriptors();
                for (BluetoothGattDescriptor descriptor : DescriptorList) {
                    UUID uuid_descriptor = descriptor.getUuid();
                    Log.d(TAG, "descriptor:" + uuid_descriptor);

                }
            }
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
