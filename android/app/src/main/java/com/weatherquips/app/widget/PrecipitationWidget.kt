package com.weatherquips.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.weatherquips.app.WeatherQuipsApplication
import com.weatherquips.app.domain.model.weatherIconKey
import com.weatherquips.app.ui.components.weatherIconRes
import java.util.Calendar

/**
 * Home-screen widget: how likely it is to rain over the next few hours.
 *
 * It renders from the same cache the app uses offline, so it shows something
 * sensible even when the phone has been off the network — and the app itself
 * never has to be running.
 *
 * Glance draws through RemoteViews, which cannot use an app's bundled font, so
 * the widget is set in the system sans rather than Space Grotesk. Everything
 * else — the palette, the weight, the lowercase labels, the blue for water —
 * follows the app.
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
        val SMALL_SIZE = androidx.compose.ui.unit.DpSize(180.dp, 110.dp)
        val WIDE_SIZE = androidx.compose.ui.unit.DpSize(280.dp, 110.dp)
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
    val wide = LocalSize.current.width >= PrecipitationWidget.WIDE_SIZE.width

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(GlanceTheme.colors.background)
            .cornerRadius(20.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = copy.headline,
                    style = TextStyle(
                        color = GlanceTheme.colors.onBackground,
                        fontSize = if (wide) 21.sp else 18.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
                Text(
                    text = copy.aside,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 12.sp,
                    ),
                    maxLines = 1,
                )
            }
            if (outlook != null) {
                Image(
                    provider = ImageProvider(
                        weatherIconRes(weatherIconKey(outlook.condition, outlook.isDay)),
                    ),
                    contentDescription = outlook.condition.id,
                    colorFilter = androidx.glance.ColorFilter.tint(GlanceTheme.colors.onBackground),
                    modifier = GlanceModifier.size(24.dp),
                )
            }
        }

        Spacer(modifier = GlanceModifier.height(10.dp))

        if (outlook != null && outlook.hours.isNotEmpty()) {
            ChanceChart(
                hours = if (wide) outlook.hours else outlook.hours.take(4),
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            )
            Text(
                text = outlook.location.lowercase(),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
                maxLines = 1,
            )
        }
    }
}

/**
 * Chance of precipitation per hour.
 *
 * Every bar is drawn full height in a faint track with the coloured part
 * on top, so an hour at 10% and an hour with no data look different — an
 * empty column would otherwise read as "no information".
 */
@Composable
private fun ChanceChart(hours: List<OutlookHour>, modifier: GlanceModifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.Bottom) {
        hours.forEachIndexed { index, hour ->
            if (index > 0) Spacer(modifier = GlanceModifier.width(6.dp))
            Column(
                modifier = GlanceModifier.defaultWeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "${hour.chancePercent}%",
                    style = TextStyle(
                        color = if (hour.chancePercent >= PrecipitationOutlooks.LIKELY_THRESHOLD) {
                            ColorProvider(WidgetColors.Wet)
                        } else {
                            GlanceTheme.colors.onSurfaceVariant
                        },
                        fontSize = 10.sp,
                        fontWeight = if (hour.isNow) FontWeight.Bold else FontWeight.Normal,
                    ),
                    maxLines = 1,
                )
                Spacer(modifier = GlanceModifier.height(3.dp))
                Bar(chancePercent = hour.chancePercent)
                Spacer(modifier = GlanceModifier.height(3.dp))
                Text(
                    text = if (hour.isNow) "now" else hour.label.take(2),
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 10.sp,
                        fontWeight = if (hour.isNow) FontWeight.Bold else FontWeight.Normal,
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun Bar(chancePercent: Int) {
    val filled = (BAR_HEIGHT_DP * chancePercent / 100f).coerceAtLeast(MIN_FILL_DP)
    Box(
        modifier = GlanceModifier
            .width(BAR_WIDTH_DP.dp)
            .height(BAR_HEIGHT_DP.dp)
            .background(GlanceTheme.colors.surfaceVariant)
            .cornerRadius(4.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = GlanceModifier
                .width(BAR_WIDTH_DP.dp)
                .height(filled.dp)
                .background(
                    ColorProvider(
                        if (chancePercent >= PrecipitationOutlooks.LIKELY_THRESHOLD) {
                            WidgetColors.Wet
                        } else {
                            WidgetColors.Damp
                        },
                    ),
                )
                .cornerRadius(4.dp),
            content = {},
        )
    }
}

private const val BAR_HEIGHT_DP = 30f
private const val BAR_WIDTH_DP = 10
private const val MIN_FILL_DP = 3f

/** The app's palette, mapped onto the slots Glance exposes. */
internal object WidgetColors {
    /** Likely enough to matter — the app's cold accent, which reads as water. */
    val Wet = Color(0xFF3B82F6)

    /** Possible, not likely. */
    val Damp = Color(0xFF93B9F7)

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
