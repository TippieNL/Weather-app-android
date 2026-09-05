package com.weatherquips.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weatherquips.app.R
import com.weatherquips.app.domain.model.WeatherData
import com.weatherquips.app.ui.components.PokeballGlyph
import com.weatherquips.app.ui.components.QuipCard
import com.weatherquips.app.ui.components.RefreshButton
import com.weatherquips.app.ui.components.StatRow
import com.weatherquips.app.ui.components.TemperatureGradientBar
import com.weatherquips.app.ui.components.WeatherGlyph
import com.weatherquips.app.ui.theme.LocalAccents
import com.weatherquips.app.ui.theme.temperatureColor
import com.weatherquips.app.utils.Formatters

/**
 * The pull-up detail panel: current conditions, a link to the radar map, the
 * hourly strip and the seven-day outlook — the same cards as the web app.
 *
 * A LazyColumn keeps the panel's scrolling co-operative with the bottom sheet's
 * drag, so a scroll that reaches the top hands the gesture back to the sheet.
 */
@Composable
fun WeatherDetailPanel(
    uiState: HomeUiState,
    weather: WeatherData,
    staleSinceMillis: Long?,
    onRefresh: () -> Unit,
    onOpenPrecipitationMap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .testTag(TAG_DETAIL_PANEL),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp,
            end = 16.dp,
            bottom = 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item("main") { MainWeatherCard(uiState, weather, staleSinceMillis, onRefresh) }
        item("map") { PrecipitationMapCard(onOpenPrecipitationMap) }
        item("today") { HourlyCard(uiState, weather) }
        item("week") { WeeklyCard(uiState, weather) }
    }
}

@Composable
private fun MainWeatherCard(
    uiState: HomeUiState,
    weather: WeatherData,
    staleSinceMillis: Long?,
    onRefresh: () -> Unit,
) {
    val unit = uiState.settings.temperatureUnit
    val accents = LocalAccents.current
    val rangeDescription = stringResource(
        R.string.high_low,
        Formatters.formatTemp(weather.temperatureMax, unit),
        Formatters.formatTemp(weather.temperatureMin, unit),
    )

    QuipCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = weather.location.lowercase(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(TAG_DETAIL_LOCATION),
                )
                RefreshButton(
                    onRefresh = onRefresh,
                    isRefreshing = uiState.isRefreshing,
                    iconSize = 18.dp,
                )
            }

            Text(
                text = Formatters.formatTemp(weather.temperature, unit),
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 68.sp, lineHeight = 70.sp),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag(TAG_DETAIL_TEMP),
            )

            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (uiState.isPokemonMode) {
                    PokeballGlyph(size = 16.dp)
                } else {
                    WeatherGlyph(condition = weather.condition, isDay = weather.isDay, size = 16.dp)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(
                        R.string.detail_feels_like,
                        weather.condition.id,
                        Formatters.formatTemp(weather.feelsLike, unit),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(TAG_DETAIL_CONDITION),
                )
            }

            if (staleSinceMillis != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.offline_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(16.dp))

            // Today's range: min ← gradient → max
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clearAndSetSemantics { contentDescription = rangeDescription },
            ) {
                Text(
                    text = Formatters.formatTemp(weather.temperatureMin, unit),
                    style = MaterialTheme.typography.titleSmall,
                    color = accents.cold,
                )
                Spacer(Modifier.width(12.dp))
                TemperatureGradientBar(modifier = Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                Text(
                    text = Formatters.formatTemp(weather.temperatureMax, unit),
                    style = MaterialTheme.typography.titleSmall,
                    color = accents.hot,
                )
            }

            Spacer(Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(16.dp)
                    .testTag(TAG_DETAIL_STATS),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        MinMaxChip(
                            iconRes = R.drawable.ic_arrow_up,
                            value = Formatters.formatTempDegrees(weather.temperatureMax, unit),
                            color = accents.hot,
                            modifier = Modifier.weight(1f),
                        )
                        MinMaxChip(
                            iconRes = R.drawable.ic_arrow_down,
                            value = Formatters.formatTempDegrees(weather.temperatureMin, unit),
                            color = accents.cold,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        StatRow(
                            label = stringResource(R.string.humidity),
                            value = stringResource(R.string.percent_value, weather.humidity),
                            modifier = Modifier.weight(1f),
                        )
                        StatRow(
                            label = stringResource(R.string.wind),
                            value = stringResource(
                                R.string.wind_value,
                                Formatters.formatWind(weather.windSpeed),
                            ),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        StatRow(
                            label = stringResource(R.string.uv_index),
                            value = Formatters.formatUv(weather.uvIndex),
                            modifier = Modifier.weight(1f),
                        )
                        StatRow(
                            label = stringResource(R.string.pressure),
                            value = weather.pressure.toString(),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        StatRow(
                            label = stringResource(R.string.precipitation),
                            value = stringResource(R.string.percent_value, weather.precipitationChance),
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun MinMaxChip(
    iconRes: Int,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = color,
        )
        Spacer(Modifier.width(8.dp))
        Text(text = value, style = MaterialTheme.typography.titleMedium, color = color)
    }
}

@Composable
private fun PrecipitationMapCard(onClick: () -> Unit) {
    QuipCard(modifier = Modifier.fillMaxWidth().testTag(TAG_MAP_CARD), onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_map),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(R.string.precipitation_map),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.live_radar_overlay),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HourlyCard(uiState: HomeUiState, weather: WeatherData) {
    val settings = uiState.settings
    val hours = remember(weather.hourlyForecast) { weather.hourlyForecast.take(12) }
    if (hours.isEmpty()) return

    val stats = remember(hours) {
        val temps = hours.map { it.temperature }
        val minT = temps.min()
        val maxT = temps.max()
        minT to ((maxT - minT).takeIf { it != 0.0 } ?: 1.0)
    }

    QuipCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(stringResource(R.string.today), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .testTag(TAG_HOURLY),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                hours.forEachIndexed { index, hour ->
                    val normalized = ((hour.temperature - stats.first) / stats.second).toFloat()
                    val color = temperatureColor(normalized)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(40.dp),
                    ) {
                        Text(
                            text = if (index == 0) {
                                stringResource(R.string.now)
                            } else {
                                Formatters.formatHourLabel(hour.time, settings.timeFormat)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        HourlyBar(fraction = normalized.coerceIn(0f, 1f), color = color)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = Formatters.formatTempDegrees(hour.temperature, settings.temperatureUnit),
                            style = MaterialTheme.typography.titleSmall,
                            color = color,
                        )
                    }
                }
            }
        }
    }
}

/** Vertical stem with a dot on top, the same encoding the web app used. */
@Composable
private fun HourlyBar(fraction: Float, color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .height(64.dp)
            .width(12.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeightFraction(fraction.coerceAtLeast(0.1f))
                .width(4.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.35f)),
        )
        Box(
            modifier = Modifier
                .offsetFromBottomFraction(fraction.coerceAtLeast(0.05f))
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
    }
}

/** Height as a fraction of the parent's max height. */
private fun Modifier.fillMaxHeightFraction(fraction: Float): Modifier = layout { measurable, constraints ->
    val height = (constraints.maxHeight * fraction).toInt().coerceAtLeast(1)
    val placeable = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
    layout(placeable.width, constraints.maxHeight) {
        placeable.placeRelative(0, constraints.maxHeight - height)
    }
}

/** Lifts the dot to the top of its stem. */
private fun Modifier.offsetFromBottomFraction(fraction: Float): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    val offset = (constraints.maxHeight * fraction).toInt()
    layout(placeable.width, constraints.maxHeight) {
        placeable.placeRelative(0, (constraints.maxHeight - offset - placeable.height / 2).coerceAtLeast(0))
    }
}

