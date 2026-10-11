package com.snyftw.mines

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.*
import android.graphics.Typeface

class MainActivity : Activity() {
    private var mines = 3
    private val safe = mutableSetOf<Int>()
    private var suggested: Int? = null
    private lateinit var grid: GridLayout
    private lateinit var status: TextView
    private lateinit var mineLabel: TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(22, 30, 22, 20); setBackgroundColor(Color.rgb(16,24,32)) }
        fun label(t:String, size:Float=18f): TextView = TextView(this).apply { text=t; textSize=size; setTextColor(Color.WHITE); gravity=Gravity.CENTER; setPadding(6,12,6,12) }
        root.addView(label("MINES ASSISTANT",26f).apply { setTypeface(null,Typeface.BOLD) })
        status=label(""); root.addView(status)
        val selector=LinearLayout(this).apply { gravity=Gravity.CENTER }
        val minus=Button(this).apply { text="−" }; mineLabel=label(""); val plus=Button(this).apply { text="+" }
        selector.addView(minus); selector.addView(mineLabel); selector.addView(plus); root.addView(selector)
        minus.setOnClickListener { if(mines>1){ mines--; reset() } }
        plus.setOnClickListener { if(mines<24){ mines++; reset() } }
        grid=GridLayout(this).apply { columnCount=5; rowCount=5 }
        root.addView(grid)
        val suggest=Button(this).apply { text="SUGGEST NEXT TILE" }
        suggest.setOnClickListener { val remaining=(0..24).filterNot { it in safe }; suggested=remaining.randomOrNull(); render() }
        root.addView(suggest)
        val reset=Button(this).apply { text="NEW GAME" }; reset.setOnClickListener { reset() }; root.addView(reset)
        root.addView(label("Suggestions are random, not predictions. Tap a tile only after it is confirmed safe in Stake.",13f))
        setContentView(root); render()
    }
    private fun reset(){ safe.clear(); suggested=null; render() }
    private fun render(){
        mineLabel.text="$mines mines"
        val unopened=25-safe.size
        val chance=if(unopened>0) ((unopened-mines).coerceAtLeast(0)*100.0/unopened) else 0.0
        status.text="Safe picks: ${safe.size}   |   Next safe chance: %.1f%%".format(chance)
        grid.removeAllViews()
        val width=(resources.displayMetrics.widthPixels-54)/5
        for(i in 0..24){
            val button=Button(this).apply {
                text=when { i in safe -> "◆"; i==suggested -> "◎"; else -> "◇" }
                textSize=21f
                setTextColor(Color.WHITE)
                setBackgroundTintList(android.content.res.ColorStateList.valueOf(when { i in safe -> Color.rgb(26,113,76); i==suggested -> Color.rgb(37,99,235); else -> Color.rgb(45,62,78) }))
                setOnClickListener { if(i !in safe && safe.size < 25-mines){safe.add(i);if(suggested==i)suggested=null;render()} }
            }
            grid.addView(button,GridLayout.LayoutParams().apply { this.width=width; this.height=width; setMargins(1,1,1,1) })
        }
    }
}
