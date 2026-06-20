package com.hifibt.player.bluetooth

import android.Manifest
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** What we can tell the user about the live Bluetooth audio link. */
data class BtAudioStatus(
    val connectedDeviceName: String? = null,
    /** Negotiated codec name (e.g. "AAC", "SBC") when the OS exposes it, else null. */
    val codecName: String? = null,
    val sampleRateHz: Int? = null,
    val bitsPerSample: Int? = null,
)

/**
 * Reports the state of the Bluetooth audio link to the car.
 *
 * Reality check baked into this class: the codec that decides quality is
 * negotiated by the Android Bluetooth stack and the car's head unit — an app
 * cannot force a codec the car doesn't support. A BMW iDrive unit advertises
 * SBC and AAC; AAC is the best it accepts. So the job here is to (1) surface the
 * negotiated codec honestly, and (2) tell the user how to pin AAC via the
 * system's Developer Options when SBC sneaks in.
 *
 * Reading the live codec (BluetoothCodecStatus) is only available to apps with
 * privileged/system access, so on a normal install we report device + connection
 * state and fall back to guidance for the rest.
 */
class BluetoothAudioMonitor(private val context: Context) {

    private val _status = MutableStateFlow(BtAudioStatus())
    val status: StateFlow<BtAudioStatus> = _status

    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager)?.adapter

    private fun hasConnectPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    /** Query the currently connected A2DP sink (the car) and publish what we can read. */
    fun refresh() {
        val a = adapter ?: return
        if (!hasConnectPermission()) return

        a.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile == BluetoothProfile.A2DP) {
                    val a2dp = proxy as BluetoothA2dp
                    val device: BluetoothDevice? = a2dp.connectedDevices.firstOrNull()
                    _status.value = BtAudioStatus(
                        connectedDeviceName = device?.let { safeName(it) },
                        codecName = readCodecName(a2dp, device),
                    )
                    a.closeProfileProxy(BluetoothProfile.A2DP, proxy)
                }
            }

            override fun onServiceDisconnected(profile: Int) {
                if (profile == BluetoothProfile.A2DP) _status.value = BtAudioStatus()
            }
        }, BluetoothProfile.A2DP)
    }

    private fun safeName(device: BluetoothDevice): String? =
        try {
            if (hasConnectPermission()) device.name else null
        } catch (_: SecurityException) {
            null
        }

    /**
     * BluetoothA2dp#getCodecStatus is a hidden/privileged API. We attempt it via
     * reflection and degrade gracefully — a null result simply means "ask the user
     * to verify AAC in Developer Options", which the UI handles.
     */
    private fun readCodecName(a2dp: BluetoothA2dp, device: BluetoothDevice?): String? {
        device ?: return null
        return try {
            val m = a2dp.javaClass.getMethod("getCodecStatus", BluetoothDevice::class.java)
            val codecStatus = m.invoke(a2dp, device) ?: return null
            val cfg = codecStatus.javaClass.getMethod("getCodecConfig").invoke(codecStatus) ?: return null
            when (cfg.javaClass.getMethod("getCodecType").invoke(cfg) as? Int) {
                0 -> "SBC"
                1 -> "AAC"
                2 -> "aptX"
                3 -> "aptX HD"
                4 -> "LDAC"
                else -> null
            }
        } catch (_: Throwable) {
            null
        }
    }
}