@Composable
private fun WeeklyCard(uiState: HomeUiState, weather: WeatherData) {
    val settings = uiState.settings
    val days = weather.dailyForecast
    if (days.isEmpty()) return

    val range = remember(days) {
        val weekMin = days.minOf { it.temperatureMin }
        val weekMax = days.maxOf { it.temperatureMax }
        weekMin to ((weekMax - weekMin).takeIf { it != 0.0 } ?: 1.0)
    }

    QuipCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(stringResource(R.string.next_7_days), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(20.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.testTag(TAG_DAILY),
            ) {
                days.forEach { day ->
                    val minNorm = ((day.temperatureMin - range.first) / range.second).toFloat()
                    val maxNorm = ((day.temperatureMax - range.first) / range.second).toFloat()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.width(80.dp)) {
                            Text(
                                text = day.day,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = Formatters.formatDate(day.date, settings.dateFormat),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = Formatters.formatTempDegrees(day.temperatureMin, settings.temperatureUnit),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = temperatureColor(minNorm),
                            modifier = Modifier.width(36.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        TemperatureGradientBar(
                            modifier = Modifier.weight(1f),
                            startFraction = minNorm,
                            endFraction = maxNorm.coerceAtLeast(minNorm + 0.04f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = Formatters.formatTempDegrees(day.temperatureMax, settings.temperatureUnit),
                            style = MaterialTheme.typography.titleSmall,
                            color = temperatureColor(maxNorm),
                            modifier = Modifier.width(36.dp),
                        )
                    }
                }
            }
        }
    }
}

const val TAG_DETAIL_PANEL = "detail-panel"
const val TAG_DETAIL_LOCATION = "detail-location"
const val TAG_DETAIL_TEMP = "detail-temp"
const val TAG_DETAIL_CONDITION = "detail-condition"
const val TAG_DETAIL_STATS = "detail-stats"
const val TAG_MAP_CARD = "detail-map-card"
const val TAG_HOURLY = "detail-hourly"
const val TAG_DAILY = "detail-daily"
