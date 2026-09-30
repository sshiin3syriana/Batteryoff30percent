package com.example.batteryoff

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import rikka.shizuku.Shizuku

class MainActivity : Activity() {

    private lateinit var status: TextView
    private val listener = Shizuku.OnRequestPermissionResultListener { _, _ -> refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        status = TextView(this).apply { textSize = 16f; setPadding(0, 0, 0, pad) }
        root.addView(status)

        fun btn(t: String, a: () -> Unit) =
            root.addView(Button(this).apply { text = t; setOnClickListener { a() } })

        btn("1. ขออนุญาต Shizuku") { requestShizuku() }
        btn("2. อนุญาตการแจ้งเตือน") {
            if (Build.VERSION.SDK_INT >= 33)
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        btn("3. ปิด Battery optimization") {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName"))
            )
        }
        btn("4. เริ่มเฝ้าดูแบต") {
            startForegroundService(Intent(this, BatteryService::class.java)); refresh()
        }
        btn("หยุดเฝ้าดู") { stopService(Intent(this, BatteryService::class.java)); refresh() }

        setContentView(ScrollView(this).apply { addView(root) })
        Shizuku.addRequestPermissionResultListener(listener)
    }

    override fun onResume() { super.onResume(); refresh() }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(listener)
        super.onDestroy()
    }

    private fun requestShizuku() {
        if (!Shizuku.pingBinder()) {
            Toast.makeText(this, "Shizuku ยังไม่ทำงาน เปิดผ่าน Wireless debugging ก่อน", Toast.LENGTH_LONG).show()
            return
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED)
            Toast.makeText(this, "ได้สิทธิ์แล้ว", Toast.LENGTH_SHORT).show()
        else Shizuku.requestPermission(100)
    }

    private fun refresh() {
        val running = Shizuku.pingBinder()
        val granted = running && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        status.text = "Shizuku: " + (if (running) "ทำงานอยู่" else "ไม่ทำงาน") +
            "\nสิทธิ์: " + (if (granted) "ได้รับแล้ว" else "ยังไม่ได้รับ") +
            "\nเกณฑ์ปิดเครื่อง: แบต ≤ ${BatteryService.THRESHOLD}% และไม่ได้ชาร์จ"
    }
}
