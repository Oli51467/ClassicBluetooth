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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.rosefinches.smiledialog.SmileDialog;
import com.rosefinches.smiledialog.SmileDialogBuilder;
import com.rosefinches.smiledialog.enums.SmileDialogType;
import com.sdu.classicbluetooth.adapter.DeviceAdapter;
import com.sdu.classicbluetooth.bluetooth.BluetoothService;
import com.sdu.classicbluetooth.bluetooth.Constants;
import com.sdu.classicbluetooth.utils.ToastUtil;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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
        mList = new ArrayList<>();
        //bluetoothService = new BluetoothService(this, deviceAdapter, mHandler, openBluetoothLauncher);
        initView();
        initReceiver();
        requestPermission();
    }

    private void initLauncher() {
        openBluetoothLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK) {
                if (bluetoothAdapter.isEnabled()) {
                    //蓝牙已打开
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
        deviceAdapter.setOnItemClickListener((adapter, view, position) -> {
            //连接设备 判断当前是否还是正在搜索周边设备，如果是则暂停搜索
            if (bluetoothAdapter.isDiscovering()) {
                bluetoothAdapter.cancelDiscovery();
            }
            connectDevice(deviceAdapter.getItem(position));
        });
    }

    private void initReceiver() {
        // 接收广播
        IntentFilter intentFilter = new IntentFilter();     // 创建一个IntentFilter对象
        intentFilter.addAction(BluetoothDevice.ACTION_FOUND);   // 获得扫描结果
        intentFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);  // 绑定状态变化
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);  // 开始扫描
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED); // 扫描结束
        //蓝牙广播接收器
        BluetoothReceiver bluetoothReceiver = new BluetoothReceiver();    // 实例化广播接收器
        registerReceiver(bluetoothReceiver, intentFilter);  // 注册广播接收器
    }


    /**
     * 初始化蓝牙配置
     */
    private void initBlueTooth() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();    // 获取蓝牙适配器
        if (bluetoothAdapter != null) {
            if (bluetoothAdapter.isEnabled()) {
                ToastUtil.show(this, "蓝牙已打开");
            } else {
                openBluetoothLauncher.launch(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
            }
        } else {
            ToastUtil.show(this, "你的设备不支持蓝牙");
        }
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
            initBlueTooth();
            ensureDiscoverable();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initBlueTooth();
                ensureDiscoverable();
            } else {
                ToastUtil.show(this, "您没有开启权限");
            }
        }
    }

    /**
     * 连接设备
     */
    @SuppressLint("MissingPermission")
    private void connectDevice(BluetoothDevice btDevice) {
        // 显示连接等待布局
        layConnectingLoading.setVisibility(View.VISIBLE);
        if (btDevice.getBondState() == BluetoothDevice.BOND_NONE) {
            createOrRemoveBond(1, btDevice);    //开始匹配
            // TODO 连接
        } else {
            SmileDialog dialog = new SmileDialogBuilder(this, SmileDialogType.WARNING)
                    .hideTitle(true)
                    .setContentText("确定取消配对吗")
                    .setConformBgResColor(R.color.warning)
                    .setConformTextColor(Color.WHITE)
                    .setCancelTextColor(Color.BLACK)
                    .setCancelButton("取消")
                    .setCancelBgResColor(R.color.whiteSmoke)
                    .setConformButton("确定", () -> {
                        createOrRemoveBond(2, btDevice);//取消匹配
                    })
                    .build();
            dialog.show();
        }
    }

    @SuppressLint({"MissingPermission"})
    @Override
    public void onClick(View v) {
        int vid = v.getId();
        if (vid == R.id.btn_start_scan) {
            if (bluetoothAdapter != null) { //是否支持蓝牙
                if (bluetoothAdapter.isEnabled()) { //打开
                    // 开始扫描周围的蓝牙设备,如果扫描到蓝牙设备，通过广播接收器发送广播
                    if (deviceAdapter != null) {    //当适配器不为空时，这时就说明已经有数据了，所以清除列表数据，再进行扫描
                        deviceAdapter.clear();
                    }
                    bluetoothAdapter.startDiscovery();
                } else {  // 未打开
                    Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    openBluetoothLauncher.launch(intent);
                }
            } else {
                ToastUtil.show(this, "你的设备不支持蓝牙");
            }
        }
    }

    /**
     * 创建或者取消匹配
     *
     * @param type   处理类型 1 匹配  2  取消匹配
     * @param device 设备
     */
    private void createOrRemoveBond(int type, BluetoothDevice device) {
        Method method;
        try {
            if (type == 1) {
                method = BluetoothDevice.class.getMethod("createBond");
                method.invoke(device);
            } else if (type == 2) {
                method = BluetoothDevice.class.getMethod("removeBond");
                method.invoke(device);
                deviceAdapter.removeDevice(device); // 清除列表中已经取消了配对的设备
            }
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * 广播接收器
     */
    @SuppressLint({"MissingPermission", "NotifyDataSetChanged"})
    public final class BluetoothReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            // 扫描到设备
            if (action.equals(BluetoothDevice.ACTION_FOUND)) {
                Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
                deviceAdapter.getBondedDevice(pairedDevices);
                //获取周围蓝牙设备
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                deviceAdapter.addDevice(device);
            }
            // 设备绑定状态发生改变
            else if (action.equals(BluetoothDevice.ACTION_BOND_STATE_CHANGED)) {
                deviceAdapter.notifyDataSetChanged();   // 刷新适配器
            }
            // 开始扫描
            else if (action.equals(BluetoothAdapter.ACTION_DISCOVERY_STARTED)) {
                layConnectingLoading.setVisibility(View.VISIBLE);   // 显示加载布局
            }
            // 扫描结束
            else if (action.equals(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)) {
                layConnectingLoading.setVisibility(View.GONE);  // 隐藏加载布局
            }
        }
    }

    // 蓝牙设备可被发现的时间
    @SuppressLint("MissingPermission")
    public void ensureDiscoverable() {
        if (bluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE); // 设置蓝牙可见性，最多300秒
            discoverIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300); //搜索300秒
            startActivity(discoverIntent);
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