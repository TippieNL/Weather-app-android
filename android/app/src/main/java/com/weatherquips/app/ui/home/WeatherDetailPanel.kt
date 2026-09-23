package com.weatherquips.app.ui.home

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weatherquips.app.R
import com.weatherquips.app.domain.model.HourlyForecast
import com.weatherquips.app.domain.model.WeatherData
import com.weatherquips.app.ui.components.PokeballGlyph
import com.weatherquips.app.ui.components.QuipCard
import com.weatherquips.app.ui.components.RefreshButton
import com.weatherquips.app.ui.components.TemperatureRangeBar
import com.weatherquips.app.ui.components.WeatherGlyph
import com.weatherquips.app.ui.theme.temperatureColorFor
import com.weatherquips.app.utils.Formatters
import kotlin.math.max

/**
 * The pull-up detail panel: current conditions, a link to the radar map, the
 * hourly strip and the seven-day outlook.
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
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .testTag(TAG_DETAIL_PANEL),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item("main") { MainWeatherCard(uiState, weather, staleSinceMillis, onRefresh) }
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

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = Formatters.formatTemp(weather.temperature, unit),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 68.sp,
                        lineHeight = 72.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag(TAG_DETAIL_TEMP),
                )
            }

            Spacer(Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (uiState.isPokemonMode) {
                    PokeballGlyph(size = 16.dp)
                } else {
                    WeatherGlyph(condition = weather.condition, isDay = weather.isDay, size = 16.dp)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = weather.condition.id,
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

            Spacer(Modifier.height(20.dp))

            TodayRange(weather = weather, uiState = uiState)

            Spacer(Modifier.height(20.dp))

            StatsGrid(weather = weather, uiState = uiState)
        }
    }
}

/**
 * Today's low→high with a marker for right now.
 *
 * This replaces two separate elements that between them said less: a decorative
 * full-width gradient that looked the same whatever the weather, and a row
 * underneath repeating the very same two numbers.
 */
@Composable
private fun TodayRange(weather: WeatherData, uiState: HomeUiState) {
    val unit = uiState.settings.temperatureUnit
    // The current reading can sit outside the forecast band; widen rather than clip.
    val low = minOf(weather.temperatureMin, weather.temperature)
    val high = maxOf(weather.temperatureMax, weather.temperature)

    val description = stringResource(
        R.string.range_description,
        Formatters.formatTemp(weather.temperatureMin, unit),
        Formatters.formatTemp(weather.temperatureMax, unit),
        Formatters.formatTemp(weather.temperature, unit),
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TAG_DETAIL_RANGE)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = Formatters.formatTempDegrees(weather.temperatureMin, unit),
                style = MaterialTheme.typography.titleMedium,
                color = temperatureColorFor(weather.temperatureMin),
                textAlign = TextAlign.End,
                modifier = Modifier.width(44.dp),
            )
            Spacer(Modifier.width(12.dp))
            TemperatureRangeBar(
                minCelsius = weather.temperatureMin,
                maxCelsius = weather.temperatureMax,
                scaleMinCelsius = low,
                scaleMaxCelsius = high,
                markerCelsius = weather.temperature,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = Formatters.formatTempDegrees(weather.temperatureMax, unit),
                style = MaterialTheme.typography.titleMedium,
                color = temperatureColorFor(weather.temperatureMax),
                modifier = Modifier.width(44.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.today_low),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.width(44.dp),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.today_high),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Six uniform tiles. The old grid mixed three different alignments — icon rows,
 * label/value rows, and one orphaned cell with a gap beside it — which made it
 * hard to scan for any single figure.
 */
