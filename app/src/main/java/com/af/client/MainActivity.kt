package com.af.client

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
        tv = TextView(this)
        tv.text = "Server running on 8080"
        setContentView(tv)

        startServer()
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
