package com.weatherquips.app.domain.model

/**
 * WMO weather-code → app condition mapping, ported verbatim from
 * `mapWeatherCode()` in `server/routes.ts`. The other providers normalize their
 * own codes into WMO codes first so this stays the single source of truth.
 */
object WeatherCodeMapper {

    fun map(code: Int, temperatureCelsius: Double): WeatherCondition = when {
        code == 0 || code == 1 -> when {
            temperatureCelsius > 30 -> WeatherCondition.HOT
            temperatureCelsius < 5 -> WeatherCondition.COLD
            else -> WeatherCondition.CLEAR
        }
        code == 2 || code == 3 -> WeatherCondition.CLOUDY
        code in 45..48 -> WeatherCondition.FOGGY
        code in 51..67 -> WeatherCondition.RAINY
        code in 71..77 -> WeatherCondition.SNOWY
        code in 80..82 -> WeatherCondition.RAINY
        code in 85..86 -> WeatherCondition.SNOWY
        code in 95..99 -> WeatherCondition.STORMY
        else -> WeatherCondition.CLOUDY
    }

    /**
     * Strong wind takes over the condition unless it's already stormy — the same
     * override the web server applied after mapping the code.
     */
    fun applyWindOverride(condition: WeatherCondition, windSpeedKmh: Double): WeatherCondition =
        if (windSpeedKmh > 40 && condition != WeatherCondition.STORMY) {
            WeatherCondition.WINDY
        } else {
            condition
        }

    /** OpenWeatherMap condition id → WMO code, as in `fetchOpenWeatherMap()`. */
    fun fromOpenWeatherMapId(id: Int): Int = when {
        id in 200..299 -> 95
        id in 300..599 -> 61
        id in 600..699 -> 71
        id in 700..799 -> 45
        id == 800 -> 0
        id > 800 -> 2
        else -> 0
    }

    /** WeatherAPI condition code → WMO code, as in `fetchWeatherAPI()`. */
    fun fromWeatherApiCode(code: Int): Int = when {
        code == 1000 -> 0
        code in 1003..1009 -> 2
        code in 1030..1035 -> 45
        code in 1063..1201 -> 61
        code in 1204..1264 -> 71
        code >= 1273 -> 95
        else -> 0
    }
}
