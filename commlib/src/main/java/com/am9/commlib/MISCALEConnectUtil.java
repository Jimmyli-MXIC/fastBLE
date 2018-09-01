package com.am9.commlib;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.clj.fastble.BleManager;
import com.clj.fastble.callback.BleGattCallback;
import com.clj.fastble.callback.BleScanCallback;
import com.clj.fastble.data.BleDevice;
import com.clj.fastble.exception.BleException;
import com.clj.fastble.scan.BleScanRuleConfig;

import java.util.List;
import java.util.UUID;

/**
 * Created by williamsha on 2018/8/31.
 */

public class MISCALEConnectUtil {
    static String service_uuid = "0000181d-0000-1000-8000-00805f9b34fb";
    static String characteristic_uuid = "00002a9d-0000-1000-8000-00805f9b34fb";
    static String TAG = "MISCALEConnectUtil";


    public  static BluetoothGattCharacteristic getCharacteristic(final BluetoothGatt bluetoothGatt){
        BluetoothGattService service = bluetoothGatt.getService(UUID.fromString(service_uuid));
        BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString(characteristic_uuid));
        return characteristic;
    }

    public static void setScanRule(String str_name, boolean isAutoConnect ) {

        String[] names;
        if (TextUtils.isEmpty(str_name)) {
            names = null;
        } else {
            names = str_name.split(",");
        }


        BleScanRuleConfig scanRuleConfig = new BleScanRuleConfig.Builder()

                .setDeviceName(true, names)   // 只扫描指定广播名的设备，可选

                .setAutoConnect(isAutoConnect)      // 连接时的autoConnect参数，可选，默认false
                .setScanTimeOut(10000)              // 扫描超时时间，可选，默认10秒
                .build();
        BleManager.getInstance().initScanRule(scanRuleConfig);

    }

}
