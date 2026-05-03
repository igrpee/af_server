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
import kotlin.concurrent.thread
import java.net.ServerSocket
import java.net.Socket
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class MainActivity : Activity() {

    lateinit var tv: TextView
    lateinit var inputCode: EditText
    lateinit var runButton: Button

    var lastResult: String = "No result yet"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestAllPermissions()

        tv = TextView(this)
        inputCode = EditText(this).apply { hint = "Enter Kotlin code here" }
        runButton = Button(this).apply { text = "Run Code" }

        // Слушатель кнопки
        runButton.setOnClickListener {
            val code = inputCode.text.toString()
            lastResult = safeEvalKotlin(code)
            tv.text = lastResult
        }

        setContentView(tv) // можно сделать LinearLayout и добавить inputCode + runButton
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
                "result" -> lastResult
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

    // -----------------------
    // Основной eval engine
    private fun safeEvalKotlin(code: String): String {
        // блокировка опасных команд
        val forbidden = listOf(
            "Runtime.getRuntime",
            "System.exit",
            "java.io.File",
            "java.net.Socket",
            "java.net.URL"
        )
        forbidden.forEach {
            if (it in code) return "Forbidden usage detected: $it"
        }

        return try {
            // Создаем Kotlin скрипт в виде функции
            val script = """
                fun run(): String {
                    ${code}
                }
                run()
            """.trimIndent()

            val outputStream = ByteArrayOutputStream()
            val compiler = K2JVMCompiler()
            val args = arrayOf("-script", "-") // компилируем как скрипт

            compiler.exec(
                PrintingMessageCollector(PrintStream(outputStream), MessageRenderer.PLAIN_RELATIVE_PATHS, false),
                args
            )

            outputStream.toString()
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}
