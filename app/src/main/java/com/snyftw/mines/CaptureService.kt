package com.snyftw.mines

import android.app.*
import android.content.*
import android.os.*
import android.media.projection.*
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.graphics.PixelFormat
import android.graphics.Bitmap
import android.os.IBinder

class CaptureService : Service() {
    private var projection: MediaProjection? = null
    private var display: android.hardware.display.VirtualDisplay? = null
    private var reader: ImageReader? = null
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val channelId="mines_capture"
        val nm=getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(channelId,"Mines screen capture",NotificationManager.IMPORTANCE_LOW))
        val notification=Notification.Builder(this,channelId).setContentTitle("Mines V2 screen capture").setContentText("Screen capture is active; stop it in Mines Assistant").setSmallIcon(android.R.drawable.ic_menu_view).build()
        if(Build.VERSION.SDK_INT>=29) startForeground(11,notification,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else startForeground(11,notification)
        val data: Intent? = if(Build.VERSION.SDK_INT>=33) intent?.getParcelableExtra("resultData",Intent::class.java) else intent?.getParcelableExtra("resultData")
        if(data==null) { stopSelf(); return START_NOT_STICKY }
        val manager=getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection=manager.getMediaProjection(intent?.getIntExtra("resultCode",Activity.RESULT_CANCELED),data)
        val metrics=resources.displayMetrics
        reader=ImageReader.newInstance(metrics.widthPixels,metrics.heightPixels,PixelFormat.RGBA_8888,2)
        projection?.registerCallback(object: MediaProjection.Callback() { override fun onStop() { stopSelf() } },Handler(Looper.getMainLooper()))
        display=projection?.createVirtualDisplay("MinesV2",metrics.widthPixels,metrics.heightPixels,metrics.densityDpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader?.surface,null,null)
        // Frames are intentionally not stored or uploaded. Recognition must be calibrated against actual gameplay screenshots.
        return START_NOT_STICKY
    }
    override fun onDestroy() { display?.release(); reader?.close(); projection?.stop(); super.onDestroy() }
}
