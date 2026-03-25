package com.reticulum.mesh

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.*
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.io.ByteArrayOutputStream

class MainActivity : Activity() {
    private lateinit var deviceSpinner: Spinner
    private lateinit var startBtn: Button
    private lateinit var announceBtn: Button
    private lateinit var sendBtn: Button
    private lateinit var msgInput: EditText
    private lateinit var destInput: EditText
    private lateinit var statusText: TextView
    private var selectedImageBytes: ByteArray? = null
    private var pairedDevices = listOf<BluetoothDevice>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Python.isStarted()) Python.start(AndroidPlatform(this))

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
        }

        statusText = TextView(this).apply { text = "Mesh Status: Offline"; textSize = 16f }
        
        // --- Connection Section ---
        deviceSpinner = Spinner(this)
        startBtn = Button(this).apply { text = "Connect RNode" }
        
        // --- Messaging Section (Hidden until connected) ---
        destInput = EditText(this).apply { hint = "Destination Hex Hash"; visibility = android.view.View.GONE }
        msgInput = EditText(this).apply { hint = "Message Text"; visibility = android.view.View.GONE }
        val attachBtn = Button(this).apply { text = "Attach Image"; visibility = android.view.View.GONE }
        sendBtn = Button(this).apply { text = "Send Message"; visibility = android.view.View.GONE }
        announceBtn = Button(this).apply { text = "Manual Announce"; visibility = android.view.View.GONE }

        startBtn.setOnClickListener {
            val selectedPos = deviceSpinner.selectedItemPosition
            if (selectedPos in pairedDevices.indices) startMesh(pairedDevices[selectedPos])
        }

        attachBtn.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(intent, 102)
        }

        sendBtn.setOnClickListener {
            val dest = destInput.text.toString()
            val msg = msgInput.text.toString()
            if (dest.length == 20 || dest.length == 32) { // Basic RNS hash length check
                sendMessage(dest, msg)
            } else {
                Toast.makeText(this, "Invalid Destination Hash", Toast.LENGTH_SHORT).show()
            }
        }

        announceBtn.setOnClickListener {
            val py = Python.getInstance()
            py.getModule("reticulum_wrapper").callAttr("get_instance").callAttr("announce_now")
            Toast.makeText(this, "Announce Sent!", Toast.LENGTH_SHORT).show()
        }

        layout.addView(statusText)
        layout.addView(deviceSpinner)
        layout.addView(startBtn)
        layout.addView(destInput)
        layout.addView(msgInput)
        layout.addView(attachBtn)
        layout.addView(sendBtn)
        layout.addView(announceBtn)
        setContentView(layout)

        checkPermissionsAndLoadDevices()
    }

    private fun startMesh(device: BluetoothDevice) {
        startBtn.text = "Connecting..."
        startBtn.isEnabled = false
        Thread {
            val bridge = KotlinRNodeBridge(device)
            if (bridge.connect()) {
                val py = Python.getInstance()
                val wrapper = py.getModule("reticulum_wrapper")
                val instance = wrapper.callAttr("get_instance", filesDir.absolutePath)
                instance.callAttr("set_bridge", bridge)
                val myHash = instance.callAttr("start_lxmf", "Android Node").toString()
                
                runOnUiThread {
                    statusText.text = "Online. My Hash: " + myHash
                    startBtn.visibility = android.view.View.GONE
                    deviceSpinner.visibility = android.view.View.GONE
                    destInput.visibility = android.view.View.VISIBLE
                    msgInput.visibility = android.view.View.VISIBLE
                    sendBtn.visibility = android.view.View.VISIBLE
                    announceBtn.visibility = android.view.View.VISIBLE
                    findViewById<Button>(sendBtn.id - 1)?.let { it.visibility = android.view.View.VISIBLE } // Hack for attachBtn
                    Toast.makeText(this, "Mesh Ready!", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun sendMessage(dest: String, msg: String) {
        Thread {
            val py = Python.getInstance()
            val success = py.getModule("reticulum_wrapper").callAttr("get_instance")
                .callAttr("send_message", dest, msg, selectedImageBytes).toBoolean()
            runOnUiThread {
                if (success) {
                    Toast.makeText(this, "Sent!", Toast.LENGTH_SHORT).show()
                    msgInput.setText(""); selectedImageBytes = null
                } else {
                    Toast.makeText(this, "Send Failed", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 102 && resultCode == RESULT_OK && data != null) {
            val uri = data.data ?: return
            processImage(uri)
        }
    }

    private fun processImage(uri: Uri) {
        val inputStream = contentResolver.openInputStream(uri)
        val original = BitmapFactory.decodeStream(inputStream)
        
        // CRITICAL for LoRa: Resize to 300px and compress heavily (~8KB)
        val scaled = Bitmap.createScaledBitmap(original, 300, 300, true)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.WEBP, 30, out)
        selectedImageBytes = out.toByteArray()
        Toast.makeText(this, "Image Ready (" + (selectedImageBytes!!.size / 1024) + " KB)", Toast.LENGTH_SHORT).show()
    }

    private fun checkPermissionsAndLoadDevices() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (permissions.any { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }) {
            requestPermissions(permissions, 101)
        } else { loadPairedDevices() }
    }

    private fun loadPairedDevices() {
        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        pairedDevices = btManager.adapter.bondedDevices.toList()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, pairedDevices.map { it.name })
        deviceSpinner.adapter = adapter
    }
}
