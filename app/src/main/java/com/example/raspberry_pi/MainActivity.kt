package com.example.raspberry_pi

import android.annotation.SuppressLint
import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.ParcelUuid
import android.provider.Settings
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.example.raspberry_pi.ui.theme.Raspberry_PiTheme
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val targetUuid = "0000181a-0000-1000-8000-00805f9b34fb"
    private val csvFileName = "ble_data.csv"

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var isScanning by mutableStateOf(false)

    private val deviceMap = mutableStateMapOf<String, BleDevice>()
    private val logList = mutableStateListOf<String>()

    // 마지막으로 수신된 유효한 센서 데이터 (SEND 버튼이 이걸 서버로 전송함)
    private var lastValidDevice: BleDevice? = null

    private var teamNumber by mutableStateOf("")
    private var sensorName by mutableStateOf("")

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) {
            appendLog("블루투스 권한이 허용되었습니다.")
        } else {
            appendLog("블루투스 권한이 거부되어 스캔을 사용할 수 없습니다.")
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (!isScanning) return
            val device = result.device
            val scanRecord = result.scanRecord
            val rssi = result.rssi
            val address = device.address

            val name = try {
                device.name ?: scanRecord?.deviceName
            } catch (e: SecurityException) {
                scanRecord?.deviceName
            }

            val uuid = scanRecord?.serviceUuids?.firstOrNull()?.toString() ?: targetUuid

            val serviceData = scanRecord?.getServiceData(ParcelUuid.fromString(targetUuid))
            val rawDataText = serviceData?.joinToString(prefix = "[", postfix = "]") { it.toInt().toString() } ?: ""
            val sensorPacket = SensorPacket.parse(serviceData)

            val bleDevice = BleDevice(name, address, rssi, uuid, scanRecord, sensorPacket)
            deviceMap[address] = bleDevice

            if (sensorPacket != null) {
                lastValidDevice = bleDevice
            }

            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA).format(Date())
            saveToCsv(timestamp, name, address, rssi, uuid, rawDataText, sensorPacket)
        }

        override fun onScanFailed(errorCode: Int) {
            isScanning = false
            appendLog("스캔 실패. 오류 코드: $errorCode")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        setContent {
            Raspberry_PiTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    BleScannerScreen(
                        modifier = Modifier.padding(innerPadding),
                        devices = deviceMap.values.toList(),
                        logs = logList,
                        isScanning = isScanning,
                        teamNumber = teamNumber,
                        sensorName = sensorName,
                        onTeamNumberChange = { teamNumber = it },
                        onSensorNameChange = { sensorName = it },
                        onScanClick = { startBleScan() },
                        onStopClick = { stopBleScan() },
                        onSaveClick = { exportCsvToDownloads() },
                        onRefreshClick = { refreshRecords() },
                        onSendClick = { sendToServer() }
                    )
                }
            }
        }

        requestBlePermissionsIfNeeded()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopBleScan()
    }

    private fun hasAllPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestBlePermissionsIfNeeded() {
        if (hasAllPermissions()) return
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        permissionLauncher.launch(permissions)
    }

    private fun startBleScan() {
        if (isScanning) return
        if (!hasAllPermissions()) {
            requestBlePermissionsIfNeeded()
            return
        }
        val adapter = bluetoothAdapter
        if (adapter == null) {
            appendLog("이 기기는 블루투스를 지원하지 않습니다.")
            return
        }
        if (!adapter.isEnabled) {
            try {
                adapter.enable()
            } catch (e: SecurityException) {
                appendLog("블루투스 활성화 권한이 없습니다.")
            }
            appendLog("블루투스가 꺼져 있어 활성화를 요청했습니다. 다시 SCAN을 눌러주세요.")
            return
        }

        bluetoothLeScanner = adapter.bluetoothLeScanner
        val scanner = bluetoothLeScanner
        if (scanner == null) {
            appendLog("BLE 스캐너를 사용할 수 없습니다.")
            return
        }

        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid.fromString(targetUuid))
                .build()
        )

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()

        try {
            scanner.startScan(filters, settings, scanCallback)
            isScanning = true
            appendLog("BLE 스캔을 시작했습니다.")
        } catch (e: SecurityException) {
            appendLog("권한 부족으로 스캔을 시작할 수 없습니다.")
        }
    }

    private fun stopBleScan() {
        val scanner = bluetoothLeScanner
        if (scanner == null || !isScanning) return
        try {
            scanner.stopScan(scanCallback)
        } catch (e: SecurityException) {
            appendLog("권한 부족으로 스캔을 중지할 수 없습니다.")
        }
        isScanning = false
        appendLog("BLE 스캔을 중지했습니다.")
    }

    private fun refreshRecords() {
        if (isScanning) {
            appendLog("먼저 STOP을 눌러 스캔을 중지해주세요.")
            return
        }
        val dir = getExternalFilesDir(null)
        if (dir == null) {
            appendLog("앱 저장소를 사용할 수 없습니다.")
            return
        }
        val sourceFile = File(dir, csvFileName)
        if (sourceFile.exists() && !sourceFile.delete()) {
            appendLog("CSV 파일을 삭제하지 못했습니다.")
            return
        }
        deviceMap.clear()
        lastValidDevice = null
        appendLog("스캔 기록과 앱 내부 CSV 데이터를 지웠습니다.")
    }

    private fun appendLog(message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date())
        logList.add(0, "[$time] $message")
        if (logList.size > 50) {
            logList.removeAt(logList.size - 1)
        }
    }

    private fun saveToCsv(
        timestamp: String,
        name: String?,
        address: String,
        rssi: Int,
        uuid: String,
        rawData: String,
        sensorPacket: SensorPacket?
    ) {
        try {
            val dir = getExternalFilesDir(null) ?: throw IOException("앱 저장소를 사용할 수 없습니다.")
            val file = File(dir, csvFileName)
            if (file.exists() && file.length() > 0L) {
                upgradeLegacyCsvIfNeeded(file)
            }
            val needsHeader = !file.exists() || file.length() == 0L
            FileWriter(file, true).use { writer ->
                if (needsHeader) writer.appendLine(SensorCsv.header)
                writer.appendLine(SensorCsv.row(timestamp, name, address, rssi, uuid, rawData, sensorPacket))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            appendLog("CSV 저장 중 오류: ${e.message}")
        }
    }

    private fun upgradeLegacyCsvIfNeeded(file: File) {
        val currentHeader = file.bufferedReader().use { it.readLine() }
        if (currentHeader == SensorCsv.header) return
        if (currentHeader != SensorCsv.legacyHeader) {
            throw IOException("알 수 없는 CSV 열 형식입니다. 기존 파일은 보존했습니다.")
        }

        val temporaryFile = File(file.parentFile, "${file.name}.tmp")
        temporaryFile.bufferedWriter().use { writer ->
            writer.appendLine(SensorCsv.header)
            file.forEachLine { line ->
                if (line != SensorCsv.legacyHeader) {
                    writer.appendLine(SensorCsv.upgradeLegacyRow(line))
                }
            }
        }
        Files.move(temporaryFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    // 앱 전용 폴더에 쌓인 CSV를 "다운로드" 폴더로 복사 (파일 관리자에서 바로 보임)
    private fun exportCsvToDownloads() {
        try {
            val sourceFile = File(getExternalFilesDir(null), csvFileName)
            if (!sourceFile.exists()) {
                appendLog("아직 저장된 CSV 데이터가 없습니다. 먼저 SCAN을 해주세요.")
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, csvFileName)
                    put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }

                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri == null) {
                    appendLog("다운로드 폴더에 파일을 생성할 수 없습니다.")
                    return
                }

                resolver.openOutputStream(uri)?.use { outputStream ->
                    sourceFile.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val destFile = File(downloadsDir, csvFileName)
                FileOutputStream(destFile).use { outputStream ->
                    sourceFile.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            }

            appendLog("CSV 파일을 다운로드 폴더에 저장했습니다: $csvFileName")
            Toast.makeText(this, "다운로드 폴더에 저장되었습니다: $csvFileName", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
            appendLog("다운로드 폴더 저장 중 오류: ${e.message}")
        }
    }

    // 마지막 위치 정보 가져오기 (실패 시 0.0, 0.0)
    private fun getLastKnownLocation(): Pair<Double, Double> {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return Pair(0.0, 0.0)
        }
        return try {
            val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providers = locationManager.getProviders(true)
            for (provider in providers) {
                val location = locationManager.getLastKnownLocation(provider)
                if (location != null) {
                    return Pair(location.latitude, location.longitude)
                }
            }
            Pair(0.0, 0.0)
        } catch (e: SecurityException) {
            Pair(0.0, 0.0)
        }
    }

    // SEND 버튼: 가장 최근에 수신한 유효한 센서 데이터를 서버로 전송
    @SuppressLint("HardwareIds")
    private fun sendToServer() {
        val device = lastValidDevice
        val packet = device?.sensorPacket

        if (device == null || packet == null) {
            appendLog("전송할 유효한 센서 데이터가 없습니다. 먼저 SCAN으로 데이터를 수신해주세요.")
            return
        }
        if (teamNumber.isBlank() || sensorName.isBlank()) {
            appendLog("팀 번호와 센서 이름을 입력해주세요.")
            return
        }

        val (lat, lon) = getLastKnownLocation()
        val senderId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"

        val request = SensorRequest(
            team = teamNumber,
            sensor = sensorName,
            mac = device.address,
            temp = packet.temperature,
            humidity = packet.humidity,
            AQI = packet.aqi,
            TVOC = packet.tvoc,
            eCO2 = packet.eco2,
            timestamp = packet.timestamp,
            lat = lat,
            lon = lon,
            sender = senderId
        )

        appendLog("서버로 데이터 전송 중...")

        RetrofitClient.api.sendSensorData(request).enqueue(object : Callback<SensorResponse> {
            override fun onResponse(call: Call<SensorResponse>, response: Response<SensorResponse>) {
                if (response.isSuccessful) {
                    val body = response.body()
                    appendLog("전송 성공: ${body?.result} - ${body?.message}")
                    Toast.makeText(this@MainActivity, "서버 전송 성공", Toast.LENGTH_SHORT).show()
                } else {
                    appendLog("전송 실패: HTTP ${response.code()}")
                }
            }

            override fun onFailure(call: Call<SensorResponse>, t: Throwable) {
                appendLog("전송 오류: ${t.message}")
            }
        })
    }
}

@Composable
fun BleScannerScreen(
    modifier: Modifier = Modifier,
    devices: List<BleDevice>,
    logs: List<String>,
    isScanning: Boolean,
    teamNumber: String,
    sensorName: String,
    onTeamNumberChange: (String) -> Unit,
    onSensorNameChange: (String) -> Unit,
    onScanClick: () -> Unit,
    onStopClick: () -> Unit,
    onSaveClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onSendClick: () -> Unit
) {
    Column(modifier = modifier.fillMaxSize().padding(8.dp)) {

        Text(
            text = "BLE Scanner",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A237E))
                .padding(12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            OutlinedTextField(
                value = teamNumber,
                onValueChange = onTeamNumberChange,
                label = { Text("팀 번호") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = sensorName,
                onValueChange = onSensorNameChange,
                label = { Text("센서 이름") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Button(onClick = onScanClick, contentPadding = PaddingValues(horizontal = 4.dp), modifier = Modifier.weight(1f)) {
                Text("SCAN", fontSize = 12.sp)
            }
            Button(onClick = onStopClick, contentPadding = PaddingValues(horizontal = 4.dp), modifier = Modifier.weight(1f)) {
                Text("STOP", fontSize = 12.sp)
            }
            Button(onClick = onSaveClick, contentPadding = PaddingValues(horizontal = 4.dp), modifier = Modifier.weight(1f)) {
                Text("SAVE", fontSize = 12.sp)
            }
            Button(
                onClick = onRefreshClick,
                enabled = !isScanning,
                contentPadding = PaddingValues(horizontal = 4.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("REFRESH", fontSize = 10.sp)
            }
            Button(
                onClick = onSendClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                contentPadding = PaddingValues(horizontal = 4.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("SEND", fontSize = 12.sp)
            }
        }

        Text(
            text = "Scan Records",
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF3949AB))
                .padding(6.dp)
                .padding(top = 8.dp)
        )

        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            items(devices) { device ->
                Text(text = device.toDisplayString(), modifier = Modifier.fillMaxWidth().padding(8.dp))
                HorizontalDivider()
            }
        }

        Text(
            text = "Log",
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF3949AB))
                .padding(6.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(Color(0xFFF5F5F5))
                .verticalScroll(rememberScrollState())
                .padding(6.dp)
        ) {
            logs.forEach { line -> Text(text = line, fontSize = 12.sp) }
        }
    }
}