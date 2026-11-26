package com.jcm.jarvisos

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.view.accessibility.AccessibilityEvent

class JarvisControlService : AccessibilityService() {

    companion object {
        var instance: JarvisControlService? = null
        var isTorchOn = false

        fun isOnline(context: Context): Boolean {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }

        fun analyzeFullDevice(context: Context): String {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val battery = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val stat = StatFs(Environment.getDataDirectory().path)
            val freeGb = (stat.availableBlocksLong * stat.blockSizeLong) / (1024 * 1024 * 1024)
            val totalGb = (stat.blockCountLong * stat.blockSizeLong) / (1024 * 1024 * 1024)
            val netStatus = if (isOnline(context)) "ONLINE (Cloud Active)" else "OFFLINE (Local Core)"
            return "સર, સિસ્ટમ સ્કેન પૂર્ણ: બેટરી $battery%, ફ્રી સ્ટોરેજ ${freeGb}GB / ${totalGb}GB, અને નેટવર્ક $netStatus પર કાર્યરત છે."
        }

        fun toggleTorch(context: Context, turnOn: Boolean): String {
            return try {
                val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val id = cm.cameraIdList[0]
                cm.setTorchMode(id, turnOn)
                isTorchOn = turnOn
                if (turnOn) "ફ્લેશલાઇટ ચાલુ કરી દીધી છે, સર." else "ફ્લેશલાઇટ બંધ કરી દીધી છે, સર."
            } catch (e: Exception) {
                "ફ્લેશલાઇટ કંટ્રોલ કરવામાં ટેકનિકલ અડચણ આવી છે."
            }
        }

        fun openAppByName(context: Context, spokenText: String): Boolean {
            val pm = context.packageManager
            val apps = pm.getInstalledApplications(0)
            val query = spokenText.lowercase()
            for (app in apps) {
                val label = pm.getApplicationLabel(app).toString().lowercase()
                if (label.length > 2 && query.contains(label)) {
                    val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(launchIntent)
                        return true
                    }
                }
            }
            return false
        }

        fun openSystemSettings(context: Context, action: String) {
            val intent = Intent(action).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(intent)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }
}
