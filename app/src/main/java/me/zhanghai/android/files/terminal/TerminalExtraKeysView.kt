/*
 * Copyright (c) 2026 OpenManager
 * All Rights Reserved.
 */

package me.zhanghai.android.files.terminal

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

class TerminalExtraKeysView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    var onKeyString: ((String) -> Unit)? = null
    var onOverflowMenuClick: ((View) -> Unit)? = null

    var isCtrlActive: Boolean = false
        private set
    var isAltActive: Boolean = false
        private set

    private var ctrlButton: TextView? = null
    private var altButton: TextView? = null

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.parseColor("#141414"))
        val pad = dp(2)
        setPadding(pad, pad, pad, pad)
        buildKeys()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun buildKeys() {
        removeAllViews()

        // Row 1: Esc, Tab, PgUp, Home, ▲, End, ⋮
        val row1 = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(38)).apply {
                bottomMargin = dp(2)
            }
        }

        val row1Keys = arrayOf(
            Pair("Esc", "\u001b"),
            Pair("Tab", "\t"),
            Pair("PgUp", "\u001b[5~"),
            Pair("Home", "\u001b[1~"),
            Pair("▲", "\u001b[A"),
            Pair("End", "\u001b[4~"),
            Pair("⋮", "MENU")
        )

        for ((label, action) in row1Keys) {
            val btn = createButton(label)
            btn.setOnClickListener {
                if (action == "MENU") {
                    onOverflowMenuClick?.invoke(btn)
                } else {
                    onKeyString?.invoke(action)
                }
            }
            row1.addView(btn)
        }
        addView(row1)

        // Row 2: Ctrl, Alt, PgDn, ◀, ▼, ▶, ⇄
        val row2 = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(38))
        }

        val row2Keys = arrayOf(
            Pair("Ctrl", "CTRL"),
            Pair("Alt", "ALT"),
            Pair("PgDn", "\u001b[6~"),
            Pair("◀", "\u001b[D"),
            Pair("▼", "\u001b[B"),
            Pair("▶", "\u001b[C"),
            Pair("⇄", "\t")
        )

        for ((label, action) in row2Keys) {
            val btn = createButton(label)
            if (action == "CTRL") {
                ctrlButton = btn
                btn.setOnClickListener {
                    isCtrlActive = !isCtrlActive
                    updateModifierStyles()
                }
            } else if (action == "ALT") {
                altButton = btn
                btn.setOnClickListener {
                    isAltActive = !isAltActive
                    updateModifierStyles()
                }
            } else {
                btn.setOnClickListener {
                    onKeyString?.invoke(action)
                }
            }
            row2.addView(btn)
        }
        addView(row2)
    }

    private fun createButton(label: String): TextView {
        val tv = TextView(context)
        val lp = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
            val m = dp(2)
            setMargins(m, 0, m, 0)
        }
        tv.layoutParams = lp
        tv.gravity = Gravity.CENTER
        tv.text = label
        tv.setTextColor(Color.WHITE)
        tv.textSize = 14f
        tv.typeface = Typeface.MONOSPACE
        applyButtonStyle(tv, false)
        return tv
    }

    private fun applyButtonStyle(tv: TextView, isActive: Boolean) {
        val bgNormal = if (isActive) Color.parseColor("#1976D2") else Color.parseColor("#282828")
        val radius = dp(4).toFloat()

        val shape = GradientDrawable().apply {
            cornerRadius = radius
            setColor(bgNormal)
            setStroke(dp(1), if (isActive) Color.parseColor("#42A5F5") else Color.parseColor("#383838"))
        }

        val ripple = RippleDrawable(
            ColorStateList.valueOf(Color.parseColor("#50FFFFFF")),
            shape,
            null
        )
        tv.background = ripple
    }

    private fun updateModifierStyles() {
        ctrlButton?.let { applyButtonStyle(it, isCtrlActive) }
        altButton?.let { applyButtonStyle(it, isAltActive) }
    }

    fun consumeCtrl(): Boolean {
        val was = isCtrlActive
        if (was) {
            isCtrlActive = false
            updateModifierStyles()
        }
        return was
    }

    fun consumeAlt(): Boolean {
        val was = isAltActive
        if (was) {
            isAltActive = false
            updateModifierStyles()
        }
        return was
    }
}
