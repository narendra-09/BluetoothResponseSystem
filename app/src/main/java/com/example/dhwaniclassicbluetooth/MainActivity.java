package com.example.dhwaniclassicbluetooth;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.SystemClock;
import android.widget.Toast;

import com.example.dhwaniclassicbluetooth.databinding.ActivityMainBinding;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding mainBinding;
    private static final int REQUEST_ENABLE_PERMISSIONS = 100;
    private static final int REQUEST_ENABLE_BLUETOOTH = 120;
    private String[] allPermissions = new String[]{"android.permission.ACCESS_COARSE_LOCATION", "android.permission.ACCESS_FINE_LOCATION"};
    //Bluetooth Related
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothDevice bluetoothDevice;

    //UUID
    private static final UUID myUUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");

    private ConnectThread connectThread;
    private DataFlowThread dataFlowThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mainBinding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(mainBinding.getRoot());
        //Grant Permissions
        checkPermissions();
        setupBluetooth();
        mainBinding.bEnable.setOnClickListener(v -> enableBluetooth());
        mainBinding.bScan.setOnClickListener(v -> getPairedDevices());
        mainBinding.bConnect.setOnClickListener(v -> connectToDevice());
        mainBinding.bDisconnect.setOnClickListener(v -> disconnectFromDevice());
        mainBinding.send.setOnClickListener(v -> {
            byte[] message = "o".getBytes();
            dataFlowThread.write(message);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(deviceReceiver, MainActivity.receiverIF());
    }

    private void setupBluetooth() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is Not supported in this device", Toast.LENGTH_SHORT).show();
        }
    }

    private void enableBluetooth() {
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BLUETOOTH);
        } else {
            Toast.makeText(this, "Already Turned On", Toast.LENGTH_SHORT).show();
        }
    }

    @SuppressLint("SetTextI18n")
    private void getPairedDevices() {
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                mainBinding.bDevices.setText(device.getName() + " " + device.getAddress());
                bluetoothDevice = device;
            }
        } else {
            scanDevices();
        }
    }

    private void scanDevices() {
        if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
            bluetoothAdapter.startDiscovery();
        }
    }

    private void showDevices() {

    }

    private void connectToDevice() {
        if (connectThread == null && bluetoothDevice != null) {
            connectThread = new ConnectThread(bluetoothDevice);
            connectThread.start();
        } else {
            Toast.makeText(this, "Failed to connect", Toast.LENGTH_SHORT).show();
        }
    }

    private void disconnectFromDevice() {
        if (connectThread != null) {
            connectThread.cancel();
        }
    }

    private void checkPermissions() {
        if (allPermissionsGranted()) {
            Toast.makeText(this, "Permissions Granted", Toast.LENGTH_SHORT).show();
        } else {
            ActivityCompat.requestPermissions(MainActivity.this, allPermissions, REQUEST_ENABLE_PERMISSIONS);
        }
    }

    private boolean allPermissionsGranted() {
        for (String permission : allPermissions) {
            if (ContextCompat.checkSelfPermission(MainActivity.this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_ENABLE_PERMISSIONS) {
            if (allPermissionsGranted())
                Toast.makeText(this, "Permissions Granted", Toast.LENGTH_SHORT).show();
            else
                Toast.makeText(this, "Permissions Denied", Toast.LENGTH_SHORT).show();
        }
        if (requestCode == REQUEST_ENABLE_BLUETOOTH) {
            Toast.makeText(this, "Bluetooth Turned On", Toast.LENGTH_SHORT).show();
        }
    }

    private final BroadcastReceiver deviceReceiver = new BroadcastReceiver() {
        @SuppressLint("SetTextI18n")
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if ("00:18:E4:35:05:47".equals(device.getAddress())) {
                    bluetoothDevice = device;
                    mainBinding.bDevices.setText(device.getName() + " " + device.getAddress());
                    bluetoothAdapter.cancelDiscovery();
                }
            }
        }
    };

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(deviceReceiver);
    }

    private static IntentFilter receiverIF() {
        return new IntentFilter(BluetoothDevice.ACTION_FOUND);
    }

    private class ConnectThread extends Thread {
        private final BluetoothDevice bluetoothDevice;
        private final BluetoothSocket bluetoothSocket;

        public ConnectThread(BluetoothDevice device) {
            BluetoothSocket temporary = null;
            bluetoothDevice = device;
            try {
                temporary = bluetoothDevice.createRfcommSocketToServiceRecord(myUUID);
            } catch (IOException e) {
                e.printStackTrace();
            }
            bluetoothSocket = temporary;
        }

        @SuppressLint("SetTextI18n")
        @Override
        public void run() {
            bluetoothAdapter.cancelDiscovery();
            try {
                if (bluetoothSocket != null) {
                    bluetoothSocket.connect();
                    runOnUiThread(()->{mainBinding.bConnect.setText("Connected");});
                    dataFlowThread = new DataFlowThread(bluetoothSocket);
                    dataFlowThread.start();
                }
            } catch (IOException e) {
                try {
                    bluetoothSocket.close();
                } catch (IOException ioException) {
                    ioException.printStackTrace();
                }
            }
        }

        public void cancel() {
            try {
                bluetoothSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class DataFlowThread extends Thread {
        private final BluetoothSocket bluetoothSocket;
        private final InputStream inputStream;
        private final OutputStream outputStream;
        private byte[] buffer;

        public DataFlowThread(BluetoothSocket socket) {
            bluetoothSocket = socket;
            InputStream tmpIS = null;
            OutputStream tmpOS = null;
            try {
                tmpIS = bluetoothSocket.getInputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                tmpOS = bluetoothSocket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
            inputStream = tmpIS;
            outputStream = tmpOS;
        }

        @SuppressWarnings("InfiniteLoopStatement")
        @SuppressLint("SetTextI18n")
        @Override
        public void run() {
            runOnUiThread(()-> mainBinding.Response.append("\n"));
            buffer = new byte[1024];
            int numBytes;
            while (true) {
                try {
                    numBytes = inputStream.read(buffer);
                    String message = new String(buffer, 0, numBytes);
                        runOnUiThread(() -> {
                            if (message.trim().equals("$")) {
                                mainBinding.Response.append("\n");
                                mainBinding.Response.append("Response Received");
                            }
                            if(message.trim().equals("!")){
                                countDownTimer.start();
                            }
                        });
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        public void write(byte[] bytes) {
              try{
                  outputStream.write(bytes);
              } catch (IOException e) {
                  e.printStackTrace();
              }
        }

        public void close(){
            try{
                bluetoothSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private final CountDownTimer countDownTimer = new CountDownTimer(2000,1000) {
        @Override
        public void onTick(long millisUntilFinished) {
         mainBinding.led.setText("LED IS ON");
        }

        @Override
        public void onFinish() {
            mainBinding.led.setText("LED IS OFF");
        }
    };
    @Override
    protected void onDestroy() {
        super.onDestroy();
        connectThread.cancel();
    }
}