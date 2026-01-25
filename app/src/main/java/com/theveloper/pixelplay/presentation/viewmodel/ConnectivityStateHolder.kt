package com.theveloper.pixelplay.presentation.viewmodel

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages WiFi and Bluetooth connectivity state.
 * Extracted from PlayerViewModel to improve modularity.
 *
 * Responsibilities:
 * - WiFi state tracking (enabled, radio state, network name)
 * - Bluetooth state tracking (enabled, device name, connected audio devices)
 * - System callback registration and lifecycle management
 */
@Singleton
class ConnectivityStateHolder @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // WiFi State
    private val _isWifiEnabled = MutableStateFlow(false)
    val isWifiEnabled: StateFlow<Boolean> = _isWifiEnabled.asStateFlow()

    private val _isWifiRadioOn = MutableStateFlow(false)
    val isWifiRadioOn: StateFlow<Boolean> = _isWifiRadioOn.asStateFlow()

    private val _wifiName = MutableStateFlow<String?>(null)
    val wifiName: StateFlow<String?> = _wifiName.asStateFlow()

    // Bluetooth State
    private val _isBluetoothEnabled = MutableStateFlow(false)
    val isBluetoothEnabled: StateFlow<Boolean> = _isBluetoothEnabled.asStateFlow()

    private val _bluetoothName = MutableStateFlow<String?>(null)
    val bluetoothName: StateFlow<String?> = _bluetoothName.asStateFlow()

    private val _bluetoothAudioDevices = MutableStateFlow<List<String>>(emptyList())
    val bluetoothAudioDevices: StateFlow<List<String>> = _bluetoothAudioDevices.asStateFlow()

    // System services
    private val connectivityManager: ConnectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager: WifiManager? =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val bluetoothManager: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val audioManager: android.media.AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager

    // Callbacks and receivers
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var wifiStateReceiver: BroadcastReceiver? = null
    private var bluetoothStateReceiver: BroadcastReceiver? = null
    private var audioDeviceCallback: android.media.AudioDeviceCallback? = null

    private var isInitialized = false

    /**
     * Initialize connectivity monitoring. Should be called once from ViewModel.
     */
    fun initialize() {
        if (isInitialized) return
        isInitialized = true

        // Initial state check
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        updateWifiRadioState()
        _isWifiEnabled.value = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        if (_isWifiEnabled.value) {
            updateWifiInfo(activeNetwork)
        }

        _isBluetoothEnabled.value = bluetoothAdapter?.isEnabled ?: false
        updateBluetoothName()

        // Register WiFi network callback
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val caps = connectivityManager.getNetworkCapabilities(network)
                if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                    _isWifiEnabled.value = true
                    updateWifiInfo(network)
                }
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    _isWifiEnabled.value = true
                    updateWifiInfo(network)
                }
            }

            override fun onLost(network: Network) {
                val currentNetwork = connectivityManager.activeNetwork
                val caps = connectivityManager.getNetworkCapabilities(currentNetwork)
                _isWifiEnabled.value = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                if (!_isWifiEnabled.value) _wifiName.value = null
            }
        }
        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback!!)

        // Register WiFi state receiver
        wifiStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == WifiManager.WIFI_STATE_CHANGED_ACTION) {
                    updateWifiRadioState()
                }
            }
        }
        context.registerReceiver(wifiStateReceiver, IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION))

        // Register Bluetooth state receiver
        bluetoothStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothAdapter.ACTION_STATE_CHANGED -> {
                        val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                        _isBluetoothEnabled.value = state == BluetoothAdapter.STATE_ON
                        if (state == BluetoothAdapter.STATE_OFF) {
                            _bluetoothName.value = null
                            _bluetoothAudioDevices.value = emptyList()
                        } else updateBluetoothName(forceClear = false)
                    }
                    BluetoothDevice.ACTION_ACL_CONNECTED,
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        updateBluetoothName(forceClear = intent.action == BluetoothDevice.ACTION_ACL_DISCONNECTED)
                    }
                }
            }
        }
        val btFilter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED).apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        context.registerReceiver(bluetoothStateReceiver, btFilter)

        // Register audio device callback
        audioDeviceCallback = object : android.media.AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out android.media.AudioDeviceInfo>?) {
                updateBluetoothName()
            }
            override fun onAudioDevicesRemoved(removedDevices: Array<out android.media.AudioDeviceInfo>?) {
                updateBluetoothName(forceClear = removedDevices?.any {
                    it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        it.type == android.media.AudioDeviceInfo.TYPE_BLE_HEADSET ||
                        it.type == android.media.AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                        it.type == android.media.AudioDeviceInfo.TYPE_HEARING_AID
                } == true)
            }
        }
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)
    }

    /**
     * Refresh all connectivity information. Call when returning to Cast UI.
     */
    fun refreshLocalConnectionInfo() {
        val currentNetwork = connectivityManager.activeNetwork
        val currentCaps = connectivityManager.getNetworkCapabilities(currentNetwork)
        updateWifiRadioState()
        _isWifiEnabled.value = currentCaps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        if (_isWifiEnabled.value) updateWifiInfo(currentNetwork)

        _isBluetoothEnabled.value = bluetoothAdapter?.isEnabled ?: false
        updateBluetoothName()
        updateBluetoothAudioDevices()
    }

    fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun updateWifiInfo(network: Network?) {
        if (!hasLocationPermission()) {
            _wifiName.value = null
            return
        }
        if (network == null) {
            _wifiName.value = null
            return
        }
        val caps = connectivityManager.getNetworkCapabilities(network)
        if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
            val wifiInfo = caps.transportInfo as? android.net.wifi.WifiInfo
            val rawSsid = wifiInfo?.ssid
            if (rawSsid != null && rawSsid != "<unknown ssid>") {
                _wifiName.value = rawSsid.trim('"')
            } else {
                // Fallback to WifiManager for older APIs
                val info = wifiManager?.connectionInfo
                val fallbackSsid = info?.ssid
                if (fallbackSsid != null && fallbackSsid != "<unknown ssid>") {
                    _wifiName.value = fallbackSsid.trim('"')
                } else {
                    _wifiName.value = null
                }
            }
        }
    }

    private fun updateWifiRadioState() {
        val state = wifiManager?.wifiState
        _isWifiRadioOn.value = when (state) {
            WifiManager.WIFI_STATE_ENABLED, WifiManager.WIFI_STATE_ENABLING -> true
            WifiManager.WIFI_STATE_DISABLED, WifiManager.WIFI_STATE_DISABLING -> false
            else -> wifiManager?.isWifiEnabled == true
        }
    }

    @SuppressLint("MissingPermission")
    private fun updateBluetoothName(forceClear: Boolean = false) {
        if (!hasBluetoothPermission()) {
            if (forceClear) _bluetoothName.value = null
            _bluetoothAudioDevices.value = emptyList()
            return
        }

        val connectedDevice = safeGetConnectedDevices(BluetoothProfile.A2DP).firstOrNull()
            ?: safeGetConnectedDevices(BluetoothProfile.HEADSET).firstOrNull()
            ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                safeGetConnectedDevices(BluetoothProfile.LE_AUDIO).firstOrNull()
            } else {
                null
            }

        val audioDevices = audioManager.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
        val activeBluetoothAudioName = audioDevices.firstOrNull {
            it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == android.media.AudioDeviceInfo.TYPE_BLE_HEADSET ||
                it.type == android.media.AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                it.type == android.media.AudioDeviceInfo.TYPE_HEARING_AID
        }?.productName?.toString()

        val resolvedName = connectedDevice?.name ?: activeBluetoothAudioName

        when {
            resolvedName != null -> _bluetoothName.value = resolvedName
            forceClear || !(bluetoothAdapter?.isEnabled ?: false) -> _bluetoothName.value = null
        }

        updateBluetoothAudioDevices()
    }

    @SuppressLint("MissingPermission")
    private fun updateBluetoothAudioDevices() {
        if (!hasBluetoothPermission()) {
            _bluetoothAudioDevices.value = emptyList()
            return
        }

        val connectedDevices = buildSet {
            safeGetConnectedDevices(BluetoothProfile.A2DP).mapNotNullTo(this) { it.name }
            safeGetConnectedDevices(BluetoothProfile.HEADSET).mapNotNullTo(this) { it.name }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                safeGetConnectedDevices(BluetoothProfile.LE_AUDIO).mapNotNullTo(this) { it.name }
            }

            audioManager.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
                .filter {
                    it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        it.type == android.media.AudioDeviceInfo.TYPE_BLE_HEADSET ||
                        it.type == android.media.AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                        it.type == android.media.AudioDeviceInfo.TYPE_HEARING_AID
                }
                .mapNotNull { it.productName?.toString() }
                .forEach { add(it) }
        }

        _bluetoothAudioDevices.value = connectedDevices.toList().sorted()
    }

    private fun safeGetConnectedDevices(profile: Int): List<BluetoothDevice> {
        return runCatching { bluetoothManager.getConnectedDevices(profile) }.getOrElse { emptyList() }
    }

    /**
     * Cleanup resources. Should be called from ViewModel's onCleared.
     */
    fun onCleared() {
        networkCallback?.let { 
            runCatching { connectivityManager.unregisterNetworkCallback(it) }
        }
        wifiStateReceiver?.let { 
            runCatching { context.unregisterReceiver(it) }
        }
        bluetoothStateReceiver?.let { 
            runCatching { context.unregisterReceiver(it) }
        }
        audioDeviceCallback?.let { 
            audioManager.unregisterAudioDeviceCallback(it) 
        }
        isInitialized = false
    }
}
