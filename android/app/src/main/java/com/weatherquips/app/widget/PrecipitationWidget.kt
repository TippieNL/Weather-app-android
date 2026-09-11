package com.weatherquips.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.weatherquips.app.WeatherQuipsApplication
import com.weatherquips.app.domain.model.weatherIconKey
import com.weatherquips.app.ui.components.weatherIconRes
import java.util.Calendar

/**
 * Home-screen widget: how hard it is about to rain, and when.
 *
 * It renders from the same cache the app uses offline, so it shows something
 * sensible even when the phone has been off the network — and the app itself
 * never has to be running.
 *
 * Glance draws through RemoteViews, which cannot use an app's bundled font or
 * draw a path, so the widget is set in the system sans and the graph arrives
 * as a bitmap from [PrecipitationGraph]. Everything else — the palette, the
 * weight, the lowercase labels, the blue for water — follows the app.
 */
class PrecipitationWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(
        setOf(SMALL_SIZE, WIDE_SIZE),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as? WeatherQuipsApplication)?.container
        val cached = container?.weatherRepository?.getCachedWeather()
        val outlook = cached?.let(PrecipitationOutlooks::from)
        val hourOfDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        provideContent {
            GlanceTheme(colors = WidgetColors.providers) {
                WidgetContent(
                    outlook = outlook,
                    hourOfDay = hourOfDay,
                    // The tap target is supplied from here so the content
                    // composable stays pure UI with no intent plumbing in it.
                    modifier = GlanceModifier.clickable(openRadarAction(outlook)),
                )
            }
        }
    }

    companion object {
        val SMALL_SIZE = DpSize(180.dp, 140.dp)
        val WIDE_SIZE = DpSize(280.dp, 140.dp)
    }
}

@Composable
fun WidgetContent(
    outlook: PrecipitationOutlook?,
    hourOfDay: Int,
    modifier: GlanceModifier = GlanceModifier,
) {
    val copy = outlook?.let { WidgetCopyWriter.write(it.outlook, hourOfDay) }
        ?: WidgetCopyWriter.empty()
    val size = LocalSize.current
    val wide = size.width >= PrecipitationWidget.WIDE_SIZE.width

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(GlanceTheme.colors.background)
            .cornerRadius(20.dp)
            .padding(horizontal = SIDE_PADDING.dp, vertical = TOP_PADDING.dp),
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = copy.headline,
                style = TextStyle(
                    color = GlanceTheme.colors.onBackground,
                    fontSize = if (wide) 20.sp else 17.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            if (outlook != null) {
                Image(
                    provider = ImageProvider(
                        weatherIconRes(weatherIconKey(outlook.condition, outlook.isDay)),
                    ),
                    contentDescription = outlook.condition.id,
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onBackground),
                    modifier = GlanceModifier.size(22.dp),
                )
            }
        }

        Row(modifier = GlanceModifier.fillMaxWidth()) {
            Text(
                text = copy.aside,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            if (outlook != null && wide) {
                Text(
                    text = meta(outlook),
                    style = TextStyle(
                        color = if (outlook.nowMillimetresPerHour >= IntensityScale.WET_MM_PER_HOUR) {
                            ColorProvider(WidgetColors.Wet)
                        } else {
                            GlanceTheme.colors.onSurfaceVariant
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                )
            }
        }

        if (outlook != null && !outlook.chart.isEmpty) {
            Spacer(modifier = GlanceModifier.height(GRAPH_GAP.dp))
            Graph(
                chart = outlook.chart,
                widthDp = size.width.value,
                widgetHeightDp = size.height.value,
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            )
        }
    }
}

/** The location, plus the current rate once there is one worth printing. */
private fun meta(outlook: PrecipitationOutlook): String {
    val place = outlook.location.lowercase()
    return if (outlook.nowMillimetresPerHour >= IntensityScale.WET_MM_PER_HOUR) {
        "$place · ${IntensityScale.format(outlook.nowMillimetresPerHour)}"
    } else {
        place
    }
}

@Composable
private fun Graph(
    chart: PrecipitationChart,
    widthDp: Float,
    widgetHeightDp: Float,
    modifier: GlanceModifier,
) {
    val graphWidth = (widthDp - 2 * SIDE_PADDING).coerceAtLeast(MIN_GRAPH_WIDTH)
    val graphHeight = (widgetHeightDp - CHROME_HEIGHT).coerceAtLeast(MIN_GRAPH_HEIGHT)
    val bitmap = remember(chart, graphWidth, graphHeight) {
        PrecipitationGraph.render(chart, graphWidth, graphHeight, WidgetColors.graph)
    } ?: return

    Image(
        provider = ImageProvider(bitmap),
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = modifier,
    )
}

private const val SIDE_PADDING = 14f
private const val TOP_PADDING = 12f

/** Gap between the two text lines and the graph. */
private const val GRAPH_GAP = 6f

/** Padding plus the two text lines plus the gap: what the graph does not get. */
private const val CHROME_HEIGHT = 2 * TOP_PADDING + 26f + 15f + GRAPH_GAP

private const val MIN_GRAPH_WIDTH = 80f
private const val MIN_GRAPH_HEIGHT = 28f

/** The app's palette, mapped onto the slots Glance exposes. */
internal object WidgetColors {
    /** Wet enough to matter — the app's cold accent, which reads as water. */
    val Wet = Color(0xFF3B82F6)

    /** "Now", borrowed from the app's hot end. */
    val Now = Color(0xFFEF4444)

    /**
     * Graph colours, chosen to work on both themes: the bitmap is drawn before
     * Glance resolves a theme, so there is only one palette to get right.
     */
    val graph = GraphPalette(
        line = Wet.toArgb(),
        fillTop = Wet.copy(alpha = 0.55f).toArgb(),
        fillBottom = Wet.copy(alpha = 0.04f).toArgb(),
        grid = Color(0xFF909090).copy(alpha = 0.35f).toArgb(),
        bandLabel = Color(0xFF9A9A9A).toArgb(),
        axisLabel = Color(0xFF8A8A8A).toArgb(),
        nowLine = Now.toArgb(),
    )

    val providers = androidx.glance.material3.ColorProviders(
        light = androidx.compose.material3.lightColorScheme(
            background = Color(0xFFFAFAFA),
            onBackground = Color(0xFF171717),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF171717),
            surfaceVariant = Color(0xFFE8E8E8),
            onSurfaceVariant = Color(0xFF737373),
        ),
        dark = androidx.compose.material3.darkColorScheme(
            background = Color(0xFF0D0D0D),
            onBackground = Color(0xFFF2F2F2),
            surface = Color(0xFF141414),
            onSurface = Color(0xFFF2F2F2),
            surfaceVariant = Color(0xFF262626),
            onSurfaceVariant = Color(0xFF8C8C8C),
        ),
    )
}
