package com.freedomfighter.mp4remixer.core

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The short side, in pixels, a video is brought down to. */
enum class Limit(val shortSide: Int, val label: String) {
    ORIGINAL(0, ""),
    P1080(1080, "1080p"),
    P720(720, "720p"),
}

/**
 * What to do to one video, in the frame as it is displayed (rotation applied).
 * The crop is in the normalised coordinates Media3 uses: -1..1 on both axes, y upwards.
 */
data class Plan(
    val cropped: Boolean,
    val left: Float,
    val right: Float,
    val bottom: Float,
    val top: Float,
    val width: Int,
    val height: Int,
    val scaled: Boolean,
)

object Planner {

    /**
     * [center] places the square along the long side: 0 keeps the left (or top) end,
     * 1 the right (or bottom) end. Returns null when the video already is what was asked,
     * so that nothing is re-encoded for no gain.
     */
    fun plan(width: Int, height: Int, square: Boolean, limit: Limit, center: Float): Plan? {
        if (width <= 0 || height <= 0) return null
        val short = min(width, height)
        val long = max(width, height)
        val cropped = square && width != height

        var left = -1f
        var right = 1f
        var bottom = -1f
        var top = 1f
        if (cropped) {
            val start = center.coerceIn(0f, 1f) * (long - short) / long
            val span = short.toFloat() / long
            if (width > height) {
                left = -1f + 2f * start
                right = left + 2f * span
            } else {
                top = 1f - 2f * start
                bottom = top - 2f * span
            }
        }

        val keptWidth = if (cropped) short else width
        val keptHeight = if (cropped) short else height
        val scaled = limit.shortSide in 1 until short
        if (!cropped && !scaled) return null

        val factor = if (scaled) limit.shortSide.toFloat() / short else 1f
        return Plan(
            cropped = cropped,
            left = left, right = right, bottom = bottom, top = top,
            width = even(keptWidth * factor),
            height = even(keptHeight * factor),
            scaled = scaled,
        )
    }

    /** "Holiday.mp4" -> "Holiday-1x1-720p.mp4" */
    fun outputName(sourceName: String, plan: Plan, limit: Limit): String {
        val base = sourceName.substringBeforeLast('.', sourceName).trim().ifBlank { "video" }
        val suffix = buildString {
            if (plan.cropped) append("-1x1")
            if (plan.scaled) append("-").append(limit.label)
        }
        return "$base$suffix.mp4"
    }

    /** Encoders refuse odd sizes. */
    private fun even(value: Float): Int = max(2, (value / 2f).roundToInt() * 2)
}
