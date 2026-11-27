package com.aistudio.netgate.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.graphics.drawable.IconCompat
import com.aistudio.netgate.data.model.LiveNetworkTelemetry
import com.aistudio.netgate.data.model.StatusBarSpeedStyle
import com.aistudio.netgate.data.model.formatCompactStatusBarSpeed
import com.aistudio.netgate.data.model.formatSingleLineCompactSpeed

/**
 * Generates crisp monochrome-alpha bitmaps for the Android status bar notification smallIcon.
 * Pure white pixels on a transparent background allow SystemUI to automatically tint the icon
 * for both dark and light status bars.
 */
object StatusBarIconRenderer {

    /**
     * Status bars render the small icon at 24dp. Drawing into 96px keeps the digits crisp when
     * SystemUI downscales the bitmap on 2x/3x displays.
     */
    private const val ICON_SIZE_PX = 96

    fun renderSpeedBitmap(
        telemetry: LiveNetworkTelemetry,
        style: StatusBarSpeedStyle
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(ICON_SIZE_PX, ICON_SIZE_PX, Bitmap.Config.ARGB_8888).apply {
            setHasAlpha(true)
        }
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT)

        val primaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isSubpixelText = true
        }
        val secondaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isSubpixelText = true
        }

        val centerX = ICON_SIZE_PX / 2f
        val maxTextWidth = ICON_SIZE_PX - 4f

        when (style) {
            StatusBarSpeedStyle.DOWNLOAD_UPLOAD -> {
                val dlText = "↓${formatSingleLineCompactSpeed(telemetry.downloadBytesPerSec)}"
                val ulText = "↑${formatSingleLineCompactSpeed(telemetry.uploadBytesPerSec)}"
                drawCenteredFittedLine(
                    canvas = canvas,
                    text = dlText,
                    centerX = centerX,
                    baselineY = 42f,
                    initialTextSize = 40f,
                    maxWidth = maxTextWidth,
                    paint = primaryPaint
                )
                drawCenteredFittedLine(
                    canvas = canvas,
                    text = ulText,
                    centerX = centerX,
                    baselineY = 86f,
                    initialTextSize = 38f,
                    maxWidth = maxTextWidth,
                    paint = secondaryPaint
                )
            }

            StatusBarSpeedStyle.COMBINED -> {
                val badge = formatCompactStatusBarSpeed(telemetry.totalBytesPerSec)
                drawCenteredFittedLine(
                    canvas = canvas,
                    text = badge.valueText,
                    centerX = centerX,
                    baselineY = 53f,
                    initialTextSize = 56f,
                    maxWidth = maxTextWidth,
                    paint = primaryPaint
                )
                drawCenteredFittedLine(
                    canvas = canvas,
                    text = badge.unitText,
                    centerX = centerX,
                    baselineY = 88f,
                    initialTextSize = 34f,
                    maxWidth = maxTextWidth,
                    paint = secondaryPaint
                )
            }

            StatusBarSpeedStyle.DOWNLOAD_ONLY -> {
                val badge = formatCompactStatusBarSpeed(telemetry.downloadBytesPerSec)
                drawCenteredFittedLine(
                    canvas = canvas,
                    text = badge.valueText,
                    centerX = centerX,
                    baselineY = 52f,
                    initialTextSize = 54f,
                    maxWidth = maxTextWidth,
                    paint = primaryPaint
                )
                drawCenteredFittedLine(
                    canvas = canvas,
                    text = "↓${badge.unitText}",
                    centerX = centerX,
                    baselineY = 87f,
                    initialTextSize = 32f,
                    maxWidth = maxTextWidth,
                    paint = secondaryPaint
                )
            }
        }

        return bitmap
    }

    fun createSpeedIcon(
        context: Context,
        telemetry: LiveNetworkTelemetry,
        style: StatusBarSpeedStyle
    ): IconCompat {
        return IconCompat.createWithResource(context, com.aistudio.netgate.R.drawable.ic_status_bar_speed)
    }

    private fun drawCenteredFittedLine(
        canvas: Canvas,
        text: String,
        centerX: Float,
        baselineY: Float,
        initialTextSize: Float,
        maxWidth: Float,
        paint: Paint
    ) {
        var size = initialTextSize
        paint.textSize = size
        var measured = paint.measureText(text)
        while (measured > maxWidth && size > 16f) {
            size -= 1.5f
            paint.textSize = size
            measured = paint.measureText(text)
        }
        canvas.drawText(text, centerX, baselineY, paint)
    }
}
