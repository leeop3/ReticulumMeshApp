package com.reticulum.mesh

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Python
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

        // Create a classic Android UI Programmatically
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 60, 60, 60)
        }

        val statusText = TextView(this).apply {
            text = "Reticulum Mesh Node\n\nEnsure your RNode is paired in your phone's Bluetooth settings first."
            textSize = 18f
            setPadding(0, 0, 0, 60)
        }

        val startBtn = Button(this).apply {
            text = "Start RNode (433MHz)"
            setOnClickListener {
                startMesh()
                text = "RNode Started! Check Logcat."
                isEnabled = false
            }
        }

        layout.addView(statusText)
        layout.addView(startBtn)

        setContentView(layout)
    }

    private fun startMesh() {
        val py = Python.getInstance()
        val wrapper = py.getModule("reticulum_wrapper")
        val instance = wrapper.callAttr("get_instance", filesDir.absolutePath)
        instance.callAttr("start_lxmf", "Android User")
    }
}
