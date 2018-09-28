package com.am9.mythings;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.graphics.Bitmap;
import android.hardware.camera2.CameraAccessException;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
//import java.util.Formatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Base64;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.am9.commlib.MISCALEConnectUtil;
import com.am9.commlib.OpenBlueTask;
import com.am9.mythings.mtcnn.Box;
import com.am9.mythings.mtcnn.MTCNN;
import com.am9.mythings.peripheralIO.PeripheralHelper;
import com.am9.mythings.camera.CameraHandler;
import com.am9.mythings.camera.ImagePreprocessor;
import com.clj.fastble.BleManager;
import com.clj.fastble.callback.BleGattCallback;
import com.clj.fastble.callback.BleNotifyCallback;
import com.clj.fastble.callback.BleScanCallback;
import com.clj.fastble.data.BleDevice;
import com.clj.fastble.exception.BleException;

import org.json.JSONException;
import org.json.JSONObject;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public class MainActivity extends Activity implements ImageReader
        .OnImageAvailableListener {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String HOST_URL = "ws://192.168.164.196:8011";
    /* 输入到MTCNN的图片尺寸 */
    private static final Size MODEL_IMAGE_SIZE = new Size(480, 480);    // 224 224

//    private static final int REQUEST_CODE_OPEN_GPS = 1;
//    private static final int REQUEST_CODE_PERMISSION_LOCATION = 2;

    private ImagePreprocessor mImagePreprocessor;
    private CameraHandler mCameraHandler;

//    private ProgressBar progressBar = findViewById(R.id.progressBar);
    private PeripheralHelper mPeripheral;

    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;

    private ImageView mImage;
    private TextView mResultText;
    private TextView mWeigthText;
    private TextView mStateText;

    private AtomicBoolean mReady = new AtomicBoolean(false);
    // WebSocket
    private OkHttpClient client;
    private WebSocketListener webSocketListener;
    private WebSocket mWebSocket;
    private Request request;

    public MTCNN mtcnn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        mImage = findViewById(R.id.imageView);
        mResultText = findViewById(R.id.nameText);
        mWeigthText = findViewById(R.id.weightText);
        mStateText = findViewById(R.id.stateText);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        initBle();

        //new GpioUtil,构造函数里初始化各IO，举例：mLedGpio
        Log.i(TAG, "Starting BlinkActivity");
        mPeripheral = new PeripheralHelper();


        initWs();
        initCam();
        initMtcnn();
//        CameraHandler.dumpFormatInfo(this);

    }

    private void initMtcnn() {
        mtcnn = new MTCNN(getAssets());
    }

    private void initBle() {
        BleManager.getInstance().init(getApplication());
        BleManager.getInstance().enableBluetooth();
        BleManager.getInstance()
                .enableLog(true)
                .setReConnectCount(1, 5000)
                .setConnectOverTime(20000)
                .setOperateTimeout(5000);
    }

    private void initCam() {
        mBackgroundThread = new HandlerThread("BackgroundThread");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
        mBackgroundHandler.post(mInitializeOnBackground);
    }

    private Runnable mInitializeOnBackground = new Runnable() {
        @Override
        public void run() {
            mCameraHandler = CameraHandler.getInstance();
            try {
                mCameraHandler.initializeCamera(MainActivity.this,
                        mBackgroundHandler, MODEL_IMAGE_SIZE, MainActivity.this);
                CameraHandler.dumpFormatInfo(MainActivity.this);
            } catch (CameraAccessException e) {
                throw new RuntimeException(e);
            }
            Size cameraCaptureSize = mCameraHandler.getImageDimensions();

            mImagePreprocessor =
                    new ImagePreprocessor(cameraCaptureSize.getWidth(), cameraCaptureSize.getHeight(),
                            MODEL_IMAGE_SIZE.getWidth(), MODEL_IMAGE_SIZE.getHeight());
            mReady.set(true);
        }
    };

    private Runnable mBackgroundClickHandler = new Runnable() {
        @Override
        public void run() {

            mCameraHandler.takePicture();
        }
    };
    /**
     * Verify and initiate a new image capture
     */
    @SuppressLint("SetTextI18n")
    private void startImageCapture() {
        boolean isReady = mReady.get();
        Log.d(TAG, "Ready for another capture? " + isReady);
        if (isReady) {
            mReady.set(false);
//            mResultText.setText("Hold on...");
            mBackgroundHandler.post(mBackgroundClickHandler);
        } else {
            Log.i(TAG, "Sorry, processing hasn't finished. Try again in a few seconds");
        }
    }
