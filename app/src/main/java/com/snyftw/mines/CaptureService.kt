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
        // Calibrated from S24 Ultra portrait screenshot, 709 x 1536.
        // Board centers: x=107,230,354,478,602; y=385,512,640,767,895.
        val xs = doubleArrayOf(107.0/709,230.0/709,354.0/709,478.0/709,602.0/709)
        val ys = doubleArrayOf(385.0/1536,512.0/1536,640.0/1536,767.0/1536,895.0/1536)
        val gems = arrayListOf<Int>()
        val bombs = arrayListOf<Int>()
        val selected = arrayListOf<Int>()
        for (r in 0..4) for (c in 0..4) {
            val cx=(xs[c]*width).toInt(); val cy=(ys[r]*height).toInt()
            var green=0; var red=0; var brightGreen=0; var brightRed=0
            for (dy in -18..18 step 3) for (dx in -23..23 step 3) {
                val x=(cx+dx*width/709).coerceIn(0,width-1)
                val y=(cy+dy*height/1536).coerceIn(0,height-1)
                val color=bitmap.getPixel(x,y)
                val rr=android.graphics.Color.red(color)
                val gg=android.graphics.Color.green(color)
                val bb=android.graphics.Color.blue(color)
                if (gg>42 && gg>rr*1.30 && gg>bb*1.08) {
                    green++
                    if (gg>135 && gg>rr*1.7) brightGreen++
                }
                if (rr>65 && rr>gg*1.45 && rr>bb*1.25) {
                    red++
                    if (rr>155 && rr>gg*1.9) brightRed++
                }
            }
            val n=r*5+c+1
            if (red>=6 && red>green/2) {
                bombs.add(n)
                if (brightRed>=5) selected.add(n)
            } else if (green>=8) {
                gems.add(n)
                if (brightGreen>=8) selected.add(n)
            }
        }
        val complete=(gems.size+bombs.size==25 && bombs.isNotEmpty())
        sendBroadcast(Intent("com.snyftw.mines.FRAME_RESULT").setPackage(packageName)
            .putIntegerArrayListExtra("gems",gems)
            .putIntegerArrayListExtra("bombs",bombs)
            .putIntegerArrayListExtra("selected",selected)
            .putExtra("complete",complete)
            .putExtra("status",if(complete) "Full board detected" else "Scanning tiles: ${gems.size+ bombs.size}/25"))
    }
    override fun onDestroy() {
        reader?.setOnImageAvailableListener(null, null)
        display?.release(); reader?.close(); projection?.stop()
        super.onDestroy()
    }
}
