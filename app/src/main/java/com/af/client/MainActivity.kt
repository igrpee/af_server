package com.af.client

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import android.content.Context
import android.net.wifi.WifiManager
import android.os.BatteryManager
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

class MainActivity : Activity() {

    lateinit var tv: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestAllPermissions()
        tv = TextView(this)
        tv.text = "Server running on 8080"
        setContentView(tv)

        startServer()
    }
    
    private fun requestAllPermissions() {
    val perms = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CAMERA,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.POST_NOTIFICATIONS
    )

    val toRequest = perms.filter {
        ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
    }

    if (toRequest.isNotEmpty()) {
        ActivityCompat.requestPermissions(this, toRequest.toTypedArray(), 1)
    }
    }

    private fun startServer() {
        thread {
            val server = ServerSocket(8080)
            while (true) {
                val client = server.accept()
                handle(client)
            }
        }
    }

    private fun handle(socket: Socket) {
        thread {
            val input = socket.getInputStream().bufferedReader()
            val output = socket.getOutputStream()

            val line = input.readLine() ?: return@thread
            val cmd = line.substringAfter("cmd=").substringBefore(" ")

            val result = when (cmd) {
                "battery" -> getBattery()
                "wifi" -> getWifi()
                else -> "unknown command"
            }

            val response = "HTTP/1.1 200 OK\r\n\r\n$result"
            output.write(response.toByteArray())
            output.flush()
            socket.close()
        }
    }

    private fun getBattery(): String {
        val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return "Battery: ${level}%"
    }

    private fun getWifi(): String {
        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        return if (wm.isWifiEnabled) "WiFi ON" else "WiFi OFF"
    }
}
