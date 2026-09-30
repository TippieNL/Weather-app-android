package com.weatherquips.app.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.weatherquips.app.R
import com.weatherquips.app.domain.model.AppSettings
import com.weatherquips.app.text.currentLocale
import com.weatherquips.app.text.formatDecimal
import com.weatherquips.app.text.intensityLabel
import com.weatherquips.app.ui.theme.Motion
import com.weatherquips.app.ui.theme.WeatherQuipsColors
import com.weatherquips.app.utils.Formatters
import com.weatherquips.app.widget.IntensityScale
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * One day's rain, unfolded under its row in the week.
 *
 * Leads with the answer — how much, how likely — then when, how hard and for
 * how long, and finally the day as a bar per hour. Every line is optional:
 * what the provider did not say is left out rather than guessed.
 */
@Composable
fun DayRainDetail(
    rain: DayRain,
    settings: AppSettings,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    val water = WeatherQuipsColors.Cold
    val wet = rain.outlook == DayRain.Outlook.WET || rain.outlook == DayRain.Outlook.TRACE

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 2.dp, bottom = 12.dp)
            .testTag(TAG_DAY_RAIN),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.ic_umbrella),
                contentDescription = null,
                tint = if (wet) water else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = when (rain.outlook) {
                    DayRain.Outlook.UNKNOWN -> stringResource(R.string.day_rain_unknown)
                    DayRain.Outlook.DRY -> stringResource(R.string.day_rain_dry)
                    DayRain.Outlook.TRACE -> stringResource(R.string.day_rain_trace)
                    DayRain.Outlook.WET -> stringResource(
                        R.string.day_rain_amount,
                        formatDecimal(rain.totalMm ?: 0.0, locale, maxDecimals = 1),
                    )
                },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .testTag(TAG_DAY_RAIN_HEADLINE),
            )
            val chance = rain.chance
            if (chance != null && rain.outlook != DayRain.Outlook.UNKNOWN) {
                Text(
                    text = stringResource(R.string.day_rain_chance, chance),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (wet) water else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }

        if (!wet) return@Column

        val facts = buildList {
            rain.window?.let { window ->
                add(
                    if (window.allDay) {
                        stringResource(R.string.day_rain_all_day)
                    } else {
                        stringResource(
                            R.string.day_rain_window,
                            Formatters.formatTime(window.from, settings.timeFormat),
                            Formatters.formatTime(window.until, settings.timeFormat),
                        )
                    },
                )
            }
            rain.peak?.let { peak ->
                add(
                    stringResource(
                        R.string.day_rain_peak,
                        Formatters.formatTime(peak.time, settings.timeFormat),
                        stringResource(intensityLabel(peak.band)),
                        stringResource(
                            R.string.rate_mm_per_hour,
                            IntensityScale.formatNumber(peak.millimetresPerHour, locale),
                        ),
                    ),
                )
            }
            rain.wetHours?.let { hours ->
                val rounded = max(1, hours.roundToInt())
                add(pluralStringResource(R.plurals.day_rain_hours, rounded, rounded))
            }
            rain.snowfallCm?.let { snow ->
                add(stringResource(R.string.day_rain_snow, formatDecimal(snow, locale, maxDecimals = 1)))
            }
        }
        if (facts.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            facts.forEach { fact ->
                Text(
                    text = fact,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 24.dp, top = 2.dp),
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        if (rain.bars.size >= 2) {
            DayRainChart(rain = rain, settings = settings)
        } else {
            Text(
                text = stringResource(R.string.day_rain_no_hours),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp),
            )
        }
    }
}

/**
 * The day as a bar per slot, on the widget's intensity scale so drizzle is
 * visible and a downpour does not flatten everything else. How sure the
 * forecast is shows as opacity: a likely shower is solid, a maybe is faint.
 *
 * Decorative to accessibility services: the lines above already say it.
 */
@Composable
private fun DayRainChart(rain: DayRain, settings: AppSettings) {
    val water = WeatherQuipsColors.Cold
    val track = MaterialTheme.colorScheme.surfaceVariant
    val labelColour = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.labelSmall
    val measurer = rememberTextMeasurer()

    // The bars rise when the day opens, like the rest of the panel.
    val growth = remember(rain.date) { Animatable(0f) }
    LaunchedEffect(rain.date) {
        growth.animateTo(1f, tween(Motion.LONG, easing = Motion.EmphasizedDecelerate))
    }

    val labels = remember(rain.bars, settings.timeFormat, rain.barHours) {
        rain.bars.mapIndexedNotNull { index, bar ->
            val hour = bar.time.substringBefore(':').toIntOrNull() ?: return@mapIndexedNotNull null
            if (hour % LABEL_EVERY_HOURS != 0) return@mapIndexedNotNull null
            index to Formatters.formatHourLabel(bar.time, settings.timeFormat)
        }
    }
    val peakIndex = rain.bars.indices.maxByOrNull { rain.bars[it].millimetresPerHour }
        ?.takeIf { rain.peak != null }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(CHART_HEIGHT)
            .padding(start = 24.dp)
            // The tag goes outside: clearAndSetSemantics drops what is inside it.
            .testTag(TAG_DAY_RAIN_CHART)
            .clearAndSetSemantics { },
    ) {
        val labelHeight = LABEL_BAND.toPx()
        val plotHeight = size.height - labelHeight
        val slot = size.width / rain.bars.size
        val gap = (slot * BAR_GAP_FRACTION).coerceAtMost(3.dp.toPx())
        val barWidth = slot - gap
        val corner = CornerRadius(barWidth / 3f, barWidth / 3f)

        // A faint track per slot, so a dry hour still reads as an hour.
        rain.bars.forEachIndexed { index, bar ->
            val left = index * slot + gap / 2f
            drawRoundRect(
                color = track,
                topLeft = Offset(left, plotHeight - STUB.toPx()),
                size = Size(barWidth, STUB.toPx()),
                cornerRadius = corner,
            )
            val fraction = IntensityScale.fraction(bar.millimetresPerHour) * growth.value
            // A likely shower the model gives no amount for still gets a stub.
            val height = when {
                fraction > 0f -> max(fraction * plotHeight, STUB.toPx())
                bar.chance >= DayRains.LIKELY_CHANCE -> STUB.toPx() * 2 * growth.value
                else -> 0f
            }
            if (height > 0f) {
                val certainty = 0.35f + 0.65f * (bar.chance / 100f)
                drawRoundRect(
                    color = water.copy(alpha = if (index == peakIndex) 1f else certainty),
                    topLeft = Offset(left, plotHeight - height),
                    size = Size(barWidth, height),
                    cornerRadius = corner,
                )
            }
        }

        labels.forEach { (index, text) ->
            val layout = measurer.measure(text, TextStyle(color = labelColour, fontSize = labelStyle.fontSize))
            val centre = index * slot + slot / 2f
            val x = (centre - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
            drawText(layout, topLeft = Offset(x, size.height - layout.size.height))
        }
    }
}

private val CHART_HEIGHT = 56.dp
private val LABEL_BAND = 16.dp
private val STUB = 2.dp
private const val BAR_GAP_FRACTION = 0.28f
private const val LABEL_EVERY_HOURS = 6

const val TAG_DAY_RAIN = "day-rain"
const val TAG_DAY_RAIN_HEADLINE = "day-rain-headline"
const val TAG_DAY_RAIN_CHART = "day-rain-chart"
