package com.weatherquips.app

import com.weatherquips.app.domain.model.WeatherCodeMapper
import com.weatherquips.app.domain.model.WeatherCondition
import org.junit.Assert.assertEquals
import org.junit.Test

/** The condition mapping is the app's identity — it must match the web server exactly. */
class WeatherCodeMapperTest {

    @Test
    fun `clear sky maps by temperature`() {
        assertEquals(WeatherCondition.CLEAR, WeatherCodeMapper.map(0, 18.0))
        assertEquals(WeatherCondition.CLEAR, WeatherCodeMapper.map(1, 18.0))
        assertEquals(WeatherCondition.HOT, WeatherCodeMapper.map(0, 30.1))
        assertEquals(WeatherCondition.COLD, WeatherCodeMapper.map(0, 4.9))
        // Boundaries: exactly 30 and exactly 5 stay "clear".
        assertEquals(WeatherCondition.CLEAR, WeatherCodeMapper.map(0, 30.0))
        assertEquals(WeatherCondition.CLEAR, WeatherCodeMapper.map(0, 5.0))
    }

    @Test
    fun `wmo code ranges map to the app's conditions`() {
        assertEquals(WeatherCondition.CLOUDY, WeatherCodeMapper.map(2, 18.0))
        assertEquals(WeatherCondition.CLOUDY, WeatherCodeMapper.map(3, 18.0))
        assertEquals(WeatherCondition.FOGGY, WeatherCodeMapper.map(45, 18.0))
        assertEquals(WeatherCondition.FOGGY, WeatherCodeMapper.map(48, 18.0))
        assertEquals(WeatherCondition.RAINY, WeatherCodeMapper.map(61, 18.0))
        assertEquals(WeatherCondition.RAINY, WeatherCodeMapper.map(80, 18.0))
        assertEquals(WeatherCondition.SNOWY, WeatherCodeMapper.map(71, -2.0))
        assertEquals(WeatherCondition.SNOWY, WeatherCodeMapper.map(85, -2.0))
        assertEquals(WeatherCondition.STORMY, WeatherCodeMapper.map(95, 18.0))
        assertEquals(WeatherCondition.STORMY, WeatherCodeMapper.map(99, 18.0))
    }

    @Test
    fun `unknown codes fall back to cloudy`() {
        assertEquals(WeatherCondition.CLOUDY, WeatherCodeMapper.map(120, 18.0))
        assertEquals(WeatherCondition.CLOUDY, WeatherCodeMapper.map(-1, 18.0))
    }

    @Test
    fun `strong wind overrides everything but storms`() {
        assertEquals(
            WeatherCondition.WINDY,
            WeatherCodeMapper.applyWindOverride(WeatherCondition.CLEAR, 41.0),
        )
        assertEquals(
            WeatherCondition.CLEAR,
            WeatherCodeMapper.applyWindOverride(WeatherCondition.CLEAR, 40.0),
        )
        assertEquals(
            WeatherCondition.STORMY,
            WeatherCodeMapper.applyWindOverride(WeatherCondition.STORMY, 90.0),
        )
    }

    @Test
    fun `openweathermap ids normalize to wmo codes`() {
        assertEquals(95, WeatherCodeMapper.fromOpenWeatherMapId(202))
        assertEquals(61, WeatherCodeMapper.fromOpenWeatherMapId(500))
        assertEquals(71, WeatherCodeMapper.fromOpenWeatherMapId(601))
        assertEquals(45, WeatherCodeMapper.fromOpenWeatherMapId(741))
        assertEquals(0, WeatherCodeMapper.fromOpenWeatherMapId(800))
        assertEquals(2, WeatherCodeMapper.fromOpenWeatherMapId(804))
    }

    @Test
    fun `weatherapi codes normalize to wmo codes`() {
        assertEquals(0, WeatherCodeMapper.fromWeatherApiCode(1000))
        assertEquals(2, WeatherCodeMapper.fromWeatherApiCode(1006))
        assertEquals(45, WeatherCodeMapper.fromWeatherApiCode(1030))
        assertEquals(61, WeatherCodeMapper.fromWeatherApiCode(1183))
        assertEquals(71, WeatherCodeMapper.fromWeatherApiCode(1210))
        assertEquals(95, WeatherCodeMapper.fromWeatherApiCode(1276))
    }
}
