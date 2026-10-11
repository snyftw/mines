package com.snyftw.mines

import android.app.*
import android.content.*
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*

class CaptureService : Service() {
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private val handler = Handler(Looper.getMainLooper())
    private var lastProcessed = 0L
    private var lastSignature = ""
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val channel = "mines_capture"
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(channel, "Mines screen capture", NotificationManager.IMPORTANCE_LOW))
        val notification = Notification.Builder(this, channel)
            .setContentTitle("Mines V2.1 screen reader")
            .setContentText("Local visual detection active; stop in Mines Assistant")
            .setSmallIcon(android.R.drawable.ic_menu_view).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(11, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else startForeground(11, notification)
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33) intent?.getParcelableExtra("resultData", Intent::class.java)
            else @Suppress("DEPRECATION") intent?.getParcelableExtra("resultData")
        if (data == null) { stopSelf(); return START_NOT_STICKY }
        val code = intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        if (code != Activity.RESULT_OK) { stopSelf(); return START_NOT_STICKY }
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = manager.getMediaProjection(code, data)
        val metrics = resources.displayMetrics
        reader = ImageReader.newInstance(metrics.widthPixels, metrics.heightPixels, PixelFormat.RGBA_8888, 2)
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { stopSelf() }
        }, handler)
        reader?.setOnImageAvailableListener({ source ->
            val image = source.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                if (SystemClock.elapsedRealtime() - lastProcessed < 600L) return@setOnImageAvailableListener
                lastProcessed = SystemClock.elapsedRealtime()
                val plane = image.planes[0]
                val buffer = plane.buffer
                val stride = plane.rowStride / plane.pixelStride
                val bitmap = Bitmap.createBitmap(stride, image.height, Bitmap.Config.ARGB_8888)
                bitmap.copyPixelsFromBuffer(buffer)
                analyze(bitmap, image.width, image.height)
                bitmap.recycle()
            } catch (_: Exception) { /* Keep capture alive on unusual frame formats. */ }
            finally { image.close() }
        }, handler)
        display = projection?.createVirtualDisplay("MinesV21", metrics.widthPixels, metrics.heightPixels,
            metrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader?.surface, null, null)
        return START_NOT_STICKY
    }
    private fun analyze(bitmap: Bitmap, width: Int, height: Int) {
        // Calibration for S24 Ultra Chrome portrait screenshot. Detects ONLY visibly green gem icons.
        // Positions are fractions of the captured display and require the board to remain in place.
        val xs = doubleArrayOf(.15, .31, .50, .675, .855)
        val ys = doubleArrayOf(.25, .335, .418, .502, .585)
        val gems = mutableListOf<Int>()
        for (r in 0..4) for (c in 0..4) {
            val cx = (xs[c] * width).toInt()
            val cy = (ys[r] * height).toInt()
            var green = 0
            for (dx in -2..2) for (dy in -2..2) {
                val x = (cx + dx * (width / 170).coerceAtLeast(1)).coerceIn(0, width - 1)
                val y = (cy + dy * (height / 330).coerceAtLeast(1)).coerceIn(0, height - 1)
                val p = bitmap.getPixel(x, y)
                val rr = android.graphics.Color.red(p)
                val gg = android.graphics.Color.green(p)
                val bb = android.graphics.Color.blue(p)
                if (gg > 100 && gg > rr * 1.45 && gg > bb * 1.15) green++
            }
            if (green >= 3) gems.add(r * 5 + c + 1)
        }
        val signature = gems.joinToString(",")
        // Always broadcast detection results for debugging
        lastSignature = signature
        sendBroadcast(Intent("com.snyftw.mines.FRAME_RESULT").setPackage(packageName)
            .putIntegerArrayListExtra("gems", ArrayList(gems))
            .putExtra("status", "Visual gem detection (experimental)"))
    }
    override fun onDestroy() {
        reader?.setOnImageAvailableListener(null, null)
        display?.release(); reader?.close(); projection?.stop()
        super.onDestroy()
    }
}
