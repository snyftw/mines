package com.snyftw.mines

import android.app.Service
import android.content.*
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
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
    private lateinit var status: TextView
    private lateinit var history: TextView
    private lateinit var suggestion: TextView
    private val gems = mutableSetOf<Int>()
    private var suggested: Int? = null
    private val prefs by lazy { getSharedPreferences("mines_v2", MODE_PRIVATE) }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val found = intent?.getIntegerArrayListExtra("gems") ?: return
            gems.clear(); gems.addAll(found)
            render()
        }
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        params = WindowManager.LayoutParams(255, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.TOP or Gravity.START; x = 10; y = 150
            }
        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 8, 12, 8)
            setBackgroundColor(Color.rgb(21, 31, 44))
        }
        val heading = TextView(this).apply { text = "☰ MINES V2.1"; textSize = 15f; setTextColor(Color.WHITE) }
        panel.addView(heading)
        heading.setOnTouchListener(object : View.OnTouchListener {
            var x = 0; var y = 0; var touchX = 0f; var touchY = 0f
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> { x = params.x; y = params.y; touchX = event.rawX; touchY = event.rawY; return true }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = x + (event.rawX - touchX).toInt()
                        params.y = y + (event.rawY - touchY).toInt()
                        wm.updateViewLayout(panel, params); return true
                    }
                }
                return true
            }
        })
        status = TextView(this).apply { setTextColor(Color.WHITE); textSize = 12f }
        suggestion = TextView(this).apply { setTextColor(Color.GREEN); textSize = 13f }
        history = TextView(this).apply { setTextColor(Color.LTGRAY); textSize = 11f }
        panel.addView(status); panel.addView(suggestion); panel.addView(history)
        val actions = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun action(text: String, fn: () -> Unit) {
            actions.addView(Button(this).apply { this.text = text; textSize = 10f; minHeight = 0; minimumHeight = 0; setOnClickListener { fn() } },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 65))
        }
        action("Random unopened tile") {
            val options = (1..25).filter { it !in gems }
            suggested = options.randomOrNull(Random.Default)
            render()
        }
        action("Record cash-out (manual)") { save("cashout") }
        action("Record bomb (manual)") { save("bomb") }
        action("Clear detected tiles") { gems.clear(); suggested = null; render() }
        panel.addView(actions)
        wm.addView(panel, params)
        val filter = IntentFilter("com.snyftw.mines.FRAME_RESULT")
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(receiver, filter)
        render()
    }
    private fun save(outcome: String) {
        val rounds = JSONArray(prefs.getString("rounds", "[]"))
        rounds.put(JSONObject().put("outcome", outcome)
            .put("selected", JSONArray(gems.sorted()))
            .put("mines", 3).put("timestamp", System.currentTimeMillis())
            .put("detection", "experimental"))
        prefs.edit().putString("rounds", rounds.toString()).apply()
        gems.clear(); suggested = null; render()
    }
    private fun render() {
        status.text = "Visible gems: ${gems.size} (experimental)"
        suggestion.text = suggested?.let { "Random tile: $it (not predictive)" } ?: "No tile selected"
        history.text = "Saved rounds: ${JSONArray(prefs.getString("rounds", "[]")).length()}"
    }
    override fun onDestroy() {
        try { unregisterReceiver(receiver) } catch (_: Exception) { }
        if (::panel.isInitialized) wm.removeView(panel)
        super.onDestroy()
    }
}
