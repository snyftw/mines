package com.snyftw.mines

import android.app.Service
import android.content.*
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.*
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var panel: LinearLayout
    private lateinit var params: WindowManager.LayoutParams
    private var current = mutableSetOf<Int>()
    private var mines = 3
    private var rounds = 0
    private lateinit var status: TextView
    private lateinit var history: TextView
    private lateinit var grid: GridLayout
    private val prefs by lazy { getSharedPreferences("mines_v2", MODE_PRIVATE) }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        params = WindowManager.LayoutParams(310, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START; x = 30; y = 170 }
        panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(12,12,12,12); setBackgroundColor(Color.rgb(23,31,43)) }
        val heading = TextView(this).apply { text = "☰  MINES V2     ✕"; textSize = 18f; setTextColor(Color.WHITE); setPadding(6,6,6,12) }
        panel.addView(heading)
        heading.setOnTouchListener(object : View.OnTouchListener {
            var sx=0; var sy=0; var tx=0f; var ty=0f
            override fun onTouch(v: View, event: android.view.MotionEvent): Boolean {
                when(event.action) {
                    MotionEvent.ACTION_DOWN -> { sx=params.x; sy=params.y; tx=event.rawX; ty=event.rawY; return true }
                    MotionEvent.ACTION_MOVE -> { params.x=sx+(event.rawX-tx).toInt(); params.y=sy+(event.rawY-ty).toInt(); wm.updateViewLayout(panel,params); return true }
                }
                return true
            }
        })
        status = TextView(this).apply { setTextColor(Color.WHITE); textSize=14f }
        history = TextView(this).apply { setTextColor(Color.LTGRAY); textSize=12f }
        panel.addView(status)
        panel.addView(history)
        grid = GridLayout(this).apply { columnCount=5 }
        panel.addView(grid)
        val buttons = LinearLayout(this)
        fun add(text: String, fn: () -> Unit) { buttons.addView(Button(this).apply { this.text=text; textSize=10f; setOnClickListener { fn() } }, LinearLayout.LayoutParams(0, 100, 1f)) }
        add("Suggest") { suggest() }
        add("Cash out") { endRound("cashout") }
        add("Bomb") { endRound("bomb") }
        add("Reset") { current.clear(); draw() }
        panel.addView(buttons)
        wm.addView(panel,params)
        draw()
    }
    private fun suggest() {
        val remaining=(1..25).filter { it !in current }
        if (remaining.isEmpty()) return
        val n=remaining.random(Random.Default)
        status.text="Random suggestion: tile $n (not predictive)"
    }
    private fun endRound(outcome: String) {
        val arr=JSONArray(prefs.getString("rounds","[]"))
        arr.put(JSONObject().put("outcome",outcome).put("selected",JSONArray(current.sorted())).put("mines",mines).put("timestamp",System.currentTimeMillis()))
        prefs.edit().putString("rounds",arr.toString()).apply()
        rounds=arr.length()
        current.clear(); draw()
    }
    private fun draw() {
        val arr=JSONArray(prefs.getString("rounds","[]"))
        status.text="Tap revealed safe tiles • Mines: $mines"
        history.text="Saved rounds: ${arr.length()} • Screen reading: experimental"
        grid.removeAllViews()
        for (n in 1..25) {
            val b=Button(this).apply {
                text=if(n in current) "✓" else "$n"
                textSize=11f; minWidth=0; minimumWidth=0; minHeight=0; minimumHeight=0
                setPadding(0,0,0,0)
                setOnClickListener { if(n in current) current.remove(n) else current.add(n); draw() }
            }
            grid.addView(b, GridLayout.LayoutParams().apply { width=54; height=62; setMargins(2,2,2,2) })
        }
    }
    override fun onDestroy() { if(::panel.isInitialized) wm.removeView(panel); super.onDestroy() }
}
