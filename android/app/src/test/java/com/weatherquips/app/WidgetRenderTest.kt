package com.weatherquips.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.weatherquips.app.domain.model.CachedWeather
import com.weatherquips.app.domain.model.Coordinates
import com.weatherquips.app.domain.model.HourlyForecast
import com.weatherquips.app.domain.model.NowcastPoint
import com.weatherquips.app.domain.model.WeatherCondition
import com.weatherquips.app.widget.IntensityScale
import com.weatherquips.app.widget.PrecipitationGraph
import com.weatherquips.app.widget.PrecipitationOutlook
import com.weatherquips.app.widget.PrecipitationOutlooks
import com.weatherquips.app.widget.WidgetColors
import com.weatherquips.app.widget.WidgetCopyWriter
import com.weatherquips.app.widget.WidgetTextLayout
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Locale

/**
 * Draws the widget to PNGs in build/screenshots/ so the graph can be reviewed.
 *
 * Glance renders through RemoteViews and cannot be rasterised off a device, so
 * the card around the graph is reconstructed here with the same dimensions,
 * colours and copy the widget uses. The graph itself is the real bitmap the
 * widget ships — this is the only way to look at it without a phone.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class WidgetRenderTest {

    @Test
    fun `render the widget in every state it can be in`() {
        listOf(
            "dry" to nowcast(DoubleArray(11) { 0.0 }),
            "shower-arriving" to nowcast(
                doubleArrayOf(0.0, 0.0, 0.0, 0.0, 0.1, 0.6, 1.6, 2.8, 2.1, 0.9, 0.2),
            ),
            "downpour" to nowcast(
                doubleArrayOf(1.2, 2.4, 4.8, 7.2, 8.4, 6.0, 3.2, 1.4, 0.4, 0.0, 0.0),
                condition = WeatherCondition.RAINY,
            ),
            "drizzle" to nowcast(
                doubleArrayOf(0.0, 0.1, 0.2, 0.3, 0.25, 0.2, 0.15, 0.1, 0.05, 0.0, 0.0),
                condition = WeatherCondition.RAINY,
            ),
            "hourly-fallback" to hourlyOnly(),
            "radar-5min" to radarNowcast(),
            "stale-hourly" to staleCache(),
        ).forEach { (name, cached) ->
            // The stale card is the point of the stale card: read it three
            // hours after it was cached.
            val readAt = if (name == "stale-hourly") NOW + 3 * 60 * 60_000L else NOW
            val outlook = PrecipitationOutlooks.from(cached, nowMillis = readAt)
            save("widget-$name-light", card(outlook, dark = false, widthDp = 280f))
            save("widget-$name-dark", card(outlook, dark = true, widthDp = 280f))
        }

        val narrow = PrecipitationOutlooks.from(
            nowcast(doubleArrayOf(0.0, 0.0, 0.3, 1.2, 2.4, 3.0, 2.0, 1.0, 0.3, 0.0, 0.0)),
            nowMillis = NOW,
        )
        save("widget-narrow-light", card(narrow, dark = false, widthDp = 180f))
        save("widget-narrow-dark", card(narrow, dark = true, widthDp = 180f))

        // The same shower in Dutch, and a clear night, to see the quip wrap.
        save("widget-nl-shower", card(narrow, dark = false, widthDp = 280f, locale = Locale.forLanguageTag("nl")))
        save("widget-nl-narrow", card(narrow, dark = true, widthDp = 180f, locale = Locale.forLanguageTag("nl")))
        val clearNight = PrecipitationOutlooks.from(
            nowcast(DoubleArray(11) { 0.0 }, condition = WeatherCondition.CLEAR),
            nowMillis = NOW,
        )
        (0 until 3).forEach { hour ->
            val at = NOW + hour * 3_600_000L
            save("widget-clear-night-$hour", card(clearNight, dark = true, widthDp = 180f, readAt = at))
        }
    }

    // --- fixtures --------------------------------------------------------

    /** Eleven quarter-hours: half an hour of history, then two hours ahead. */
    /** A fixed clock: the widget ages its series against the cache time. */
    private val NOW = 1_757_000_000_000L

    private fun nowcast(
        rates: DoubleArray,
        condition: WeatherCondition = WeatherCondition.CLOUDY,
    ): CachedWeather {
        val start = 17 * 60 + 30
        return CachedWeather(
            data = TestWeather.sample(condition = condition).copy(
                nowcast = rates.mapIndexed { i, mm ->
                    val minutes = start + i * 15
                    NowcastPoint(
                        time = "%02d:%02d".format((minutes / 60) % 24, minutes % 60),
                        minutesFromNow = i * 15 - 30,
                        millimetresPerHour = mm,
                    )
                },
            ),
            fetchedAtEpochMillis = NOW,
            coordinates = Coordinates(52.99, 6.56),
        )
    }

    /**
     * What Buienradar's radar feed looks like: five-minute steps, two hours
     * ahead, and no history — the left edge is the current minute.
     */
    private fun radarNowcast(): CachedWeather {
        val shower = doubleArrayOf(
            0.0, 0.0, 0.0, 0.0, 0.1, 0.3, 0.8, 1.6, 2.9, 4.4, 5.8, 6.6,
            6.1, 4.9, 3.4, 2.2, 1.3, 0.7, 0.3, 0.1, 0.0, 0.0, 0.0, 0.0,
        )
        val start = 18 * 60 + 5
        return CachedWeather(
            data = TestWeather.sample(condition = WeatherCondition.CLOUDY).copy(
                nowcast = shower.mapIndexed { i, mm ->
                    val minutes = start + i * 5
                    NowcastPoint(
                        time = "%02d:%02d".format((minutes / 60) % 24, minutes % 60),
                        minutesFromNow = i * 5,
                        millimetresPerHour = mm,
                    )
                },
            ),
            fetchedAtEpochMillis = NOW,
            coordinates = Coordinates(52.99, 6.56),
        )
    }

    /**
     * A cache three hours old: the five-minute series has expired, so the
     * graph falls back to the hourly forecast and the face says how old it is.
     */
    private fun staleCache() = CachedWeather(
        data = TestWeather.sample(condition = WeatherCondition.CLOUDY).copy(
            utcOffsetSeconds = 0,
            nowcast = (-12..23).map { step ->
                val minutes = 12 * 60 + step * 5
                NowcastPoint(
                    time = "%02d:%02d".format((minutes / 60 + 24) % 24, Math.floorMod(minutes, 60)),
                    minutesFromNow = step * 5,
                    millimetresPerHour = 0.0,
                )
            },
            hourlyForecast = (12..23).map {
                HourlyForecast("%02d:00".format(it), 14.0, 40, if (it in 16..17) 1.4 else 0.0)
            },
        ),
        fetchedAtEpochMillis = NOW,
        coordinates = Coordinates(52.99, 6.56),
    )

    /** A provider with no sub-hourly feed, which is the coarse path. */
    private fun hourlyOnly() = CachedWeather(
        data = TestWeather.sample(condition = WeatherCondition.CLOUDY).copy(
            nowcast = emptyList(),
            hourlyForecast = listOf(
                HourlyForecast("18:00", 14.0, 20, 0.0),
                HourlyForecast("19:00", 13.0, 55, 1.1),
                HourlyForecast("20:00", 12.0, 70, 2.6),
                HourlyForecast("21:00", 12.0, 30, 0.4),
            ),
        ),
        fetchedAtEpochMillis = NOW,
        coordinates = Coordinates(52.99, 6.56),
    )

    // --- the card --------------------------------------------------------

    private fun card(
        outlook: PrecipitationOutlook,
        dark: Boolean,
        widthDp: Float,
        locale: Locale = Locale.ENGLISH,
        readAt: Long = NOW,
    ): Bitmap {
        val heightDp = 140f
        val scale = PrecipitationGraph.RENDER_SCALE
        val bitmap = Bitmap.createBitmap(
            (widthDp * scale).toInt(),
            (heightDp * scale).toInt(),
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(bitmap)
        val wide = widthDp >= 280f

        val background = if (dark) 0xFF0D0D0D.toInt() else 0xFFFAFAFA.toInt()
        val onBackground = if (dark) 0xFFF2F2F2.toInt() else 0xFF171717.toInt()
        val muted = if (dark) 0xFF8C8C8C.toInt() else 0xFF737373.toInt()

        canvas.drawRoundRect(
            RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat()),
            20f * scale, 20f * scale,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = background },
        )

        val writer = WidgetCopyWriter(TestResources.resources(locale))
        val copy = writer.write(outlook, nowMillis = readAt)
        val side = 14f * scale
        val contentWidth = widthDp - 28f
        val headline = text(onBackground, (if (wide) 20f else 17f) * scale, bold = true)
        canvas.drawText(copy.headline, side, 12f * scale - headline.ascent(), headline)

        // The meta line, where the widget shows it: always on the wide size,
        // and on the narrow one only to admit the data is old.
        val stale = outlook.ageMinutes >= 30
        val wet = outlook.nowMillimetresPerHour >= IntensityScale.WET_MM_PER_HOUR
        val (quip, metaText, lines) = WidgetTextLayout.arrange(
            copy, writer.metaChoices(outlook, wide), contentWidth, 1f,
        )
        val quipTop = 12f * scale + 26f * scale
        if (metaText != null) {
            val meta = text(
                when {
                    stale -> 0xFFD97706.toInt()
                    wet -> 0xFF3B82F6.toInt()
                    else -> muted
                },
                11f * scale,
                bold = true,
            )
            canvas.drawText(
                metaText,
                bitmap.width - side - meta.measureText(metaText),
                quipTop - meta.ascent(),
                meta,
            )
        }

        // The quip, wrapped the way the widget measures it.
        val quipWidth = contentWidth -
            (metaText?.let { WidgetTextLayout.metaWidth(it, 1f) + 8f } ?: 0f)
        val quipPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = onBackground
            textSize = WidgetTextLayout.QUIP_SP * scale
            typeface = Typeface.create("sans-serif", Typeface.ITALIC)
        }
        val layout = StaticLayout.Builder
            .obtain(quip, 0, quip.length, quipPaint, (quipWidth * scale).toInt())
            .setIncludePad(false)
            .setMaxLines(WidgetTextLayout.MAX_QUIP_LINES)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
        canvas.save()
        canvas.translate(side, quipTop)
        layout.draw(canvas)
        canvas.restore()

        val graphTop = quipTop + lines * WidgetTextLayout.QUIP_LINE_DP * scale + 6f * scale
        val graph = PrecipitationGraph.render(
            chart = outlook.chart,
            widthDp = contentWidth,
            heightDp = (heightDp * scale - graphTop - 12f * scale) / scale,
            palette = WidgetColors.graph,
            labels = writer.graphLabels(),
        )
        if (graph != null) canvas.drawBitmap(graph, side, graphTop, null)

        return bitmap
    }

    private fun text(colour: Int, size: Float, bold: Boolean = false) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colour
            textSize = size
            typeface = Typeface.create(
                if (bold) "sans-serif-medium" else "sans-serif",
                if (bold) Typeface.BOLD else Typeface.NORMAL,
            )
        }

    private fun save(name: String, bitmap: Bitmap) {
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }
}
