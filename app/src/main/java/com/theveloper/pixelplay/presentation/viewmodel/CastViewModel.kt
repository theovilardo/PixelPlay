package com.theveloper.pixelplay.presentation.viewmodel

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouter
import androidx.mediarouter.media.MediaRouteSelector
import com.google.android.gms.cast.CastMediaControlIntent
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import timber.log.Timber

@HiltViewModel
class CastViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    // Cast Routes
    private val _castRoutes = MutableStateFlow<List<MediaRouter.RouteInfo>>(emptyList())
    val castRoutes: StateFlow<List<MediaRouter.RouteInfo>> = _castRoutes.asStateFlow()

    private val _selectedRoute = MutableStateFlow<MediaRouter.RouteInfo?>(null)
    val selectedRoute: StateFlow<MediaRouter.RouteInfo?> = _selectedRoute.asStateFlow()

    private val _routeVolume = MutableStateFlow(0)
    val routeVolume: StateFlow<Int> = _routeVolume.asStateFlow()

    private val _isRefreshingRoutes = MutableStateFlow(false)
    val isRefreshingRoutes: StateFlow<Boolean> = _isRefreshingRoutes.asStateFlow()

    private val _isCastConnecting = MutableStateFlow(false)
    val isCastConnecting: StateFlow<Boolean> = _isCastConnecting.asStateFlow()
    
    // We observe remote playback active state indirectly or via PlayerViewModel? 
    // Actually, CastViewModel focuses on discovery. 
    // PlayerViewModel handles the "Session Connected" and "Remote Playback Active" part.
    // However, CastBottomSheet needs isRemotePlaybackActive. 
    // We might keep isRemotePlaybackActive in PlayerViewModel or mirror it here. 
    // For now, assume CastBottomSheet gets it from PlayerViewModel or we add it later.

    // Wifi
    private val _isWifiEnabled = MutableStateFlow(false)
    val isWifiEnabled: StateFlow<Boolean> = _isWifiEnabled.asStateFlow()

    private val _isWifiRadioOn = MutableStateFlow(false)
    val isWifiRadioOn: StateFlow<Boolean> = _isWifiRadioOn.asStateFlow()

    private val _wifiName = MutableStateFlow<String?>(null)
    val wifiName: StateFlow<String?> = _wifiName.asStateFlow()

    // Bluetooth
    private val _isBluetoothEnabled = MutableStateFlow(false)
    val isBluetoothEnabled: StateFlow<Boolean> = _isBluetoothEnabled.asStateFlow()

    private val _bluetoothName = MutableStateFlow<String?>(null)
    val bluetoothName: StateFlow<String?> = _bluetoothName.asStateFlow()

    private val _bluetoothAudioDevices = MutableStateFlow<List<String>>(emptyList())
    val bluetoothAudioDevices: StateFlow<List<String>> = _bluetoothAudioDevices.asStateFlow()

    // Dependencies
    private val mediaRouter: MediaRouter = MediaRouter.getInstance(context)
    private val castControlCategory = CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
    private val mediaRouteSelector = MediaRouteSelector.Builder()
        .addControlCategory(castControlCategory)
        .build()

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager

    // Callbacks & Receivers
    private var pendingCastRouteId: String? = null

    private var connectingTimeoutJob: kotlinx.coroutines.Job? = null

    private val mediaRouterCallback = object : MediaRouter.Callback() {
        override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) {
            Timber.tag("CastViewModel").d("onRouteAdded: %s (id=%s)", route.name, route.id)
            updateRoutes()
        }
        override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) {
            Timber.tag("CastViewModel").d("onRouteRemoved: %s", route.name)
            updateRoutes()
        }
        override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) {
            Timber.tag("CastViewModel").d("onRouteChanged: %s (id=%s), connectionState=%d", route.name, route.id, route.connectionState)
            updateRoutes()
            if (route.id == _selectedRoute.value?.id) {
                _selectedRoute.value = route
                _routeVolume.value = route.volume
                if (route.connectionState == MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTED) {
                    Timber.tag("CastViewModel").i("Route CONNECTED, setting isCastConnecting=false")
                    _isCastConnecting.value = false
                }
            }
        }
        override fun onRouteSelected(router: MediaRouter, route: MediaRouter.RouteInfo) {
            Timber.tag("CastViewModel").i("onRouteSelected: %s (id=%s), connectionState=%d", route.name, route.id, route.connectionState)
            _selectedRoute.value = route
            _routeVolume.value = route.volume
            // If we selected a Cast route, we might be connecting
            if (route.matchesSelector(mediaRouteSelector) && route.connectionState == MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTING) {
                _isCastConnecting.value = true
            }
        }
        override fun onRouteUnselected(router: MediaRouter, route: MediaRouter.RouteInfo) {
            Timber.tag("CastViewModel").i("onRouteUnselected: %s", route.name)
            if (_selectedRoute.value?.id == route.id) {
                _selectedRoute.value = null
            }
        }
        override fun onRouteVolumeChanged(router: MediaRouter, route: MediaRouter.RouteInfo) {
            if (route.id == _selectedRoute.value?.id) {
                _routeVolume.value = route.volume
            }
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { updateWifiInfo() }
        override fun onLost(network: Network) { updateWifiInfo() }
        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) { updateWifiInfo() }
    }

    private val wifiStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == WifiManager.WIFI_STATE_CHANGED_ACTION) {
                 updateWifiRadioState()
            }
        }
    }

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            if (BluetoothAdapter.ACTION_STATE_CHANGED == action ||
                BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED == action
            ) {
                updateBluetoothState()
            }
        }
    }

    init {
        // Initialize MediaRouter
        mediaRouter.addCallback(mediaRouteSelector, mediaRouterCallback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)
        updateRoutes()
        _selectedRoute.value = mediaRouter.selectedRoute

        // Initialize Wifi Monitoring
        updateWifiInfo()
        updateWifiRadioState()
        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
        context.registerReceiver(wifiStateReceiver, IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION))

        // Initialize Bluetooth Monitoring
        updateBluetoothState()
        val btFilter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
        }
        context.registerReceiver(bluetoothStateReceiver, btFilter)
    }

    override fun onCleared() {
        super.onCleared()
        mediaRouter.removeCallback(mediaRouterCallback)
        connectivityManager.unregisterNetworkCallback(networkCallback)
        try {
            context.unregisterReceiver(wifiStateReceiver)
        } catch (e: IllegalArgumentException) {
            // Ignore if not registered
        }
        try {
            context.unregisterReceiver(bluetoothStateReceiver)
        } catch (e: IllegalArgumentException) {
            // Ignore
        }
    }
    
    fun refreshLocalConnectionInfo() {
        updateWifiInfo()
        updateBluetoothState()
    }

    fun refreshCastRoutes() {
        _isRefreshingRoutes.value = true
        // Force update
        mediaRouter.removeCallback(mediaRouterCallback)
        mediaRouter.addCallback(mediaRouteSelector, mediaRouterCallback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY or MediaRouter.CALLBACK_FLAG_FORCE_DISCOVERY)
         viewModelScope.launch {
            kotlinx.coroutines.delay(2000) // Simulate refresh/wait for discovery
            _isRefreshingRoutes.value = false
        }
    }

    fun selectRoute(route: MediaRouter.RouteInfo) {
        Timber.tag("CastViewModel").i("selectRoute called for route=%s (id=%s), isEnabled=%s", route.name, route.id, route.isEnabled)
        if (route.isEnabled) {
            mediaRouter.selectRoute(route)
            _isCastConnecting.value = true
            Timber.tag("CastViewModel").i("Route selected, isCastConnecting set to true")
            
            // Start a timeout to reset connecting state if it gets stuck
            connectingTimeoutJob?.cancel()
            connectingTimeoutJob = viewModelScope.launch {
                kotlinx.coroutines.delay(30_000) // 30 second timeout
                if (_isCastConnecting.value) {
                    Timber.tag("CastViewModel").w("Connection timeout - resetting isCastConnecting to false")
                    _isCastConnecting.value = false
                }
            }
        } else {
            Timber.tag("CastViewModel").w("Route is not enabled, cannot select")
        }
    }
    
    fun cancelConnectingTimeout() {
        connectingTimeoutJob?.cancel()
        connectingTimeoutJob = null
    }

    fun disconnect() {
         mediaRouter.unselect(0) 
         mediaRouter.defaultRoute.select()
         _isCastConnecting.value = false
    }

    fun setRouteVolume(volume: Int) {
        _selectedRoute.value?.requestSetVolume(volume)
    }

    private fun updateRoutes() {
        _castRoutes.value = mediaRouter.routes.filter { 
            it.matchesSelector(mediaRouteSelector) && !it.isDefault 
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

    @Suppress("DEPRECATION")
    private fun updateWifiInfo() {
         val caps = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
         val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
         _isWifiEnabled.value = isWifi

         if (isWifi) {
             // Try to get SSID
             try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                     if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                         val info: WifiInfo? = wifiManager?.connectionInfo
                         val ssid = info?.ssid
                         _wifiName.value = if (ssid != null && ssid != "<unknown ssid>") {
                             ssid.trim('\"')
                         } else {
                             "Connected"
                         }
                     } else {
                         val info: WifiInfo? = wifiManager?.connectionInfo
                         val ssid = info?.ssid
                         _wifiName.value = if (ssid != null && ssid != "<unknown ssid>") {
                             ssid.trim('\"')
                         } else {
                             "Connected"
                         }
                     }
                } else {
                     val info: WifiInfo? = wifiManager?.connectionInfo
                     val ssid = info?.ssid
                     _wifiName.value = if (ssid != null && ssid != "<unknown ssid>") {
                         ssid.trim('\"')
                     } else {
                         "Connected"
                     }
                }
             } catch (e: Exception) {
                 _wifiName.value = "Connected"
             }
         } else {
             _wifiName.value = null
         }
    }
    
    private fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Permission implicitly granted on older versions by manifest
        }
    }

    private fun updateBluetoothState(forceClear: Boolean = false) {
        if (!hasBluetoothPermission()) {
            if (forceClear) _bluetoothName.value = null
            _bluetoothAudioDevices.value = emptyList()
            // Even if we lack permission, we might still check adapter state if possible, but safer to return
            _isBluetoothEnabled.value = bluetoothAdapter?.isEnabled ?: false
            return
        }
        
        val isOn = bluetoothAdapter?.isEnabled ?: false
        _isBluetoothEnabled.value = isOn

        if (!isOn) {
            _bluetoothName.value = null
             updateBluetoothAudioDevices()
            return
        }

        // Logic from PlayerViewModel
        val connectedDevice = safeGetConnectedDevices(android.bluetooth.BluetoothProfile.A2DP).firstOrNull()
            ?: safeGetConnectedDevices(android.bluetooth.BluetoothProfile.HEADSET).firstOrNull()
            ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                safeGetConnectedDevices(android.bluetooth.BluetoothProfile.LE_AUDIO).firstOrNull()
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

        val resolvedName = connectedDevice?.name ?: activeBluetoothAudioName ?: if (bluetoothAdapter!!.bondedDevices.isNotEmpty()) "Active" else null

        when {
            resolvedName != null -> _bluetoothName.value = resolvedName
            forceClear -> _bluetoothName.value = null
        }

        updateBluetoothAudioDevices()
    }
    
    private fun updateBluetoothAudioDevices() {
        if (!hasBluetoothPermission()) {
            _bluetoothAudioDevices.value = emptyList()
            return
        }

        val connectedDevices = buildSet {
            safeGetConnectedDevices(android.bluetooth.BluetoothProfile.A2DP).mapNotNullTo(this) { it.name }
            safeGetConnectedDevices(android.bluetooth.BluetoothProfile.HEADSET).mapNotNullTo(this) { it.name }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                safeGetConnectedDevices(android.bluetooth.BluetoothProfile.LE_AUDIO).mapNotNullTo(this) { it.name }
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
    
    private fun safeGetConnectedDevices(profile: Int): List<android.bluetooth.BluetoothDevice> {
        return runCatching { bluetoothManager.getConnectedDevices(profile) }.getOrElse { emptyList() }
    }
}
