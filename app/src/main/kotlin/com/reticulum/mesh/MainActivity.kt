package com.reticulum.mesh

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Python
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

        setContent {
            Column {
                Text(text = "Reticulum Mesh Node")
                Button(onClick = { startMesh() }) {
                    Text("Start RNode (433MHz)")
                }
            }
        }
    }

    private fun startMesh() {
        val py = Python.getInstance()
        val wrapper = py.getModule("reticulum_wrapper")
        val instance = wrapper.callAttr("get_instance", filesDir.absolutePath)
        instance.callAttr("start_lxmf", "Android User")
    }
}