@Composable
private fun StatsGrid(weather: WeatherData, uiState: HomeUiState) {
    val unit = uiState.settings.temperatureUnit
    val tiles = listOf(
        StatTile(
            iconRes = R.drawable.ic_thermometer,
            label = stringResource(R.string.feels_like),
            value = Formatters.formatTemp(weather.feelsLike, unit),
        ),
        StatTile(
            iconRes = R.drawable.ic_umbrella,
            label = stringResource(R.string.rain_chance),
            value = stringResource(R.string.percent_value, weather.precipitationChance),
        ),
        StatTile(
            iconRes = R.drawable.ic_droplet,
            label = stringResource(R.string.humidity),
            value = stringResource(R.string.percent_value, weather.humidity),
        ),
        StatTile(
            iconRes = R.drawable.ic_weather_windy,
            label = stringResource(R.string.wind),
            value = stringResource(R.string.wind_value, Formatters.formatWind(weather.windSpeed)),
        ),
        StatTile(
            iconRes = R.drawable.ic_weather_clear_day,
            label = stringResource(R.string.uv_index),
            value = Formatters.formatUv(weather.uvIndex),
        ),
        StatTile(
            iconRes = R.drawable.ic_gauge,
            label = stringResource(R.string.pressure),
            value = stringResource(R.string.pressure_value, weather.pressure),
        ),
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(4.dp)
            .testTag(TAG_DETAIL_STATS),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                row.forEach { tile ->
                    StatTileCell(tile = tile, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private data class StatTile(
    @DrawableRes val iconRes: Int,
    val label: String,
    val value: String,
)

@Composable
private fun StatTileCell(tile: StatTile, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .clearAndSetSemantics { contentDescription = "${tile.label}: ${tile.value}" },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(tile.iconRes),
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = tile.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Text(
            text = tile.value,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
private fun HourlyCard(uiState: HomeUiState, weather: WeatherData) {
    val settings = uiState.settings
    val hours = remember(weather.hourlyForecast) { weather.hourlyForecast.take(12) }
    if (hours.isEmpty()) return

    // A quiet evening can vary by a single degree. Scaling to whatever happens
    // to be on screen turned that into a full-height swing; holding a minimum
    // span keeps a flat night looking flat.
    val scale = remember(hours) {
        val temps = hours.map { it.temperature }
        val low = temps.min()
        val high = temps.max()
        val padding = max(0.0, MIN_HOURLY_SPAN_CELSIUS - (high - low)) / 2
        (low - padding) to (high + padding)
    }
    val showPrecipitation = remember(hours) { hours.any { it.precipitationChance > 0 } }

    QuipCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 20.dp)) {
            Text(
                text = stringResource(R.string.today),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .testTag(TAG_HOURLY),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                hours.forEachIndexed { index, hour ->
                    HourColumn(
                        hour = hour,
                        isNow = index == 0,
                        scaleLow = scale.first,
                        scaleHigh = scale.second,
                        showPrecipitation = showPrecipitation,
                        uiState = uiState,
                    )
                }
            }
        }
    }
}

@Composable
private fun HourColumn(
    hour: HourlyForecast,
    isNow: Boolean,
    scaleLow: Double,
    scaleHigh: Double,
    showPrecipitation: Boolean,
    uiState: HomeUiState,
) {
    val settings = uiState.settings
    val span = (scaleHigh - scaleLow).takeIf { it > 0.0 } ?: 1.0
    val fraction = ((hour.temperature - scaleLow) / span).coerceIn(0.0, 1.0).toFloat()
    val color = temperatureColorFor(hour.temperature)
    val label = if (isNow) {
        stringResource(R.string.now)
    } else {
        Formatters.formatHourLabel(hour.time, settings.timeFormat)
    }
    val temperature = Formatters.formatTempDegrees(hour.temperature, settings.temperatureUnit)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(40.dp)
            .clearAndSetSemantics {
                contentDescription = if (hour.precipitationChance > 0) {
                    "$label: $temperature, ${hour.precipitationChance}% chance of rain"
                } else {
                    "$label: $temperature"
                }
            },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (isNow) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = temperature,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Spacer(Modifier.height(6.dp))
        HourlyStem(fraction = fraction, color = color)
        if (showPrecipitation) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (hour.precipitationChance > 0) "${hour.precipitationChance}%" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/**
 * A dot on a track: the dot's height is the hour's temperature within the
 * strip's scale, and the faint track behind it gives every hour the same
 * reference so the dots can actually be compared.
 */
@Composable
private fun HourlyStem(fraction: Float, color: Color) {
    Box(
        modifier = Modifier
            .height(56.dp)
            .width(12.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeightFraction(1f)
                .width(3.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Box(
            modifier = Modifier
                .offsetFromBottomFraction(fraction)
                .size(11.dp)
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

/** Positions the dot at [fraction] of the way up its track. */
private fun Modifier.offsetFromBottomFraction(fraction: Float): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    val travel = constraints.maxHeight - placeable.height
    val offset = (travel * fraction).toInt().coerceIn(0, travel)
    layout(placeable.width, constraints.maxHeight) {
        placeable.placeRelative(0, travel - offset)
    }
}

@Composable
private fun WeeklyCard(uiState: HomeUiState, weather: WeatherData) {
    val settings = uiState.settings
    val days = weather.dailyForecast
    if (days.isEmpty()) return

    val scale = remember(days) {
        val weekLow = days.minOf { it.temperatureMin }
        val weekHigh = days.maxOf { it.temperatureMax }
        val padding = max(0.0, MIN_WEEKLY_SPAN_CELSIUS - (weekHigh - weekLow)) / 2
        (weekLow - padding) to (weekHigh + padding)
    }

    QuipCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.next_7_days),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(16.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.testTag(TAG_DAILY),
            ) {
                days.forEach { day ->
                    val low = Formatters.formatTempDegrees(day.temperatureMin, settings.temperatureUnit)
                    val high = Formatters.formatTempDegrees(day.temperatureMax, settings.temperatureUnit)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clearAndSetSemantics {
                                contentDescription = "${day.day}: $low to $high"
                            },
                    ) {
                        Column(modifier = Modifier.width(84.dp)) {
                            Text(
                                text = day.day,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                            )
                            Text(
                                text = Formatters.formatDate(day.date, settings.dateFormat),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        Text(
                            text = low,
                            style = MaterialTheme.typography.titleSmall,
                            color = temperatureColorFor(day.temperatureMin),
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            modifier = Modifier.width(38.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        TemperatureRangeBar(
                            minCelsius = day.temperatureMin,
                            maxCelsius = day.temperatureMax,
                            scaleMinCelsius = scale.first,
                            scaleMaxCelsius = scale.second,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = high,
                            style = MaterialTheme.typography.titleSmall,
                            color = temperatureColorFor(day.temperatureMax),
                            maxLines = 1,
                            modifier = Modifier.width(38.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Smallest temperature window the hourly strip will draw across. */
private const val MIN_HOURLY_SPAN_CELSIUS = 6.0

/** Smallest temperature window the weekly bars will draw across. */
private const val MIN_WEEKLY_SPAN_CELSIUS = 8.0

const val TAG_DETAIL_PANEL = "detail-panel"
const val TAG_DETAIL_LOCATION = "detail-location"
const val TAG_DETAIL_TEMP = "detail-temp"
const val TAG_DETAIL_CONDITION = "detail-condition"
const val TAG_DETAIL_RANGE = "detail-range"
const val TAG_DETAIL_STATS = "detail-stats"
const val TAG_HOURLY = "detail-hourly"
const val TAG_DAILY = "detail-daily"
