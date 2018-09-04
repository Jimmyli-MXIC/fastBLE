package com.am9.mythings;

import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.os.Bundle;

import java.io.IOException;
import java.util.List;

import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.am9.commlib.MISCALEConnectUtil;
import com.am9.commlib.OpenBlueTask;
import com.am9.commlib.PeripheralHelper;
import com.clj.fastble.BleManager;
import com.clj.fastble.callback.BleGattCallback;
import com.clj.fastble.callback.BleNotifyCallback;
import com.clj.fastble.callback.BleScanCallback;
import com.clj.fastble.data.BleDevice;
import com.clj.fastble.exception.BleException;
import com.clj.fastble.utils.HexUtil;

public class MainActivity extends Activity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int REQUEST_CODE_OPEN_GPS = 1;
    private static final int REQUEST_CODE_PERMISSION_LOCATION = 2;
    TextView txt;

    private ProgressDialog progressDialog;
    private PeripheralHelper mPeripheral;
//    private Gpio mLedGpio;

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

        txt = findViewById(R.id.mtext);
        progressDialog = new ProgressDialog(this);

        //new GpioUtil,构造函数里初始化各IO，举例：mLedGpio

        Log.i(TAG, "Starting BlinkActivity");
        mPeripheral = new PeripheralHelper();
//        PeripheralManager pioService = PeripheralManager.getInstance();
//        try {
//            Log.i(TAG, "Configuring GPIO pins");
//            mLedGpio = pioService.openGpio(BoardDefaults.getGPIOForLED());
//            mLedGpio.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
//
//        } catch (IOException e) {
//            Log.e(TAG, "Error configuring GPIO pins", e);
//        }
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

    @Override
    protected void onStop() {

        Log.d(TAG, "onStop called.");
        if (mPeripheral.getLedGpio() != null) {
            try {
                Log.d(TAG, "Unregistering LED.");
                mPeripheral.getLedGpio().close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing LED GPIO", e);
            } finally {
                mPeripheral.setLedGpio(null);
            }
        }
        super.onStop();
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
                                    addText(txt, ""+convertToWeight(characteristic.getValue()));

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
//                setLEDValue(false);

                progressDialog.dismiss();
                Toast.makeText(MainActivity.this, getString(R.string.connect_fail), Toast.LENGTH_LONG).show();
            }

            @Override
            public void onConnectSuccess(BleDevice bleDevice, BluetoothGatt gatt, int status) {
                addText(txt, "connect.onConnectSuccess");
                mPeripheral.setLeDValue(true);

                progressDialog.dismiss();
                BluetoothGattCharacteristic characteristic = MISCALEConnectUtil.getCharacteristic(gatt);
                regNotify(false, bleDevice, characteristic);
            }

            @Override
            public void onDisConnected(boolean isActiveDisConnected, BleDevice bleDevice, BluetoothGatt gatt, int status) {
                progressDialog.dismiss();
                addText(txt, "connect.onDisConnected:isActiveDisConnected:"+isActiveDisConnected);
                mPeripheral.setLeDValue(false);
                if (isActiveDisConnected) {
                    Toast.makeText(MainActivity.this, getString(R.string.active_disconnected), Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(MainActivity.this, getString(R.string.disconnected), Toast.LENGTH_LONG).show();
                }
                connect(bleDevice);

            }
        });
    }

//    /**
//     *
//     * @param value
//     */
//    private void setLeDValue(boolean value){
//        try {
//            mLedGpio.setValue(value);
//        } catch (IOException e) {
//            Log.e(TAG, "Error updating GPIO value", e);
//        }
//
//    }
    private void addText(TextView txt, String text) {
        txt.append(text + "\n");

        progressDialog.setMessage(text);
        progressDialog.show();
        Log.d(TAG, text);


    }

    /**
     * 体重秤传回数据解析为体重数值（公斤）
     * @param data
     * @return weight
     */
    public static double convertToWeight(byte[] data) {
        if (data == null || data.length < 1)
            return -1;  //数据异常返回-1

        if ((data[0] & 0xFF) == 0x22) {
            Log.d(TAG, "(data[0] & 0xFF) == 22");
            return (((data[2] & 0xFF) << 8) + (data[1] & 0xFF)) / 200.0;
        }
        return 0;   //数据未稳定返回0

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