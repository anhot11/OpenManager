/*
 * Copyright (c) 2026 OpenManager
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.hex

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import me.zhanghai.android.files.databinding.ItemHexRowBinding

class HexRowAdapter(
    private var buffer: HexBuffer,
    private val typeface: Typeface,
    private val onByteSelected: (Int) -> Unit
) : RecyclerView.Adapter<HexRowAdapter.ViewHolder>() {

    var selectedOffset: Int = 0
        set(value) {
            val oldRow = field / 16
            field = value
            val newRow = value / 16
            if (oldRow == newRow) {
                notifyItemChanged(oldRow)
            } else {
                notifyItemChanged(oldRow)
                notifyItemChanged(newRow)
            }
        }

    var highlightRange: IntRange? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    fun updateBuffer(newBuffer: HexBuffer) {
        this.buffer = newBuffer
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = (buffer.size + 15) / 16

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHexRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        binding.tvOffset.typeface = typeface
        binding.tvHex.typeface = typeface
        binding.tvAscii.typeface = typeface

        val holder = ViewHolder(binding)

        val hexGestureDetector = GestureDetector(parent.context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                val pos = holder.bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return false
                val rowOffset = pos * 16
                val x = e.x - binding.tvHex.paddingLeft
                if (x >= 0) {
                    val charWidth = binding.tvHex.paint.measureText("0")
                    val charPos = (x / charWidth).toInt()
                    val byteInRow = if (charPos < 24) {
                        (charPos / 3).coerceIn(0, 7)
                    } else {
                        (8 + ((charPos - 25).coerceAtLeast(0) / 3)).coerceIn(8, 15)
                    }
                    val targetOffset = rowOffset + byteInRow
                    if (targetOffset < buffer.size) {
                        onByteSelected(targetOffset)
                        return true
                    }
                }
                return false
            }
        })

        val asciiGestureDetector = GestureDetector(parent.context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                val pos = holder.bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return false
                val rowOffset = pos * 16
                val x = e.x - binding.tvAscii.paddingLeft
                if (x >= 0) {
                    val charWidth = binding.tvAscii.paint.measureText("·")
                    val byteInRow = (x / charWidth).toInt().coerceIn(0, 15)
                    val targetOffset = rowOffset + byteInRow
                    if (targetOffset < buffer.size) {
                        onByteSelected(targetOffset)
                        return true
                    }
                }
                return false
            }
        })

        binding.tvHex.setOnTouchListener { v, event ->
            val handled = hexGestureDetector.onTouchEvent(event)
            if (handled) {
                v.performClick()
            }
            handled
        }

        binding.tvAscii.setOnTouchListener { v, event ->
            val handled = asciiGestureDetector.onTouchEvent(event)
            if (handled) {
                v.performClick()
            }
            handled
        }

        return holder
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val rowOffset = position * 16
        val b = holder.binding

        b.tvOffset.text = String.format("%08X:", rowOffset)

        // 1. Build Hex column
        val hexSb = SpannableStringBuilder()
        for (i in 0 until 16) {
            val curOffset = rowOffset + i
            if (curOffset < buffer.size) {
                val byteVal = buffer.getByte(curOffset).toInt() and 0xFF
                val start = hexSb.length
                hexSb.append(String.format("%02X", byteVal))
                val end = hexSb.length

                applySpans(hexSb, start, end, curOffset)

                hexSb.append(" ")
                if (i == 7) {
                    hexSb.append(" ")
                }
            } else {
                hexSb.append("   ")
                if (i == 7) {
                    hexSb.append(" ")
                }
            }
        }
        b.tvHex.text = hexSb

        // 2. Build ASCII column
        val asciiSb = SpannableStringBuilder()
        for (i in 0 until 16) {
            val curOffset = rowOffset + i
            if (curOffset < buffer.size) {
                val byteVal = buffer.getByte(curOffset).toInt() and 0xFF
                val ch = if (byteVal in 32..126) byteVal.toChar() else '·'
                val start = asciiSb.length
                asciiSb.append(ch)
                val end = asciiSb.length

                applySpans(asciiSb, start, end, curOffset)
            } else {
                asciiSb.append(" ")
            }
        }
        b.tvAscii.text = asciiSb
    }

    private fun applySpans(sb: SpannableStringBuilder, start: Int, end: Int, offset: Int) {
        if (offset == selectedOffset) {
            sb.setSpan(
                BackgroundColorSpan(Color.parseColor("#1976D2")),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            sb.setSpan(
                ForegroundColorSpan(Color.WHITE),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        } else if (highlightRange != null && offset in highlightRange!!) {
            sb.setSpan(
                BackgroundColorSpan(Color.parseColor("#388E3C")),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            sb.setSpan(
                ForegroundColorSpan(Color.WHITE),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        } else if (buffer.modifiedIndices.contains(offset)) {
            sb.setSpan(
                ForegroundColorSpan(Color.parseColor("#FFA726")), // Amber/Orange
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    class ViewHolder(val binding: ItemHexRowBinding) : RecyclerView.ViewHolder(binding.root)
}
