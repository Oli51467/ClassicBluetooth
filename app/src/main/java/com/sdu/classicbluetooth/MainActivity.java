package com.sdu.classicbluetooth;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.sdu.classicbluetooth.adapter.DeviceAdapter;
import com.sdu.classicbluetooth.bluetooth.BluetoothService;
import com.sdu.classicbluetooth.bluetooth.Constants;
import com.sdu.classicbluetooth.utils.ToastUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    // 权限请求码
    public static final int REQUEST_PERMISSION_CODE = 9527;

    // 蓝牙适配器
    private BluetoothAdapter bluetoothAdapter;

    // 列表适配器
    private DeviceAdapter deviceAdapter;

    private ActivityResultLauncher<Intent> openBluetoothLauncher;

    private LinearLayout layConnectingLoading;  // 等待连接

    private BluetoothService bluetoothService;

    private List<BluetoothDevice> mList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Objects.requireNonNull(getSupportActionBar()).hide();
        initLauncher();
        initView();
        bluetoothService = new BluetoothService(this, deviceAdapter, mHandler, openBluetoothLauncher, layConnectingLoading, this);
        bluetoothService.initReceiver();
        bluetoothService.initClick();
        requestPermission();
    }

    private void initLauncher() {
        mList = new ArrayList<>();
        openBluetoothLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK) {
                if (bluetoothAdapter.isEnabled()) {
                    ToastUtil.show(this, "蓝牙已打开");
                } else {
                    ToastUtil.show(this, "请打开蓝牙");
                }
            }
        });
    }

    @SuppressLint("MissingPermission")
    private void initView() {
        RecyclerView rvDevice = findViewById(R.id.rv_device);
        TextView startScan = findViewById(R.id.btn_start_scan);
        layConnectingLoading = findViewById(R.id.loading_lay);

        startScan.setOnClickListener(this);
        //列表配置
        deviceAdapter = new DeviceAdapter(R.layout.item_device_list, mList);
        deviceAdapter.setAnimationEnable(true);  // 启用动画
        deviceAdapter.setAnimationWithDefault(BaseQuickAdapter.AnimationType.SlideInRight); // 设置动画方式
        rvDevice.setLayoutManager(new LinearLayoutManager(this));
        rvDevice.setAdapter(deviceAdapter);
    }


    /**
     * 请求权限
     */
    private void requestPermission() {
        List<String> neededPermissions = new ArrayList<>();
        String[] permissions = {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN};
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                neededPermissions.add(permission);
            }
        }
        if (!neededPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(this, neededPermissions.toArray(new String[0]), REQUEST_PERMISSION_CODE);
        } else {
            bluetoothService.initBluetooth();
            bluetoothService.ensureDiscoverable();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                bluetoothService.initBluetooth();
                bluetoothService.ensureDiscoverable();
            } else {
                ToastUtil.show(this, "您没有开启权限");
            }
        }
    }

    @SuppressLint({"MissingPermission"})
    @Override
    public void onClick(View v) {
        int vid = v.getId();
        if (vid == R.id.btn_start_scan) {
            bluetoothService.scanBluetooth();
        }
    }

    @SuppressLint("HandlerLeak")
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case Constants.MESSAGE_TOAST:
                    String status = msg.getData().getString(Constants.TOAST);
                    break;
                case Constants.MESSAGE_DEVICE_NAME:
                    String string = msg.getData().getString(Constants.DEVICE_NAME);
                    break;
                case Constants.MESSAGE_READ: //读取接收数据

                    break;
                case Constants.MESSAGE_WRITE: //发送端，正在发送的数据

                    break;
            }
        }
    };
}