//    private void connectWs() {
//        Request request = new Request.Builder()
//                .url(HOST_URL)
//                .build();
//        client.newWebSocket(request, webSocketListener);
//    }

    private void initWs() {
        client = new OkHttpClient.Builder().
                connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS).build();
//        .readTimeout(3, TimeUnit.SECONDS)
        request = new Request.Builder()
                .url(HOST_URL)
                .build();
        webSocketListener = new WebSocketListener() {

            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                super.onOpen(webSocket, response);
//                mWebSocket = webSocket;
                Log.d("WEBSOCKET", "==ONOPEN:==" + response);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mStateText.setText("connect success");
                    }

                });
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                super.onMessage(webSocket, text);
                try {
                    JSONObject jsonObject = new JSONObject(text);
                    JSONObject jsonPerson = jsonObject.getJSONArray("data").getJSONObject(0);
                    final String name = jsonPerson.getString("name");
                    final Double acc = jsonPerson.getDouble("prob");
                    @SuppressLint("DefaultLocale") final String acc1 = String.format("%.2f", acc);
                    Log.d(TAG, "name:" + name);

                    runOnUiThread(new Runnable() {
                        @SuppressLint("SetTextI18n")
                        @Override
                        public void run() {
                            try {
                                mResultText.setText(String.format("name: %s accuracy: %s", name, acc1));

                            } catch (Exception e) {
                                Log.e(TAG, " " + e);
                            }
                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    try{
                                        Thread.sleep(7000);
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                mResultText.setText(" ");
                                            }
                                        });
                                    }catch (InterruptedException e){
                                        e.printStackTrace();
                                    }
                                }
                            }).start();
                        }

                    });

                } catch (JSONException e) {
                    e.printStackTrace();
                }

                Log.d("WEBSOCKET", "==onMessage:==" + text);
            }


            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                super.onMessage(webSocket, bytes);

                Log.d("WEBSOCKET", "==onMessagebytes:==" + bytes.toString());

            }


            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                super.onClosing(webSocket, code, reason);

                Log.d("WEBSOCKET", "==onClosing:==" + reason + "#" + code);
            }


            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                super.onClosed(webSocket, code, reason);

                Log.d("WEBSOCKET", "==onClosed:==" + reason + "#" + code);
//                mWebSocket = null;
            }


            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                super.onFailure(webSocket, t, response);
                runOnUiThread(
                        new Runnable() {
                    @Override
                    public void run() {
                        mStateText.setText("connect failure");
                    }

                });

                Log.d("WEBSOCKET", "==onFailure:==" + response);
                t.printStackTrace();
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(10000);
                            mWebSocket = client.newWebSocket(request, webSocketListener);

                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }).start();

            }
        };
        mWebSocket =client.newWebSocket(request,webSocketListener);


    }



    @SuppressLint("SetTextI18n")
    @Override
    protected void onPostResume() {
        super.onPostResume();
//MainActivity.this.runOnUiThread();

        Log.d(TAG, "onPostResume");
        Runnable runnable = new Runnable() {
            public void run() {
                Log.d(TAG, "RUN=======");
                MISCALEConnectUtil.setScanRule("MI_SCALE", false);
                startScan();
            }
        };
//        mResultText.setText("OpenBlueTask");

        OpenBlueTask dTask = new OpenBlueTask(MainActivity.this, runnable);
        dTask.execute(20);

    }

    private void regNotify(boolean isStop, final BleDevice bleDevice, final BluetoothGattCharacteristic characteristic) {
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
//                                    mResultText.setText("notify success, STAND ON the MI_SCALE");
//                                    progressBar.setVisibility(View.GONE);
                                }
                            });

                        }

                        @Override
                        public void onNotifyFailure(final BleException exception) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
