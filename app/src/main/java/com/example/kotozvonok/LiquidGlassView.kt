package com.example.kotozvonok

// Основано на библиотеке "AppleLiquidGlassForAndroid" (классический View, без Compose),
// перенесено в проект напрямую, чтобы не тянуть внешние зависимости и JitPack.
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout

class LiquidGlassView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : FrameLayout(context, attrs, defStyle) {

    private var blurRadius = 15f
    private var downsampleFactor = 0.12f
    private var zoomLevel = 1.2f
    private var overlayColor = 0x15FFFFFF.toInt()
    private var borderColor = 0x60FFFFFF.toInt()
    private var cornerRadius = 0f

    private var rootView: View? = null
    private var backgroundBitmap: Bitmap? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val clipPath = Path()
    private val rectF = RectF()
    private var shimmerPos = -0.4f
    private var shimmerAnimator: ValueAnimator? = null
    private val shimmerPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        setWillNotDraw(false)
        clipToPadding = false
        clipToOutline = true

        val a = context.obtainStyledAttributes(attrs, R.styleable.LiquidGlassView)
        blurRadius = a.getFloat(R.styleable.LiquidGlassView_lg_blurRadius, blurRadius)
        overlayColor = a.getColor(R.styleable.LiquidGlassView_lg_overlayColor, overlayColor)
        cornerRadius = a.getDimension(R.styleable.LiquidGlassView_lg_cornerRadius, cornerRadius)
        a.recycle()

        borderPaint.color = borderColor

        val colorMatrix = android.graphics.ColorMatrix()
        colorMatrix.setSaturation(1.4f)
        paint.colorFilter = android.graphics.ColorMatrixColorFilter(colorMatrix)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startShimmer()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        shimmerAnimator?.cancel()
        shimmerAnimator = null
    }

    private fun startShimmer() {
        shimmerAnimator?.cancel()
        shimmerAnimator = ValueAnimator.ofFloat(-0.5f, 1.5f).apply {
            duration = 3200L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                shimmerPos = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun setupWithRoot(root: View) {
        this.rootView = root
        root.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (backgroundBitmap == null && root.width > 0 && root.height > 0) {
                    updateBackgroundSnapshot()
                    root.viewTreeObserver.removeOnPreDrawListener(this)
                }
                return true
            }
        })
    }

    fun setupWithActivityRoot() {
        if (context is Activity) {
            val decorView = (context as Activity).window.decorView
            setupWithRoot(decorView)
        }
    }

    fun updateBackgroundSnapshot() {
        val root = rootView ?: return
        if (root.width == 0 || root.height == 0) return
        val wasVisible = visibility
        try {
            val scaledWidth = (root.width * downsampleFactor).toInt().coerceAtLeast(1)
            val scaledHeight = (root.height * downsampleFactor).toInt().coerceAtLeast(1)

            if (backgroundBitmap == null || backgroundBitmap?.width != scaledWidth || backgroundBitmap?.height != scaledHeight) {
                backgroundBitmap?.recycle()
                backgroundBitmap = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888)
            }

            val canvas = Canvas(backgroundBitmap!!)
            canvas.scale(downsampleFactor, downsampleFactor)

            visibility = View.INVISIBLE
            root.draw(canvas)
        } catch (e: Exception) {
            e.printStackTrace()
            // Если снимок не получился — не оставляем вид невидимым навсегда.
            backgroundBitmap = null
        } finally {
            // КРИТИЧНО: видимость обязана вернуться, даже если выше была ошибка —
            // иначе плашка "стекла" может навсегда пропасть с экрана.
            visibility = wasVisible
        }
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rectF.set(0f, 0f, w.toFloat(), h.toFloat())
        clipPath.reset()
        clipPath.addRoundRect(rectF, cornerRadius, cornerRadius, Path.Direction.CW)

        if (cornerRadius > 0) {
            outlineProvider = ViewOutlineProviderRounded(cornerRadius)
            clipToOutline = true
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (rootView != null && backgroundBitmap != null) {
            val root = rootView!!
            val location = IntArray(2)
            getLocationOnScreen(location)
            val rootLocation = IntArray(2)
            root.getLocationOnScreen(rootLocation)

            val relativeX = (location[0] - rootLocation[0]).toFloat()
            val relativeY = (location[1] - rootLocation[1]).toFloat()

            canvas.save()
            canvas.clipPath(clipPath)
            canvas.translate(-relativeX, -relativeY)

            val centerX = relativeX + width / 2f
            val centerY = relativeY + height / 2f
            canvas.scale(zoomLevel, zoomLevel, centerX, centerY)

            val scaleUp = 1f / downsampleFactor
            canvas.scale(scaleUp, scaleUp)

            paint.isFilterBitmap = true
            canvas.drawBitmap(backgroundBitmap!!, 0f, 0f, paint)

            canvas.restore()
        } else {
            // Снимка ещё нет (или не получился) — красивый полупрозрачный фон вместо серого прямоугольника,
            // чтобы плашка никогда не выглядела "пропавшей".
            val fallback = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, 0f, width.toFloat(), height.toFloat(),
                    0x332A2640.toInt(), 0x551D1A2E.toInt(),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, fallback)
        }

        val overlayPaint = Paint().apply { color = overlayColor }
        canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, overlayPaint)

        // Переливающийся блик — узкая диагональная полоса света, которая медленно едет по стеклу по кругу.
        if (width > 0) {
            val bandWidth = width * 0.5f
            val x = shimmerPos * (width + bandWidth) - bandWidth
            shimmerPaint.shader = LinearGradient(
                x, 0f, x + bandWidth, height.toFloat(),
                intArrayOf(0x00FFFFFF, 0x30FFFFFF, 0x00FFFFFF),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.save()
            canvas.clipPath(clipPath)
            canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, shimmerPaint)
            canvas.restore()
        }

        val inset = borderPaint.strokeWidth / 2
        val borderRect = RectF(inset, inset, width - inset, height - inset)
        canvas.drawRoundRect(borderRect, cornerRadius, cornerRadius, borderPaint)

        super.onDraw(canvas)
    }

    fun setRadius(radius: Float) {
        this.blurRadius = radius
        this.downsampleFactor = (2f / radius).coerceIn(0.05f, 0.5f)
        updateBackgroundSnapshot()
    }

    fun setZoom(zoom: Float) {
        this.zoomLevel = zoom
        invalidate()
    }

    fun setGlassColor(color: Int) {
        this.overlayColor = color
        invalidate()
    }

    fun setCorner(radiusPx: Float) {
        this.cornerRadius = radiusPx
        requestLayout()
        invalidate()
    }
}
