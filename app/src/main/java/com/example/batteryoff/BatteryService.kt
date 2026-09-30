package com.example.batteryoff

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import rikka.shizuku.Shizuku

class BatteryService : Service() {

    companion object {
        const val THRESHOLD = 30
        const val GRACE_MS = 15_000L
        const val CH_MONITOR = "monitor"
        const val CH_ALERT = "alert"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var pending = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = evaluate(i)
    }

    private val shutdownTask = Runnable {
        pending = false
        val i = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return@Runnable
        if (isLow(i) && !isCharging(i)) {
            if (!shutdown()) notifyAlert("ปิดเครื่องไม่สำเร็จ", "Shizuku ไม่ทำงานหรือยังไม่ได้อนุญาต กรุณาปิดเครื่องเอง")
        }
    }

    private fun level(i: Intent): Int {
        val l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val s = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        return if (l < 0) 100 else l * 100 / s
    }

    private fun isLow(i: Intent) = level(i) <= THRESHOLD
    private fun isCharging(i: Intent) = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0

    private fun evaluate(i: Intent) {
        val trigger = isLow(i) && !isCharging(i)
        if (trigger && !pending) {
            pending = true
            notifyAlert("แบตเหลือ ${level(i)}%", "จะปิดเครื่องใน ${GRACE_MS / 1000} วินาที (เสียบสายชาร์จเพื่อยกเลิก)")
            handler.postDelayed(shutdownTask, GRACE_MS)
        } else if (!trigger && pending) {
            pending = false
            handler.removeCallbacks(shutdownTask)
            getSystemService(NotificationManager::class.java).cancel(2)
        }
    }

    private fun shutdown(): Boolean = try {
        if (!Shizuku.pingBinder() ||
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED
        ) false
        else {
            val m = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java, Array<String>::class.java, String::class.java
            )
            m.isAccessible = true
            m.invoke(null, arrayOf("sh", "-c", "reboot -p"), null, null)
            true
        }
    } catch (t: Throwable) { false }

    private fun notifyAlert(title: String, text: String) {
        val n = Notification.Builder(this, CH_ALERT)
            .setSmallIcon(android.R.drawable.ic_lock_idle_low_battery)
            .setContentTitle(title).setContentText(text).build()
        getSystemService(NotificationManager::class.java).notify(2, n)
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_MONITOR, "Battery monitor", NotificationManager.IMPORTANCE_MIN))
        nm.createNotificationChannel(NotificationChannel(CH_ALERT, "Battery alert", NotificationManager.IMPORTANCE_HIGH))

        val n = Notification.Builder(this, CH_MONITOR)
            .setSmallIcon(android.R.drawable.ic_lock_idle_low_battery)
            .setContentTitle("Battery monitor กำลังทำงาน").build()
        if (Build.VERSION.SDK_INT >= 34)
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else
            startForeground(1, n)

        registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let { evaluate(it) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(shutdownTask)
        unregisterReceiver(receiver)
        super.onDestroy()
    }
}