//                                    mResultText.setText(exception.toString());
                                }
                            });
                        }

                        @Override
                        public void onCharacteristicChanged(byte[] data) {
                            runOnUiThread(new Runnable() {
                                @SuppressLint("SetTextI18n")
                                @Override
                                public void run() {
//                                    startImageCapture();
                                    convertToWeight(characteristic.getValue());
//                                    mResultText.setText(HexUtil.formatHexString(characteristic.getValue(), true));
//                                    mResultText.setText("" + weight);
                                }
                            });
                        }
                    });
        } else {
            BleManager.getInstance().stopNotify(
                    bleDevice,
                    characteristic.getService().getUuid().toString(),
                    characteristic.getUuid().toString());
//            progressBar.setVisibility(View.GONE);

        }

    }

    private void connectBle(final BleDevice bleDevice) {
        BleManager.getInstance().connect(bleDevice, new BleGattCallback() {
            @Override
            public void onStartConnect() {
//                mResultText.setText("connectBle.onStartConnect");
            }

            @Override
            public void onConnectFail(BleDevice bleDevice, BleException exception) {
//                mResultText.setText("connectBle.onConnectFail");
                mPeripheral.setLeDValue(false);
//                progressBar.setVisibility(View.GONE);
                Toast.makeText(MainActivity.this, getString(R.string.connect_fail), Toast.LENGTH_LONG).show();
            }

            @Override
            public void onConnectSuccess(BleDevice bleDevice, BluetoothGatt gatt, int status) {
//                mResultText.setText("connectBle.onConnectSuccess");
                try{
                    mPeripheral.setLeDValue(true);}
                    catch (Exception e){
                    Log.e(TAG, "Error set LED value true" + e);
                    }

//                progressBar.setVisibility(View.GONE);
                BluetoothGattCharacteristic characteristic = MISCALEConnectUtil.getCharacteristic(gatt);
                regNotify(false, bleDevice, characteristic);
            }

            @Override
            public void onDisConnected(boolean isActiveDisConnected, BleDevice bleDevice, BluetoothGatt gatt, int status) {
//                progressBar.setVisibility(View.GONE);
//                mResultText.setText("connectBle.onDisConnected:isActiveDisConnected:" + isActiveDisConnected);
                mPeripheral.setLeDValue(false);
                if (isActiveDisConnected) {
                    Toast.makeText(MainActivity.this, getString(R.string.active_disconnected), Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(MainActivity.this, getString(R.string.disconnected), Toast.LENGTH_LONG).show();
                }
                connectBle(bleDevice);
            }
        });
    }

    /**
     * 体重秤传回数据解析为体重数值（公斤）
     *
     * @param data
     * @return weight
     */
    public double convertToWeight(byte[] data) {
        if (data == null || data.length < 1)
            return -1;  //数据异常返回-1
        double weight = ((((data[2] & 0xFF) << 8) + (data[1] & 0xFF)) / 200.0);
        mWeigthText.setText(weight + " kg");


        if ((data[0] & 0xFF) == 0x22) {
            Log.d(TAG, "(data[0] & 0xFF) == 22");
            mPeripheral.setSpeakerValue(true);
            startImageCapture();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try{
                        Thread.sleep(8000);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mWeigthText.setText(" ");
                            }
                        });
                    }catch (InterruptedException e){
                        e.printStackTrace();
                    }
                }
            }).start();

            return (((data[2] & 0xFF) << 8) + (data[1] & 0xFF)) / 200.0;
        }
        return 0;   //数据未稳定返回0

    }

    private void startScan() {
        BleManager.getInstance().scan(new BleScanCallback() {
            @Override
            public void onScanStarted(boolean success) {
//                mResultText.setText("scan.onScanStarted");
            }

            @Override
            public void onScanning(BleDevice bleDevice) {
//                mResultText.setText("scan.onScanning;name:" + bleDevice.getName() + "\n" + bleDevice.getMac());
                BleManager.getInstance().cancelScan();
//                progressBar.setMessage("scan.onScanning:" + bleDevice.getName());
//                progressBar.setVisibility(View.VISIBLE);
                connectBle(bleDevice);
            }

            @Override
            public void onScanFinished(List<BleDevice> scanResultList) {

//                mResultText.setText("scan.onScanFinished:" + scanResultList.size());
//                progressBar.setMessage("scan.onScanFinished");
//                progressBar.setVisibility(View.VISIBLE);

            }
        });
    }

    @Override
    public void onImageAvailable(ImageReader reader) {
        final Bitmap bitmap;

        JSONObject jsonObject;
        try (Image image = reader.acquireNextImage()) {
            bitmap = mImagePreprocessor.preprocessImage(image);
            List<Bitmap> marginBitmap = new ArrayList<>();
            /*
            获取经MTCNN人脸检测之后产生的边框位置坐标,并添加margin后将原始图片进行裁剪,输出marginBitmap;
             */
            try {
                Vector<Box> boxes = mtcnn.detectFaces(
                        bitmap.copy(bitmap.getConfig(), true), 160);
                for (int i = 0; i < boxes.size(); i++) {
                    marginBitmap.add(Bitmap.createBitmap(bitmap,
                            boxes.get(i).marginleft(),
                            boxes.get(i).margintop(),
                            Math.min(boxes.get(i).marginwidth(), bitmap.getWidth() - boxes.get(i).marginleft()),
                            Math.min(boxes.get(i).marginheight(), bitmap.getHeight() - boxes.get(i).margintop())));
                }
            } catch (ArrayIndexOutOfBoundsException e) {
                Log.e(TAG, "[*]detect false: " + e);
            }
            /*
            将marginBitmap转换为Base64编码并通过json对象传到后台;
             */
            for (int i = 0; i < marginBitmap.size(); i++) {
                String b64Img = bitmapToBase64(marginBitmap.get(i));
                jsonObject = new JSONObject();
                try {
                    jsonObject.put("action_type", "/identify_no_mtcnn");
                    jsonObject.put("req_id", "000");
                    jsonObject.put("data", b64Img);
                    String strJSONreq = jsonObject.toString();
                    Log.d(TAG, "strJSONreq:" + strJSONreq);
                    Log.d(TAG, "marginBitmap.size()" + marginBitmap.size());
                    try {
                        mWebSocket.send(strJSONreq);
                    }catch (NullPointerException e){
                        e.printStackTrace();
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

//            String B64img = bitmapToBase64(showBitmap);
//            jsonObject = new JSONObject();
//            try {
//                jsonObject.put("action_type", "/identify");
//                jsonObject.put("req_id", "000");
//                jsonObject.put("data", B64img);
//                String strJSONreq = jsonObject.toString();
//                Log.d(TAG, "strJSONreq:" + strJSONreq);
//                mWebSocket.send(strJSONreq);
//
//            } catch (JSONException e) {
//                e.printStackTrace();
//            }
            //mWebSocket.send(testDataImg);
            //mWebSocket.send("{\"action_type\":\"/identify\",\"req_id\":\"000\",\"data\":\"" + B64img+"\"}");
        }

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mImage.setImageBitmap(bitmap);
//                mResultText.setText("");
            }

        });

        mReady.set(true);

    }

    /**
     * 将bitmap转码为Base64
     * @param bitmap
     * @return
     */
    private String bitmapToBase64(Bitmap bitmap){
        /*Convert image into a byte array.*/
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
        byte[] byteArrayImage = baos.toByteArray();

        String encodedImage = Base64.encodeToString(byteArrayImage, Base64.DEFAULT);
//        Log.d(TAG, "Base64: " + encodedImage);

        return encodedImage;
    }

    @Override
    protected void onPause() {
        mPeripheral.setLeDValue(false);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        try {
            Log.d(TAG, "destroy");
            mPeripheral.setLeDValue(false);
            mPeripheral.getLedGpio().close();
        } catch (IOException e) {
            Log.e(TAG, "getLedGpio cannot be closed");
        }
        super.onDestroy();
        try {
            if (mBackgroundThread != null) mBackgroundThread.quit();
        } catch (Throwable t) {
            // close quietly
        }
        mBackgroundThread = null;
        mBackgroundHandler = null;

        try {
            if (mCameraHandler != null) mCameraHandler.shutDown();
        } catch (Throwable t) {
            // close quietly
        }
    }
}