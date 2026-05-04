package com.af.client

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.core.app.ActivityCompat
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

// Kotlin Scripting imports
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvm.*
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost

object RuntimeState {
    var lastResult: String = "empty"
}

class MainActivity : Activity() {

    lateinit var tv: TextView
    lateinit var editCode: EditText
    lateinit var btnRun: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestAllPermissions()

        tv = TextView(this)
        tv.text = "Server running on 8080"

        editCode = EditText(this)
        editCode.hint = "Enter Kotlin code here"
        editCode.minLines = 5

        btnRun = Button(this)
        btnRun.text = "Execute"
        btnRun.setOnClickListener {
            val code = editCode.text.toString()
            thread {
                val result = safeEval(code)
                RuntimeState.lastResult = result
            }
        }

        val layout = android.widget.LinearLayout(this)
        layout.orientation = android.widget.LinearLayout.VERTICAL
        layout.addView(tv)
        layout.addView(editCode)
        layout.addView(btnRun)

        setContentView(layout)

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
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE
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
                "result" -> RuntimeState.lastResult
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
        return "Battery: $level%"
    }

    private fun getWifi(): String {
        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        return if (wm.isWifiEnabled) "WiFi ON" else "WiFi OFF"
    }

    // --------- Kotlin safe eval ---------
    private fun safeEval(code: String): String {
        val host = BasicJvmScriptingHost()

        // запрещаем опасные слова
        val forbidden = listOf("Runtime.getRuntime", "System.exit", "java.io.File", "java.net.Socket", "java.net.URL")
        forbidden.forEach {
            if (it in code) return "Forbidden usage detected: $it"
        }

        val compilationConfig = ScriptCompilationConfiguration {
            jvm {
                dependenciesFromCurrentContext(wholeClasspath = true)
            }
            defaultImports("kotlin.math.*")
        }

        val evaluationConfig = ScriptEvaluationConfiguration {
            providedProperties(
                "getBatterySafe" to { getBattery() },
                "getWifiSafe" to { getWifi() }
            )
        }

        return try {
            val res = host.eval(code.toScriptSource(), compilationConfig, evaluationConfig)
            res.valueOrNull()?.returnValue?.toString() ?: "ok"
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}
