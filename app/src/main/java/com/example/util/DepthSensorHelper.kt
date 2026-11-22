package com.example.util

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build

data class DepthCapabilityInfo(
    val hasHardwareDepthSensor: Boolean,
    val hasArCoreFeature: Boolean
)

object DepthSensorHelper {

    fun inspectDeviceDepthCapability(context: Context): DepthCapabilityInfo {
        val hasArCore = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_AR)
        val hasDepthOutput = runCatching {
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                ?: return@runCatching false
            manager.cameraIdList.any { id ->
                val chars = manager.getCameraCharacteristics(id)
                val capabilities = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                    ?: IntArray(0)
                capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT)
            }
        }.getOrDefault(false)

        return DepthCapabilityInfo(
            hasHardwareDepthSensor = hasDepthOutput,
            hasArCoreFeature = hasArCore
        )
    }
}
