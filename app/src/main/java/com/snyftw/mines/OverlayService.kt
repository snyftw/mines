package com.snyftw.mines

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.random.Random

class OverlayService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var panel: LinearLayout
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var heading: TextView
    private lateinit var suggestion: TextView
    private lateinit var status: TextView
    private lateinit var nextButton: TextView

    private val gems = mutableSetOf<Int>()
    private val bombs = mutableSetOf<Int>()
    private val selected = mutableSetOf<Int>()

    private var currentPick: Int? = null
    private var minimized = false

    private fun dp(n: Int): Int =
        (n * resources.displayMetrics.density).toInt()

    private fun text(value: String, size: Float): TextView =
        TextView(this).apply {
            this.text = value
            textSize = size
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(9), dp(8), dp(9))
        }

    private fun candidates(): List<Int> =
        (1..25).filter {
            it !in gems && it !in bombs && it !in selected
        }

    private fun updateDisplay() {
        val available = candidates()
        if (currentPick !in available) {
            currentPick = null
        }

        suggestion.text =
            currentPick?.let { "TILE $it" } ?: "READY"

        status.text =
            "${available.size} possible unopened tiles"

        nextButton.text =
            if (available.isEmpty()) "NO TILES" else "NEXT PICK"
    }

    private fun nextPick() {
        val available = candidates()
        val alternatives =
            available.filter { it != currentPick }

        currentPick =
            (if (alternatives.isNotEmpty()) alternatives
             else available).randomOrNull(Random.Default)

        updateDisplay()
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action !=
                "com.snyftw.mines.FRAME_RESULT") return

            fun tiles(key: String): Set<Int> =
                (intent.getIntegerArrayListExtra(key)
                    ?: arrayListOf())
                    .filter { it in 1..25 }
                    .toSet()

            gems.clear()
            gems.addAll(tiles("gems"))

            bombs.clear()
            bombs.addAll(tiles("bombs"))

            selected.clear()
            selected.addAll(tiles("selected"))

            updateDisplay()
        }
    }

    private val visibilityReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!::panel.isInitialized) return
            when (intent?.action) {
                "com.snyftw.mines.HIDE_OVERLAY" ->
                    panel.visibility = View.INVISIBLE
                "com.snyftw.mines.SHOW_OVERLAY" ->
                    panel.visibility = View.VISIBLE
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        wm = getSystemService(WINDOW_SERVICE) as WindowManager

        params = WindowManager.LayoutParams(
            dp(190),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(10)
            y = dp(100)
        }

        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(4), dp(6), dp(6))
            setBackgroundColor(Color.rgb(21, 31, 44))
        }

        heading = text("MINES LITE  −", 13f)
        suggestion = text("READY", 24f)
        status = text("Waiting for scan", 11f)
        nextButton = text("NEXT PICK", 14f).apply {
            setBackgroundColor(Color.rgb(32, 110, 190))
            setOnClickListener { nextPick() }
        }

        panel.addView(heading)
        panel.addView(suggestion)
        panel.addView(status)
        panel.addView(nextButton)

        heading.setOnClickListener {
            minimized = !minimized
            val visibility =
                if (minimized) View.GONE else View.VISIBLE

            suggestion.visibility = visibility
            status.visibility = visibility
            nextButton.visibility = visibility

            heading.text =
                if (minimized) "MINES LITE  +" else "MINES LITE  −"
        }

        heading.setOnTouchListener(object : View.OnTouchListener {
            private var startX = 0
            private var startY = 0
            private var touchX = 0f
            private var touchY = 0f
            private var moved = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = params.x
                        startY = params.y
                        touchX = event.rawX
                        touchY = event.rawY
                        moved = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - touchX
                        val dy = event.rawY - touchY

                        if (abs(dx) > dp(6) ||
                            abs(dy) > dp(6)) moved = true

                        if (moved) {
                            params.x = startX + dx.toInt()
                            params.y = startY + dy.toInt()
                            wm.updateViewLayout(panel, params)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) v.performClick()
                        return true
                    }
                }
                return true
            }
        })

        wm.addView(panel, params)

        val filter = IntentFilter(
            "com.snyftw.mines.FRAME_RESULT"
        )

        val visibilityFilter = IntentFilter().apply {
            addAction("com.snyftw.mines.HIDE_OVERLAY")
            addAction("com.snyftw.mines.SHOW_OVERLAY")
        }

        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
            registerReceiver(
                visibilityReceiver,
                visibilityFilter,
                RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(receiver, filter)
            @Suppress("DEPRECATION")
            registerReceiver(visibilityReceiver, visibilityFilter)
        }

        updateDisplay()
    }

    override fun onDestroy() {
        try { unregisterReceiver(receiver) }
        catch (_: Exception) {}

        try { unregisterReceiver(visibilityReceiver) }
        catch (_: Exception) {}

        if (::panel.isInitialized) {
            wm.removeView(panel)
        }

        super.onDestroy()
    }
}
