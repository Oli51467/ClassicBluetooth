package com.sdu.classicbluetooth.bluetooth;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;

import com.rosefinches.smiledialog.SmileDialog;
import com.rosefinches.smiledialog.SmileDialogBuilder;
import com.rosefinches.smiledialog.enums.SmileDialogType;
import com.sdu.classicbluetooth.R;
import com.sdu.classicbluetooth.adapter.DeviceAdapter;
import com.sdu.classicbluetooth.utils.ToastUtil;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;

public class BluetoothService {
    private static final int CONNECTED_SUCCESS_STATUE = 0x03;
    private static final int CONNECTED_FAILURE_STATUE = 0x04;

    public BluetoothAdapter bluetoothAdapter;
    private final Context mContext;
    public DeviceAdapter deviceAdapter;
    private ConnectedThread connectedThread; // 管理连接的线程
    private ConnectThread connectThread;

    private boolean curConnState = false; // 当前设备连接状态
    private BluetoothDevice curBluetoothDevice;
    private Handler handler;
    public LinearLayout layLoading;
    public AppCompatActivity appCompatActivity;

    private ActivityResultLauncher<Intent> openBluetoothLauncher;

    public BluetoothService(Context context, DeviceAdapter deviceAdapter, Handler handler, ActivityResultLauncher<Intent> launcher, LinearLayout layLoading, AppCompatActivity appCompatActivity) {
        this.mContext = context;
        this.deviceAdapter = deviceAdapter;
        this.handler = handler;
        this.openBluetoothLauncher = launcher;
        this.layLoading = layLoading;
        this.appCompatActivity = appCompatActivity;
    }

    @SuppressLint("MissingPermission")
    public void initClick() {
        deviceAdapter.setOnItemClickListener((adapter, view, position) -> {
            //连接设备 判断当前是否还是正在搜索周边设备，如果是则暂停搜索
            if (bluetoothAdapter.isDiscovering()) {
                bluetoothAdapter.cancelDiscovery();
            }
            layLoading.setVisibility(View.VISIBLE);
            // TODO
            connectDevice(deviceAdapter.getItem(position));
        });
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

    // 这个就放在activity里
    public void initReceiver() {
        // 接收广播
        IntentFilter intentFilter = new IntentFilter();     // 创建一个IntentFilter对象
        intentFilter.addAction(BluetoothDevice.ACTION_FOUND);   // 获得扫描结果
        intentFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);  // 绑定状态变化
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);  // 开始扫描
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED); // 扫描结束
        //蓝牙广播接收器
        BluetoothReceiver bluetoothReceiver = new BluetoothReceiver();    // 实例化广播接收器
        mContext.registerReceiver(bluetoothReceiver, intentFilter);  // 注册广播接收器
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

    // 获取配对过的设备
    @SuppressLint("MissingPermission")
    public Set getBluetoothDevices() {
        Set<BluetoothDevice> bluetoothDevices = bluetoothAdapter.getBondedDevices();
        if (bluetoothDevices.size() > 0) {
            for (BluetoothDevice bluetoothDevice : bluetoothDevices) {
                Log.d("Bluetooth", bluetoothDevice.getName() + "  |  " + bluetoothDevice.getAddress());
            }
        } else {
            Toast.makeText(mContext, "没有配对过的设备", Toast.LENGTH_SHORT).show();
        }
        return bluetoothDevices;
    }

    /**
     * 创建或者取消匹配
     *
     * @param type   处理类型 1 匹配  2  取消匹配
     * @param device 设备
     */
    public void createOrRemoveBond(int type, BluetoothDevice device) {
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

    @SuppressLint("MissingPermission")
    public void scanBluetooth() {
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
            ToastUtil.show(mContext, "你的设备不支持蓝牙");
        }
    }

    /**
     * 连接设备
     */
    @SuppressLint("MissingPermission")
    public void connectDevice(BluetoothDevice btDevice) {
        if (btDevice.getBondState() == BluetoothDevice.BOND_NONE) {
            createOrRemoveBond(1, btDevice);    //开始匹配
            // TODO 连接
        } else {
            SmileDialog dialog = new SmileDialogBuilder(appCompatActivity, SmileDialogType.WARNING)
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
                layLoading.setVisibility(View.VISIBLE);   // 显示加载布局
            }
            // 扫描结束
            else if (action.equals(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)) {
                layLoading.setVisibility(View.GONE);  // 隐藏加载布局
            }
        }
    }
}