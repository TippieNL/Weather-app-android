package com.weatherquips.app.widget

import android.content.Context
import android.content.res.Resources
import android.graphics.Paint
import android.graphics.Typeface
import android.text.StaticLayout
import android.text.TextPaint
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
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontStyle
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.weatherquips.app.WeatherQuipsApplication
import com.weatherquips.app.domain.model.weatherIconKey
import com.weatherquips.app.locale.AppLocale
import com.weatherquips.app.text.conditionLabel
import com.weatherquips.app.ui.components.weatherIconRes

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
        val nowMillis = System.currentTimeMillis()
        val outlook = cached?.let { PrecipitationOutlooks.from(it, nowMillis) }
        // In the app's language, which below Android 13 is not the process's.
        val resources = AppLocale.localized(context).resources

        // Being looked at is the one moment the widget knows it matters. If
        // what it is about to draw is old, ask for a fetch on the way out:
        // background work gets throttled, but a glance at the home screen
        // repairs it.
        if (outlook == null || outlook.ageMinutes >= STALE_MINUTES) {
            WidgetRefreshScheduler(context).refreshNow()
        }

        provideContent {
            GlanceTheme(colors = WidgetColors.providers) {
                WidgetContent(
                    outlook = outlook,
                    nowMillis = nowMillis,
                    resources = resources,
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

        /** Old enough to be worth a catch-up fetch, and to say so on the face. */
        const val STALE_MINUTES = 30
    }
}

@Composable
fun WidgetContent(
    outlook: PrecipitationOutlook?,
    nowMillis: Long,
    resources: Resources = LocalContext.current.resources,
    modifier: GlanceModifier = GlanceModifier,
) {
    val writer = remember(resources) { WidgetCopyWriter(resources) }
    val copy = outlook?.let { writer.write(it, nowMillis) } ?: writer.empty()
    val size = LocalSize.current
    val wide = size.width >= PrecipitationWidget.WIDE_SIZE.width
    val fontScale = resources.configuration.fontScale.takeIf { it > 0f } ?: 1f

    val (quip, meta, quipLines) = WidgetTextLayout.arrange(
        copy = copy,
        metaChoices = outlook?.let { writer.metaChoices(it, wide) } ?: listOf(null),
        contentWidthDp = size.width.value - 2 * SIDE_PADDING,
        fontScale = fontScale,
    )

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
                    contentDescription = resources.getString(conditionLabel(outlook.condition)),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onBackground),
                    modifier = GlanceModifier.size(22.dp),
                )
            }
        }

        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            // The joke. Set apart from the headline by style rather than by
            // colour alone: the fact is bold, the remark is italic.
            Text(
                text = quip,
                style = TextStyle(
                    color = GlanceTheme.colors.onBackground,
                    fontSize = WidgetTextLayout.QUIP_SP.sp,
                    fontStyle = FontStyle.Italic,
                ),
                maxLines = quipLines,
                modifier = GlanceModifier.defaultWeight(),
            )
            if (meta != null && outlook != null) {
                Spacer(modifier = GlanceModifier.width(WidgetTextLayout.META_GAP_DP.dp))
                Text(
                    text = meta,
                    style = TextStyle(
                        color = when {
                            outlook.ageMinutes >= PrecipitationWidget.STALE_MINUTES ->
                                ColorProvider(WidgetColors.Stale)
                            outlook.nowMillimetresPerHour >= IntensityScale.WET_MM_PER_HOUR ->
                                ColorProvider(WidgetColors.Wet)
                            else -> GlanceTheme.colors.onSurfaceVariant
                        },
                        fontSize = WidgetTextLayout.META_SP.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                )
            }
        }

        Spacer(modifier = GlanceModifier.height(GRAPH_GAP.dp))
        if (outlook != null && !outlook.chart.isEmpty) {
            Graph(
                chart = outlook.chart,
                labels = writer.graphLabels(),
                widthDp = size.width.value,
                graphHeightDp = size.height.value - chromeHeight(quipLines, fontScale),
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            )
        } else {
            // A widget with an empty rectangle where a graph should be reads as
            // broken. Say what is actually wrong instead.
            Box(
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = writer.noGraph(hasForecast = outlook != null),
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 12.sp,
                    ),
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
private fun Graph(
    chart: PrecipitationChart,
    labels: GraphLabels,
    widthDp: Float,
    graphHeightDp: Float,
    modifier: GlanceModifier,
) {
    val graphWidth = (widthDp - 2 * SIDE_PADDING).coerceAtLeast(MIN_GRAPH_WIDTH)
    val graphHeight = graphHeightDp.coerceAtLeast(MIN_GRAPH_HEIGHT)
    val bitmap = remember(chart, labels, graphWidth, graphHeight) {
        PrecipitationGraph.render(chart, graphWidth, graphHeight, WidgetColors.graph, labels)
    } ?: return

    Image(
        provider = ImageProvider(bitmap),
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = modifier,
    )
}

/**
 * Measures the widget's text the way the launcher will set it.
 *
 * RemoteViews cannot report a layout back, and the graph is a bitmap drawn to
 * a fixed height before the launcher lays anything out. So the quip is
 * measured here: a line that wraps takes its second line out of the graph's
 * height up front, instead of the graph being squashed to fit afterwards.
 */
internal object WidgetTextLayout {
    const val QUIP_SP = 12f
    const val META_SP = 11f

    /** Two lines is a remark; three is an essay on a widget. */
    const val MAX_QUIP_LINES = 2

    /** Line height at 12sp, font padding included. */
    const val QUIP_LINE_DP = 16f

    /**
     * Lines [text] needs at [widthDp]. Text size and width are both in dp
     * here — scaled sp is just dp times the font scale — so no display
     * metrics are needed.
     */
    fun quipLines(text: String, widthDp: Float, fontScale: Float): Int =
        fullLines(text, widthDp, fontScale).coerceIn(1, MAX_QUIP_LINES)

    /** Lines [text] would take with no limit, so callers can tell it would be cut. */
    fun fullLines(text: String, widthDp: Float, fontScale: Float): Int {
        if (text.isEmpty()) return 1
        if (widthDp <= 0f) return Int.MAX_VALUE
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = QUIP_SP * fontScale
            typeface = Typeface.create("sans-serif", Typeface.ITALIC)
        }
        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, paint, widthDp.toInt().coerceAtLeast(1))
            .setIncludePad(false)
            .build()
        return layout.lineCount.coerceAtLeast(1)
    }

    /** Between the quip and the meta beside it. */
    const val META_GAP_DP = 8f

    /** What the second row ends up holding. */
    data class Arrangement(val quip: String, val meta: String?, val quipLines: Int)

    /**
     * Fits the quip and the meta line into the second row, in this order of
     * preference: this hour's quip beside the fullest meta that leaves it
     * room; then the leanest meta beside the first understudy that fits; and
     * only if nothing fits at all, this hour's quip, cut at two lines.
     */
    fun arrange(
        copy: WidgetCopy,
        metaChoices: List<String?>,
        contentWidthDp: Float,
        fontScale: Float,
    ): Arrangement {
        val choices = metaChoices.ifEmpty { listOf(null) }
        fun fits(quip: String, meta: String?) =
            fullLines(quip, quipWidth(meta, contentWidthDp, fontScale), fontScale) <= MAX_QUIP_LINES
        fun arranged(quip: String, meta: String?) =
            Arrangement(quip, meta, quipLines(quip, quipWidth(meta, contentWidthDp, fontScale), fontScale))

        // By index: "no meta" is itself a choice, and it is null.
        val roomy = choices.indexOfFirst { fits(copy.quip, it) }
        if (roomy >= 0) return arranged(copy.quip, choices[roomy])
        val leanest = choices.last()
        val understudy = copy.understudies.firstOrNull { fits(it, leanest) } ?: copy.quip
        return arranged(understudy, leanest)
    }

    private fun quipWidth(meta: String?, contentWidthDp: Float, fontScale: Float): Float =
        contentWidthDp - (meta?.let { metaWidth(it, fontScale) + META_GAP_DP } ?: 0f)

    fun metaWidth(text: String, fontScale: Float): Float =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = META_SP * fontScale
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }.measureText(text)
}

/** Padding, headline, quip and gap: the height the graph does not get. */
private fun chromeHeight(quipLines: Int, fontScale: Float): Float =
    2 * TOP_PADDING + (HEADLINE_HEIGHT + quipLines * WidgetTextLayout.QUIP_LINE_DP) * fontScale + GRAPH_GAP

private const val SIDE_PADDING = 14f
private const val TOP_PADDING = 12f

/** Gap between the two text lines and the graph. */
private const val GRAPH_GAP = 6f

/** The headline row at 20sp, bold. */
private const val HEADLINE_HEIGHT = 26f

private const val MIN_GRAPH_WIDTH = 80f
private const val MIN_GRAPH_HEIGHT = 28f

/** The app's palette, mapped onto the slots Glance exposes. */
internal object WidgetColors {
    /** Wet enough to matter — the app's cold accent, which reads as water. */
    val Wet = Color(0xFF3B82F6)

    /** "Now", borrowed from the app's hot end. */
    val Now = Color(0xFFEF4444)

    /** Data old enough that the user should know before trusting it. */
    val Stale = Color(0xFFD97706)

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
