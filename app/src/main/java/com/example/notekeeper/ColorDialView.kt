package com.example.notekeeper

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.MotionEvent.*
import android.view.View
import kotlin.math.atan2
import kotlin.math.roundToInt

class ColorDialView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet?,
    defStyle: Int = 0,
    defRes: Int = 0
) : View(
    context,
    attrs,
    defStyle,
    defRes
) {
    private var colors: ArrayList<Int> = arrayListOf(
        Color.RED,
        Color.YELLOW,
        Color.BLUE,
        Color.GREEN,
        Color.DKGRAY,
        Color.CYAN,
        Color.MAGENTA,
        Color.BLACK
    )

    private var dialDrawable: Drawable? = null
    private var noColorDrawable: Drawable? = null

    private val paint = Paint().also {
        it.color = Color.BLUE
        it.isAntiAlias = true
    }

    private var dialDiameter = toDP(100)
    private var extraPadding = toDP(30)
    private var tickSize = toDP(10).toFloat()
    private var angleBetweenColors = 0f
    private var scale = 1f
    private var tickSizeScaled = tickSize * scale
    private var scaleToFit = false

    //Pre-computed padding values
    private var totalStartPadding = 0f
    private var totalTopPadding = 0f
    private var totalEndPadding = 0f
    private var totalBottomPadding = 0f

    //Pre-computed helper values
    private var horizontalSize = 0f
    private var verticalSize = 0f

    //Pre-computed position values
    private var tickPositionVertical = 0f
    private var centerHorizontal = 0f
    private var centerVertical = 0f

    //View interaction values
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var dragging = false
    private var snapAngle = 0f
    private var selectedPosition = 0

    init {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.ColorDialView)
        try {
            val customColors = typedArray.getTextArray(R.styleable.ColorDialView_colors)?.map {
                Color.parseColor(it.toString())
            } as ArrayList<Int>?
            customColors?.let {
                colors = customColors
            }
            dialDiameter =
                typedArray.getDimension(R.styleable.ColorDialView_dialDiameter, toDP(100).toFloat())
                    .toInt()
            extraPadding = typedArray.getDimension(R.styleable.ColorDialView_tickPadding, toDP(30).toFloat()).toInt()
            tickSize = typedArray.getDimension(R.styleable.ColorDialView_tickRadius, toDP(10).toFloat())
            scaleToFit = typedArray.getBoolean(R.styleable.ColorDialView_scaleToFit, false)
        } finally {
            typedArray.recycle()
        }
        dialDrawable = context.getDrawable(R.drawable.ic_dial).also {
            it?.bounds = getCenteredBounds(dialDiameter)
            it?.setTint(Color.DKGRAY)
        }
        noColorDrawable = context.getDrawable(R.drawable.ic_baseline_clear_24).also {
            it?.bounds = getCenteredBounds(tickSize.toInt(), scalar = 2f)
        }
        colors.add(0, Color.TRANSPARENT)
        angleBetweenColors = 360f / colors.size
        refreshValues(true)
    }

    var selectedColorValue: Int = android.R.color.transparent
    set(value) {
        val index = colors.indexOf(value)
        selectedPosition = if (index == -1) 0 else index
        snapAngle = (selectedPosition * angleBetweenColors).toFloat()
        invalidate()
    }

    private var listeners: ArrayList<(Int) -> Unit> = arrayListOf()
    fun addListener(function : (Int) -> Unit) {
        listeners.add (function)
    }

    private fun broadcastColorChange() {
        listeners.forEach {
            if (selectedPosition > colors.size - 1) {
                it(colors[0])
            } else {
                it(colors[selectedPosition])
            }
        }
    }

    private fun getCenteredBounds(size: Int, scalar: Float = 1f): Rect {
        val half = ((if (size > 0) size / 2 else 1) * scalar).toInt()
        return Rect(-half, -half, half, half)
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event != null) {
            dragStartX = event.x
            dragStartY = event.y
        }
        if (event?.action == ACTION_DOWN || event?.action == ACTION_MOVE) {
            dragging = true

            //figure out snap angle
            if (getSnapAngle(dragStartX, dragStartY)) {
                broadcastColorChange()
                invalidate()
            }

        }
        if (event?.action == ACTION_UP) {
            dragging = false
        }
        return true
    }

    private fun getSnapAngle(x: Float, y: Float): Boolean {
        var dragAngle = cartesianToPolar(x - horizontalSize / 2, (verticalSize - y) - verticalSize / 2)
        val nearest  = (getNearestAngle(dragAngle) / angleBetweenColors).roundToInt()
        val newAngle: Float = nearest * angleBetweenColors
        var shouldUpdate = false
        if (newAngle != snapAngle) {
            shouldUpdate = true
            selectedPosition = nearest
        }
        snapAngle = newAngle
        return shouldUpdate
    }

    private fun getNearestAngle(dragAngle: Float): Float {
        var adjustedAngle = (360 - dragAngle) + 90
        while (adjustedAngle > 360) adjustedAngle -= 360
        return adjustedAngle
    }

    private fun cartesianToPolar(x: Float, y: Float): Float {
        val angle: Float = Math.toDegrees((atan2(y.toDouble(), x.toDouble()))).toFloat()
        return when (angle.toInt()) {
            in 0 .. 180 -> angle
            in -180 .. 0 -> angle + 360
            else -> angle
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (scaleToFit) {
            refreshValues(false)
            val specWidth = MeasureSpec.getSize(widthMeasureSpec)
            val specHeight = MeasureSpec.getSize(heightMeasureSpec)
            val workingWidth = specWidth - paddingStart - paddingEnd
            val workingHeight = specHeight - paddingTop - paddingBottom
            scale = if (workingWidth < workingHeight) {
                (workingWidth) / (horizontalSize - paddingLeft - paddingRight)
            } else {
                (workingHeight) / (verticalSize - paddingTop - paddingBottom)
            }
            dialDrawable?.let {
                it.bounds = getCenteredBounds((dialDiameter * scale).toInt())
            }
            noColorDrawable?.let {
                it.bounds = getCenteredBounds((tickSize * scale).toInt(), 2f)
            }
            val width = resolveSizeAndState((horizontalSize * scale).toInt(), widthMeasureSpec, 0)
            val height = resolveSizeAndState((verticalSize * scale).toInt(), heightMeasureSpec, 0)
            setMeasuredDimension(width, height)
        } else {
            val width = resolveSizeAndState(horizontalSize.toInt(), widthMeasureSpec, 0)
            val height = resolveSizeAndState(verticalSize.toInt(), heightMeasureSpec, 0)
            refreshValues(true)
            setMeasuredDimension(width, height)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val saveCount = canvas.save()
        colors.forEachIndexed { i, color ->
            if (i == 0) {
                canvas.translate(centerHorizontal, tickPositionVertical)
                noColorDrawable?.draw(canvas)

                //this is an alternative to restoreToCount() used to return to normal position after translation
                canvas.translate(-centerHorizontal, -tickPositionVertical)
            } else {
                paint.color = colors[i]
                canvas.drawCircle(centerHorizontal, tickPositionVertical, tickSizeScaled, paint)
            }
            canvas.rotate(angleBetweenColors, centerHorizontal, centerVertical)
        }
        canvas.restoreToCount(saveCount)
        canvas.rotate(snapAngle, centerHorizontal, centerVertical)
        canvas.translate(centerHorizontal, centerVertical)
        dialDrawable?.draw(canvas)
    }

    private fun refreshValues(withScale: Boolean) {
        val localScale = if (withScale) scale else 1f
        //compute padding values
        totalStartPadding = paddingStart + extraPadding * localScale
        totalTopPadding = paddingTop + extraPadding * localScale
        totalEndPadding = paddingEnd + extraPadding * localScale
        totalBottomPadding = paddingBottom + extraPadding * localScale
        //compute helper values
        horizontalSize = paddingStart + paddingEnd + (extraPadding * localScale * 2) + (dialDiameter * localScale)
        verticalSize = paddingTop + paddingBottom + (extraPadding * localScale * 2) + (dialDiameter * localScale)

        //compute position values
        tickPositionVertical = paddingTop + (extraPadding * localScale) / 2f
        centerHorizontal =
            totalStartPadding + (horizontalSize - totalStartPadding - totalEndPadding) / 2f
        centerVertical =
            totalTopPadding + (verticalSize - totalTopPadding - totalBottomPadding) / 2f
        tickSizeScaled = tickSize * localScale
    }

    private fun toDP(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }
}