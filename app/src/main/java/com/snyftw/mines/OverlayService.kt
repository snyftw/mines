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
    private lateinit var suggestion: TextView
    private lateinit var history: TextView
    private lateinit var board: GridLayout
    private lateinit var details: LinearLayout
    private val gems = mutableSetOf<Int>()
    private val bombs = mutableSetOf<Int>()
    private val selected = mutableSetOf<Int>()
    private var suggested: Int? = null
    private var complete = false
    private var recognizedTiles = 0
    private var manualEdit = false
    private var collapsed = true
    private val prefs by lazy { getSharedPreferences("mines_v2", MODE_PRIVATE) }
    private val visibilityReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!::panel.isInitialized) return
            when (intent?.action) {
                "com.snyftw.mines.HIDE_OVERLAY" -> panel.visibility = View.INVISIBLE
                "com.snyftw.mines.SHOW_OVERLAY" -> panel.visibility = View.VISIBLE
            }
        }
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            if (manualEdit) return
            gems.clear(); gems.addAll(intent.getIntegerArrayListExtra("gems") ?: arrayListOf())
            bombs.clear(); bombs.addAll(intent.getIntegerArrayListExtra("bombs") ?: arrayListOf())
            selected.clear(); selected.addAll(intent.getIntegerArrayListExtra("selected") ?: arrayListOf())
            complete = intent.getBooleanExtra("complete", false)
            recognizedTiles = intent.getIntExtra("recognizedTiles", gems.size + bombs.size)
            if (intent.getBooleanExtra("newRoundResult", false)) saveAutomatic()
            render()
        }
    }
    override fun onBind(intent: Intent?): IBinder? = null
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun label(size: Float, color: Int = Color.WHITE) = TextView(this).apply {
        textSize = size; setTextColor(color); setPadding(dp(4), dp(3), dp(4), dp(3))
    }
    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        params = WindowManager.LayoutParams(dp(280), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START; x = dp(8); y = dp(82)
        }
        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(9),dp(8),dp(9),dp(8))
            setBackgroundColor(Color.rgb(21,31,44))
        }
        val heading = label(14f).apply { text = "☰ MINES V3.1 · Tap to expand" }
        panel.addView(heading)
        heading.setOnClickListener {
            collapsed = !collapsed
            details.visibility = if (collapsed) View.GONE else View.VISIBLE
            heading.text = if (collapsed) "☰ MINES V3.1 · Tap to expand" else "☰ MINES V3.1 · Tap to collapse"
        }
        heading.setOnTouchListener(object : View.OnTouchListener {
            var px = 0; var py = 0; var sx = 0f; var sy = 0f; var moved = false
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { px=params.x; py=params.y; sx=event.rawX; sy=event.rawY; moved=false; return true }
                    MotionEvent.ACTION_MOVE -> {
                        if (kotlin.math.abs(event.rawX-sx)>dp(6) || kotlin.math.abs(event.rawY-sy)>dp(6)) moved=true
                        if (moved) { params.x=px+(event.rawX-sx).toInt(); params.y=py+(event.rawY-sy).toInt(); wm.updateViewLayout(panel,params) }
                        return true
                    }
                    MotionEvent.ACTION_UP -> { if (!moved) v.performClick(); return true }
                }
                return true
            }
        })
        details = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        panel.addView(details)
        status = label(13f); suggestion = label(14f, Color.rgb(135,235,165)); history = label(12f, Color.LTGRAY)
        details.addView(status); details.addView(suggestion); details.addView(history)
        board = GridLayout(this).apply { columnCount = 5 }
        details.addView(board)
        fun action(title: String, callback: () -> Unit) {
            details.addView(Button(this).apply { text=title; textSize=12f; setOnClickListener { callback() } },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(43)))
        }
        action("Edit completed board (manual)") {
            manualEdit = true
            gems.clear(); bombs.clear(); selected.clear(); complete=false; suggested=null
            render()
            Toast.makeText(this, "Tap tiles: unknown → gem → bomb → unknown", Toast.LENGTH_LONG).show()
        }
        action("Save manually entered board") {
            if (manualEdit && gems.size + bombs.size == 25 && bombs.isNotEmpty()) {
                complete = true
                saveAutomatic()
                manualEdit = false
                render()
                Toast.makeText(this, "Board saved", Toast.LENGTH_SHORT).show()
            } else Toast.makeText(this, "Mark all 25 tiles, including bombs, before saving", Toast.LENGTH_LONG).show()
        }
        action("Resume automatic scanning") { manualEdit=false; render() }
        action("Historical ranking (not predictive)") { suggest(); render() }
        action("Random unopened tile") {
            suggested = (1..25).filter { it !in gems && it !in bombs }.randomOrNull(Random.Default); render()
        }
        action("Save verified board manually") {
            if (complete) {
                saveAutomatic(); render()
            } else {
                Toast.makeText(this, "Not saved: all 25 tiles must be identified", Toast.LENGTH_LONG).show()
            }
        }
        action("Clear current display") { gems.clear(); bombs.clear(); selected.clear(); complete=false; suggested=null; render() }
        wm.addView(panel,params)
        val visibilityFilter = IntentFilter().apply {
            addAction("com.snyftw.mines.HIDE_OVERLAY")
            addAction("com.snyftw.mines.SHOW_OVERLAY")
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(visibilityReceiver, visibilityFilter, RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(visibilityReceiver, visibilityFilter)
        val filter = IntentFilter("com.snyftw.mines.FRAME_RESULT")
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver,filter,RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(receiver,filter)
        render()
    }
    private fun rounds(): JSONArray = try { JSONArray(prefs.getString("rounds","[]")) } catch (_: Exception) { JSONArray() }
    private fun saveAutomatic() {
        if (!complete || gems.size + bombs.size < 23 || bombs.isEmpty()) return
        val arr=rounds()
        val last = arr.optJSONObject(arr.length() - 1)
        if (last != null && last.optString("bombs") == JSONArray(bombs.sorted()).toString()
            && System.currentTimeMillis() - last.optLong("timestamp") < 10000L) return
        val outcome = when {
            selected.any { it in bombs } -> "loss"
            selected.isNotEmpty() -> "unknown"
            else -> "unknown"
        }
        arr.put(JSONObject().put("outcome",outcome)
            .put("gems",JSONArray(gems.sorted())).put("bombs",JSONArray(bombs.sorted()))
            .put("selected",JSONArray(selected.sorted()))
            .put("mines",bombs.size).put("recognizedTiles",gems.size + bombs.size)
            .put("confidence",if (gems.size + bombs.size == 25) "full" else "partial")
            .put("timestamp",System.currentTimeMillis())
            .put("detection",if (manualEdit) "manual-v32" else "automatic-v32"))
        prefs.edit().putString("rounds",arr.toString()).apply()
    }
    private fun counts(): IntArray {
        val out=IntArray(25); val arr=rounds()
        for (i in 0 until arr.length()) {
            val round=arr.optJSONObject(i) ?: continue
            if (round.optString("confidence") != "full") continue
            val b=round.optJSONArray("bombs") ?: continue
            for (j in 0 until b.length()) { val tile=b.optInt(j); if (tile in 1..25) out[tile-1]++ }
        }
        return out
    }
    private fun suggest() {
        val candidates=(1..25).filter { it !in gems && it !in bombs }
        val freq=counts()
        suggested=candidates.groupBy { freq[it-1] }.minByOrNull { it.key }?.value?.randomOrNull(Random.Default)
    }
    private fun render() {
        val arr=rounds(); val freq=counts()
        val valid=(0 until arr.length()).count { arr.optJSONObject(it)?.optJSONArray("bombs") != null }
        status.text=if (manualEdit) "MANUAL ENTRY · ${gems.size + bombs.size}/25 tiles marked" else if (complete) "ROUND RESULT · ${gems.size} gems / ${bombs.size} bombs · ${recognizedTiles}/25 detected" else "Scanning · ${gems.size} gems / ${bombs.size} bombs · ${25-gems.size-bombs.size} unknown"
        suggestion.text=suggested?.let { "Tile $it · historical count ${freq[it-1]}/$valid (NOT predictive)" }
            ?: "Select Historical ranking to see a tile"
        val wins=(0 until arr.length()).count { arr.optJSONObject(it)?.optString("outcome") == "probable_cashout" }
        val losses=(0 until arr.length()).count { arr.optJSONObject(it)?.optString("outcome") == "loss" }
        history.text="Saved rounds: ${arr.length()} · Cashouts (estimated): $wins · Losses: $losses\nBright = selected · Dim = unplayed"
        board.removeAllViews()
        for (n in 1..25) {
            val t=label(13f).apply {
                text="${if(n in bombs) "💣" else if(n in gems) "💎" else "·"} $n"
                gravity=Gravity.CENTER
                setBackgroundColor(if (n==suggested) Color.rgb(35,85,80) else Color.rgb(35,48,65))
                alpha=if (n in selected || n==suggested) 1f else 0.78f
                setOnClickListener {
                    if (manualEdit) {
                        when {
                            n in gems -> { gems.remove(n); bombs.add(n) }
                            n in bombs -> bombs.remove(n)
                            else -> gems.add(n)
                        }
                        complete=false
                    } else suggested=n
                    render()
                }
            }
            board.addView(t,GridLayout.LayoutParams().apply {
                width=dp(46); height=dp(40); setMargins(dp(1),dp(1),dp(1),dp(1))
            })
        }
    }
    override fun onDestroy() {
        try { unregisterReceiver(receiver) } catch (_: Exception) { }
        try { unregisterReceiver(visibilityReceiver) } catch (_: Exception) { }
        if (::panel.isInitialized) wm.removeView(panel)
        super.onDestroy()
    }
}
