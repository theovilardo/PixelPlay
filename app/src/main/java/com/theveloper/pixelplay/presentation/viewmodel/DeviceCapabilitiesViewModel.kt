package com.theveloper.pixelplay.presentation.viewmodel

import android.content.Context
import android.media.AudioManager
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.service.player.DualPlayerEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderCounters

data class CodecInfo(
    val name: String,
    val supportedTypes: List<String>,
    val isHardwareAccelerated: Boolean,
    val maxSupportedInstances: Int
)

data class AudioCapabilities(
    val outputSampleRate: Int,
    val outputFramesPerBuffer: Int,
    val isLowLatencySupported: Boolean,
    val isProAudioSupported: Boolean,
    val supportedCodecs: List<CodecInfo>
)

data class ExoPlayerInfo(
    val version: String,
    val renderers: String,
    val decoderCounters: String
)

data class DeviceCapabilitiesState(
    val deviceInfo: Map<String, String> = emptyMap(),
    val audioCapabilities: AudioCapabilities? = null,
    val exoPlayerInfo: ExoPlayerInfo? = null,
    val isLoading: Boolean = true
)

@HiltViewModel
class DeviceCapabilitiesViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engine: DualPlayerEngine
) : ViewModel() {

    private val _state = MutableStateFlow(DeviceCapabilitiesState())
    val state = _state.asStateFlow()

    init {
        loadCapabilities()
    }

    private fun loadCapabilities() {
        viewModelScope.launch {
            val deviceInfo = getDeviceInfo()
            val audioCaps = getAudioCapabilities()
            val exoInfo = getExoPlayerInfo()

            _state.value = DeviceCapabilitiesState(
                deviceInfo = deviceInfo,
                audioCapabilities = audioCaps,
                exoPlayerInfo = exoInfo,
                isLoading = false
            )
        }
    }

    private fun getDeviceInfo(): Map<String, String> {
        return mapOf(
            "Manufacturer" to Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
            "Model" to Build.MODEL,
            "Brand" to Build.BRAND.replaceFirstChar { it.uppercase() },
            "Device" to Build.DEVICE,
            "Android Version" to Build.VERSION.RELEASE,
            "SDK Version" to Build.VERSION.SDK_INT.toString(),
            "Hardware" to Build.HARDWARE
        )
    }

    private fun getAudioCapabilities(): AudioCapabilities {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val sampleRate = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 44100
        val framesPerBuffer = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull() ?: 256
        val hasLowLatency = context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_AUDIO_LOW_LATENCY)
        val hasProAudio = context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_AUDIO_PRO)

        val supportedCodecs = getSupportedAudioCodecs()

        return AudioCapabilities(
            outputSampleRate = sampleRate,
            outputFramesPerBuffer = framesPerBuffer,
            isLowLatencySupported = hasLowLatency,
            isProAudioSupported = hasProAudio,
            supportedCodecs = supportedCodecs
        )
    }

    private fun getSupportedAudioCodecs(): List<CodecInfo> {
        val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
        val codecs = mutableListOf<CodecInfo>()

        for (codecInfo in codecList.codecInfos) {
            if (codecInfo.isEncoder) continue // Skip encorders

            val types = codecInfo.supportedTypes.filter { it.startsWith("audio/") }
            if (types.isEmpty()) continue

            var isHardware = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                isHardware = codecInfo.isHardwareAccelerated
            }

            // Estimate instances (not always available/accurate via public API directly without capabilities, but usually safe default)
            val instances = try {
                 val caps = codecInfo.getCapabilitiesForType(types[0])
                 caps.maxSupportedInstances
            } catch (e: Exception) {
                -1
            }

            codecs.add(
                CodecInfo(
                    name = codecInfo.name,
                    supportedTypes = types,
                    isHardwareAccelerated = isHardware,
                    maxSupportedInstances = instances
                )
            )
        }
        return codecs.sortedBy { it.name }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    private fun getExoPlayerInfo(): ExoPlayerInfo {
        val player = engine.masterPlayer
        
        // This is a basic info string, expanding it would require deeper reflection or ExoPlayer specific listeners
        // For now, we return version and renderer count.
        
        val version = androidx.media3.common.MediaLibraryInfo.VERSION
        val exoPlayer = player as? androidx.media3.exoplayer.ExoPlayer
        val renderers = "${exoPlayer?.rendererCount ?: 0} Active Renderers"
        
        // We can't easily get internal decoder counters without a listener, 
        // but we can show what we know.
        
        return ExoPlayerInfo(
            version = version,
            renderers = renderers,
            decoderCounters = "N/A (Requires Debug Listener)"
        )
    }
}
