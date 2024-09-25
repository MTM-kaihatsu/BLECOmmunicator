package mtmcomm.blecommunicator

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.UUID

class MainActivity : AppCompatActivity() {
    // 関数での結果を受け取るための変数
    private val devices = mutableListOf<String>()
    private val id = mutableListOf<Int>()
    private val id2mac = mutableMapOf<Int, String>()

    // Bluetooth接続用変数
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var device: BluetoothDevice
    private var gatt: BluetoothGatt? = null
    private lateinit var deviceName: String
    private var deviceAddress: String? = null

    // View
    private lateinit var deviceIdTextView: TextView
    private lateinit var scanButton: Button
    private lateinit var temperature1TextView: TextView
    private lateinit var temperature2TextView: TextView
    private lateinit var temperature3TextView: TextView
    private lateinit var voltageTextView: TextView
    private lateinit var idListView: ListView

    private var dataReceiveCount = 0  // データを受け取った回数をカウントするための変数


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_detail)

        val bluetoothManager = getSystemService(BluetoothManager::class.java)
        bluetoothAdapter = bluetoothManager.adapter

        checkPermissionAndScan()

        scanButton = findViewById(R.id.scanButton)
        // スキャンボタンクリック時イベント
        scanButton.setOnClickListener {
            checkPermissionAndScan()
        }

        deviceName = "Feath"
        // ListViewの設定
        idListView = findViewById<ListView>(R.id.deviceIdListView)
        Log.d("BLECommunicator", "ID : $id")
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, id)
        idListView.adapter = adapter

        // リストアイテムが選択されたときの処理
        idListView.setOnItemClickListener { parent, view, position, id ->
            val selectedDeviceId = parent.getItemAtPosition(position) as Int
            deviceAddress = id2mac[selectedDeviceId]
            device = bluetoothAdapter.getRemoteDevice(deviceAddress)
            Log.d("ListView", "Selected Device ID: $selectedDeviceId, Device Address: $deviceAddress")

            // ボタンを有効化
            val getDataButton = findViewById<Button>(R.id.getDataButton)
            getDataButton.isEnabled = true
        }

        findViewById<TextView>(R.id.deviceNameTextView).text = "Device ID:"

        // ボタンを取得し、クリックリスナーを設定
        val getDataButton = findViewById<Button>(R.id.getDataButton)
        getDataButton.setOnClickListener {
            Toast.makeText(this, "Connecting to device...", Toast.LENGTH_SHORT).show()
            connectToDevice() // ボタンを押した時にデバイスに接続
        }

        // TextViewの値を参照
        deviceIdTextView = findViewById(R.id.deviceId)
        temperature1TextView = findViewById(R.id.temperature1)
        temperature2TextView = findViewById(R.id.temperature2)
        temperature3TextView = findViewById(R.id.temperature3)
        voltageTextView = findViewById(R.id.voltage)
    }

    private fun checkPermissionAndScan() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this, arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ), 1)
        } else {
            findViewById<TextView>(R.id.deviceNameTextView).text = "Scanning Devices..."
            scanForDevices()
        }
    }

    private fun scanForDevices() {
        val bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
        devices.clear()

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.device?.let { device ->
                    try {
                        val deviceName = device.name ?: "Unknown Device"
                        val deviceAddress = device.address
                        val deviceInfo = "$deviceName ($deviceAddress)"
                        Log.d("BLECommunicator", "device name is $deviceName")
                        if (!devices.contains(deviceInfo) && deviceName == "Feath") {
                            devices.add(deviceInfo)
                        } else {

                        }
                    } catch (e: SecurityException) {
                        Log.e(
                            "BLECommunicator",
                            "Permission denied for accessing device name or address: ${e.message}"
                        )
                    }
                }
            }
        }
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                bluetoothLeScanner.startScan(scanCallback)
                // スキャンを2秒間行い、その後接続を開始
                window.decorView.postDelayed({
                    bluetoothLeScanner.stopScan(scanCallback)
                    Log.d("BLECommunicator", "Scan complete. Devices found: ${devices.size}")
                    if (devices.isNotEmpty()) {
                        testDevices(devices)
                    } else {
                        Toast.makeText(this, "No Feath devices found.", Toast.LENGTH_SHORT).show()
                    }
                    findViewById<TextView>(R.id.deviceNameTextView).text = "Device ID:"
                }, 2000)
            } catch (e: SecurityException) {
                Log.e(
                    "BLECommunicator",
                    "Permission denied for starting Bluetooth scan: ${e.message}"
                )
            }
        }
    }

    private fun testDevices(devices: MutableList<String>) {
        for (deviceInfo in devices) {
            val deviceAddress = deviceInfo.substringAfter("(").substringBefore(")")
            val device = bluetoothAdapter.getRemoteDevice(deviceAddress)

            // ここでデバイスに接続
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) { return
            }
            gatt = device.connectGatt(this, false, object : BluetoothGattCallback() {
                override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {

                        // サービスを発見
                        if (ActivityCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.BLUETOOTH_CONNECT
                            ) != PackageManager.PERMISSION_GRANTED
                        ) { return
                        }
                        gatt.discoverServices()
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        Log.d("BLECommunicator", "Disconnected from $deviceInfo")
                    }
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        val service = gatt.getService(UUID.fromString("ffb5a6ac-3d50-6077-76d8-91ebef580d90"))
                        val characteristic = service.getCharacteristic(UUID.fromString("4ced6dcf-51c9-cd4b-30f0-3d39f7243209"))

                        if (characteristic != null) {
                            // CharacteristicがIndicateプロパティを持つか確認
                            if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE > 0) {
                                val cccd = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                                cccd.value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                                if (ActivityCompat.checkSelfPermission(
                                        this@MainActivity,
                                        Manifest.permission.BLUETOOTH_CONNECT
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) { return
                                }
                                gatt.writeDescriptor(cccd)
                                gatt.setCharacteristicNotification(characteristic, true)
                            }
                        }
                    }
                }

                override fun onCharacteristicChanged(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic
                ) {
                    val value = characteristic.value

                    // 2バイトごとに区切る
                    val groupedValues = value.toList().chunked(2)
                    val group = groupedValues[0]
                    val byte1 = group[0].toInt() and 0xFF
                    val byte2 = group[1].toInt() and 0xFF
                    val decimalValue = (byte1 shl 8) or byte2
                    if (decimalValue !in id){
                        id.add(decimalValue)
                        id2mac[decimalValue] = deviceAddress
                        Log.d("BLECommunicator", "ID : $id")
                        runOnUiThread {
                            updateDeviceIdList()  // UI更新をメインスレッドで行う
                        }
                    }
                    if (ActivityCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        return
                    }
                    gatt.disconnect()
                    gatt.close()
                }
            })

            // 接続したらMACアドレスとDevice IDを記録
            Log.d("DeviceInfo", "Device Address: $deviceAddress")
            // Device IDの取得処理をここに追加することができます
        }
        Log.d("BLECommunicator", "ID : $id")
    }
    private fun updateDeviceIdList() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, id)
        idListView.adapter = adapter
    }

    private fun connectToDevice() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // 必要な権限がない場合はリクエスト
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 1)
            return
        }


        // 前回の接続があればクローズする
        gatt?.disconnect()
        gatt?.close()  // ここでリソースを完全に解放
        gatt = null    // 次回の接続に備えてnullにする

        // ボタンのテキストを変更
        val getDataButton = findViewById<Button>(R.id.getDataButton)
        getDataButton.text = "Receiving data..."

        gatt = device.connectGatt(this, false, object: BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Connected to $deviceName", Toast.LENGTH_SHORT).show()

                    }
                    //サービスを発見
                    if (ActivityCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        return
                    }
                    gatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Disconnected", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    // サービスが発見された時の処理
                    val service = gatt.getService(UUID.fromString("ffb5a6ac-3d50-6077-76d8-91ebef580d90"))
                    val characteristic = service.getCharacteristic(UUID.fromString("4ced6dcf-51c9-cd4b-30f0-3d39f7243209"))

                    if (characteristic != null) {
                        Log.w("GATT", "characteristic is not null.")

                        // Characteristic が Indicate プロパティを持っているかチェック
                        if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE > 0) {
                            Log.d("BLECommunicator", "Characteristic supports Indicate")

                            // CCCD (Client Characteristic Configuration Descriptor) の設定
                            val cccd = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                            if (cccd != null) {
                                cccd.value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                                if (ActivityCompat.checkSelfPermission(
                                        this@MainActivity,
                                        Manifest.permission.BLUETOOTH_CONNECT
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    return
                                }
                                gatt.writeDescriptor(cccd)  // CCCD を有効化
                                Log.d("BLECommunicator", "Indication enabled")
                            } else {
                                Log.w("BLECommunicator", "CCCD is null.")
                            }

                            // Indication を受け取るように設定
                            gatt.setCharacteristicNotification(characteristic, true)
                        } else {
                            Log.w("BLECommunicator", "Characteristic does not support Indicate")
                        }
                    } else {
                        Log.w("GATT", "characteristic is null.")
                    }
                } else {
                    Log.w("GATT", "onServicesDiscovered received: $status")
                }
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int
            ) {

                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val stringValue = String(value)
                    Log.d("BLECommunicator", "Characteristic Read: $stringValue")
                } else {
                    Log.w("BLECommunicator", "Characteristic read failed with status: $status")
                }
            }
            @Suppress("DEPRECATION")
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {
                val value = characteristic.value
                val hexString = value.joinToString(separator = " ") { byte ->
                    String.format("%02X", byte)  // バイトを16進数表記に変換
                }
                Log.d("BLECommunicator", "Characteristic Changed (Hex): $hexString")

                // 2バイトごとに区切る
                val groupedValues = value.toList().chunked(2)
                val decimalValues = mutableListOf<Double>()

                for ((index, group) in groupedValues.withIndex()) {
                    val byte1 = group[0].toInt() and 0xFF
                    val byte2 = group[1].toInt() and 0xFF
                    val decimalValue = (byte1 shl 8) or byte2
//                     1〜3番目の値（温度）を1/100で固定小数点表示して°C表記
                    if (index in 1..3) {
                        val fixedPointValue = decimalValue / 100.0
                        Log.d("BLECommunicator", "Decimal Value (Fixed Point): ${"%.2f".format(fixedPointValue)}°C")
                        decimalValues.add(fixedPointValue)
                    } else if (index == 4) { // 4番目の値（電圧）を1/00で固定小数点表示してV表記
                        val fixedPointValue = decimalValue / 100.0
                        Log.d("BLECommunicator", "Decimal Value (Fixed Point): ${"%.2f".format(fixedPointValue)}V")
                        decimalValues.add(fixedPointValue)
                    } else if (index == 0) { // デバイスIDをDoubleにキャストして格納
                        Log.d("BLECommunicator", "Decimal Value: $decimalValue")
                        decimalValues.add(decimalValue.toDouble())
                    }
                }

                runOnUiThread {
                    if (decimalValues.size >= 5) {
                        deviceIdTextView.text = "Device ID: ${decimalValues[0].toInt()}"
                        temperature1TextView.text = "Temperature1: ${"%.2f".format(decimalValues[1])}(°C)"
                        temperature2TextView.text = "Temperature2: ${"%.2f".format(decimalValues[2])}(°C)"
                        temperature3TextView.text = "Temperature3: ${"%.2f".format(decimalValues[3])}(°C)"
                        voltageTextView.text = "Voltage: ${"%.2f".format(decimalValues[4])}(V)"
                        // データを受け取った回数をカウント
                        dataReceiveCount++
                    } else {
                        Log.e("BLEData", "Received incomplete data set: $decimalValues")
                    }
                }

                // 3回データを取得したら接続を切断
                if (dataReceiveCount >= 10) {
                    Log.d("BLECommunicator", "disconnect from the device because received data 3 times.")
                    if (ActivityCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        return
                    }
                    dataReceiveCount = 0
                    getDataButton.text = "get data"
                    gatt.disconnect()
                    gatt.close()
                }
            }

        })
    }
    override fun onDestroy() {
        super.onDestroy()
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        gatt?.close()
    }
}