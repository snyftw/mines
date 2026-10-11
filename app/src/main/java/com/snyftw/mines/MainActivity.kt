package com.snyftw.mines

import android.app.*
import android.os.*
import android.content.*
import android.graphics.*
import android.view.*
import android.widget.*
import android.provider.Settings
import android.net.Uri
import android.media.projection.MediaProjectionManager
import android.content.pm.PackageManager

class MainActivity : Activity() {
    private val captureCode = 921
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply { orientation = 1; setPadding(36,48,36,24) }
        fun button(text: String, action: () -> Unit) { layout.addView(Button(this).apply { this.text = text; setOnClickListener { action() } }) }
        layout.addView(TextView(this).apply { text = "Mines Assistant V2"; textSize = 26f })
        layout.addView(TextView(this).apply { text = "Floating tile tracker with optional screen reading. Suggestions do not predict mines."; textSize = 16f })
        button("1. Allow floating window") {
            if (!Settings.canDrawOverlays(this)) startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            else Toast.makeText(this, "Overlay already allowed", Toast.LENGTH_SHORT).show()
        }
        button("2. Start floating assistant") {
            if (Settings.canDrawOverlays(this)) startService(Intent(this, OverlayService::class.java))
            else Toast.makeText(this, "Allow overlay first", Toast.LENGTH_LONG).show()
        }
        button("3. Start screen reading") {
            val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(mgr.createScreenCaptureIntent(), captureCode)
        }
        button("Stop assistant") { stopService(Intent(this, OverlayService::class.java)); stopService(Intent(this, CaptureService::class.java)) }
        setContentView(layout)
    }
    @Deprecated("Deprecated in Android")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == captureCode && resultCode == RESULT_OK && data != null) {
            val intent = Intent(this, CaptureService::class.java).putExtra("resultCode", resultCode).putExtra("resultData", data)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
        }
    }
}
