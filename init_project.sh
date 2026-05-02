#!/data/data/com.termux/files/usr/bin/bash

set -e

echo "[+] Creating Android project..."

mkdir -p app/src/main/java/com/af/client

# settings.gradle
cat > settings.gradle <<'EOF'
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "af_client"
include(":app")
EOF

# root build.gradle
cat > build.gradle <<'EOF'
plugins {
    id 'com.android.application' version '8.1.0' apply false
    id 'org.jetbrains.kotlin.android' version '1.9.0' apply false
}
EOF

# app/build.gradle
mkdir -p app
cat > app/build.gradle <<'EOF'
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
}

android {
    namespace "com.af.client"
    compileSdk 34

    defaultConfig {
        applicationId "com.af.client"
        minSdk 24
        targetSdk 34
        versionCode 1
        versionName "1.0"
    }

    buildTypes {
        debug {
            minifyEnabled false
        }
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
}
EOF

# Manifest
mkdir -p app/src/main
cat > app/src/main/AndroidManifest.xml <<'EOF'
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET"/>

    <application
        android:label="AF Server"
        android:usesCleartextTraffic="true">

        <activity
            android:name=".MainActivity"
            android:exported="true">

            <intent-filter>
                <action android:name="android.intent.action.MAIN"/>
                <category android:name="android.intent.category.LAUNCHER"/>
            </intent-filter>

        </activity>
    </application>
</manifest>
EOF

# MainActivity.kt (фикс кавычек 🔥)
cat > app/src/main/java/com/af/client/MainActivity.kt <<'EOF'
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
EOF

echo "[+] Done!"
