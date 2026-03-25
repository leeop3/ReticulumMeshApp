package com.reticulum.mesh

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

class MainActivity : Activity() {
    private lateinit var deviceSpinner: Spinner
    private lateinit var startBtn: Button
    private var pairedDevices = listOf<BluetoothDevice>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 60, 60, 60)
        }

        val statusText = TextView(this).apply {
            text = "Reticulum Mesh Node\n\nSelect your paired RNode:"
            textSize = 18f
            setPadding(0, 0, 0, 40)
        }

        deviceSpinner = Spinner(this).apply {
            setPadding(0, 0, 0, 40)
        }

        startBtn = Button(this).apply {
            text = "Connect & Start RNode"
            isEnabled = false
            setOnClickListener {
                val selectedPos = deviceSpinner.selectedItemPosition
                if (selectedPos in pairedDevices.indices) {
                    val device = pairedDevices[selectedPos]
                    startMesh(device)
                    text = "Connecting..."
                    isEnabled = false
                }
            }
        }

        layout.addView(statusText)
        layout.addView(deviceSpinner)
        layout.addView(startBtn)
        setContentView(layout)

        checkPermissionsAndLoadDevices()
    }

    private fun checkPermissionsAndLoadDevices() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        val missing = permissions.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), 101)
        } else {
            loadPairedDevices()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            loadPairedDevices()
        } else {
            Toast.makeText(this, "Bluetooth permissions required!", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadPairedDevices() {
        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = btManager.adapter
        if (adapter == null || !adapter.isEnabled) {
            Toast.makeText(this, "Please enable Bluetooth!", Toast.LENGTH_LONG).show()
            return
        }

        try {
            pairedDevices = adapter.bondedDevices.toList()
            val deviceNames = pairedDevices.map { it.name + " (" + it.address + ")" }
            
            val arrayAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, deviceNames)
            deviceSpinner.adapter = arrayAdapter
            
            if (pairedDevices.isNotEmpty()) {
                startBtn.isEnabled = true
            } else {
                Toast.makeText(this, "No paired devices found.", Toast.LENGTH_LONG).show()
            }
        } catch (e: SecurityException) {
            Toast.makeText(this, "Permission denied loading devices", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startMesh(device: BluetoothDevice) {
        val bridge = KotlinRNodeBridge(device)
        
        Thread {
            val success = bridge.connect()
            runOnUiThread {
                if (success) {
                    Toast.makeText(this, "BT Connected! Starting Reticulum...", Toast.LENGTH_SHORT).show()
                    
                    // Pass the bridge to Python
                    val py = Python.getInstance()
                    val wrapper = py.getModule("reticulum_wrapper")
                    val instance = wrapper.callAttr("get_instance", filesDir.absolutePath)
                    
                    // Handshake between Kotlin and Python
                    instance.callAttr("set_bridge", bridge)
                    instance.callAttr("start_lxmf", "Android Node")
                    
                    startBtn.text = "Mesh Online!"
                } else {
                    Toast.makeText(this, "Connection Failed! Is the RNode on?", Toast.LENGTH_LONG).show()
                    startBtn.isEnabled = true
                    startBtn.text = "Connect & Start RNode"
                }
            }
        }.start()
    }
}
