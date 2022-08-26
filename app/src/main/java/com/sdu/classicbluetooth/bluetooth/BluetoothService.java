package com.sdu.classicbluetooth.bluetooth;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;

import com.sdu.classicbluetooth.MainActivity;
import com.sdu.classicbluetooth.adapter.DeviceAdapter;
import com.sdu.classicbluetooth.utils.ToastUtil;

import java.util.ArrayList;
import java.util.List;

public class BluetoothService {
    private static final int CONNECTED_SUCCESS_STATUE = 0x03;
    private static final int CONNECTED_FAILURE_STATUE = 0x04;

    private BluetoothAdapter bluetoothAdapter;
    private final Context mContext;
    private DeviceAdapter deviceAdapter;
    private ConnectedThread connectedThread; // 管理连接的线程
    private ConnectThread connectThread;

    private boolean curConnState = false; // 当前设备连接状态
    private BluetoothDevice curBluetoothDevice;
    private Handler handler;

    private ActivityResultLauncher<Intent> openBluetoothLauncher;

    public BluetoothService(Context context, DeviceAdapter deviceAdapter, Handler handler, ActivityResultLauncher<Intent> launcher) {
        this.mContext = context;
        this.deviceAdapter = deviceAdapter;
        this.handler = handler;
        this.openBluetoothLauncher = launcher;
    }

    // 初始化蓝牙
    @SuppressLint("MissingPermission")
    public void initBluetooth() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null) {
            if (bluetoothAdapter.isEnabled()) {
                ToastUtil.show(mContext, "蓝牙已打开");
            } else {
                openBluetoothLauncher.launch(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
            }
        } else {
            ToastUtil.show(mContext, "你的设备不支持蓝牙");
        }
    }

    // 蓝牙设备可被发现的时间
    @SuppressLint("MissingPermission")
    public void ensureDiscoverable() {
        if (bluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE); // 设置蓝牙可见性，最多300秒
            discoverIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300); //搜索300秒
            mContext.startActivity(discoverIntent);
        }
    }
}