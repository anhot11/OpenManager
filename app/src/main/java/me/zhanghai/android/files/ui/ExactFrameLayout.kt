/*
 * OpenManager - ExactFrameLayout
 * Enforces MeasureSpec.EXACTLY for child views like DrawerLayout in split/multi-pane layouts.
 */

package me.zhanghai.android.files.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes

class ExactFrameLayout : FrameLayout {
    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, @AttrRes defStyleAttr: Int) : super(
        context, attrs, defStyleAttr
    )

    constructor(
        context: Context,
        attrs: AttributeSet?,
        @AttrRes defStyleAttr: Int,
        @StyleRes defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        val exactWidthSpec = MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY)
        val exactHeightSpec = MeasureSpec.makeMeasureSpec(heightSize, MeasureSpec.EXACTLY)

        super.onMeasure(exactWidthSpec, exactHeightSpec)
    }

    override fun measureChildWithMargins(
        child: View,
        parentWidthMeasureSpec: Int,
        widthUsed: Int,
        parentHeightMeasureSpec: Int,
        heightUsed: Int
    ) {
        val lp = child.layoutParams as MarginLayoutParams
        val widthSize = MeasureSpec.getSize(parentWidthMeasureSpec)
        val heightSize = MeasureSpec.getSize(parentHeightMeasureSpec)

        val childWidth = (widthSize - paddingLeft - paddingRight - lp.leftMargin - lp.rightMargin - widthUsed).coerceAtLeast(0)
        val childHeight = (heightSize - paddingTop - paddingBottom - lp.topMargin - lp.bottomMargin - heightUsed).coerceAtLeast(0)

        val exactWidthSpec = MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY)
        val exactHeightSpec = MeasureSpec.makeMeasureSpec(childHeight, MeasureSpec.EXACTLY)

        child.measure(exactWidthSpec, exactHeightSpec)
    }

    override fun measureChild(child: View, parentWidthMeasureSpec: Int, parentHeightMeasureSpec: Int) {
        val widthSize = MeasureSpec.getSize(parentWidthMeasureSpec)
        val heightSize = MeasureSpec.getSize(parentHeightMeasureSpec)

        val childWidth = (widthSize - paddingLeft - paddingRight).coerceAtLeast(0)
        val childHeight = (heightSize - paddingTop - paddingBottom).coerceAtLeast(0)

        child.measure(
            MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(childHeight, MeasureSpec.EXACTLY)
        )
    }
}
