package com.weatherquips.app

import com.weatherquips.app.domain.model.DateFormat
import com.weatherquips.app.domain.model.TemperatureUnit
import com.weatherquips.app.domain.model.TimeFormat
import com.weatherquips.app.utils.Formatters
import org.junit.Assert.assertEquals
import org.junit.Test

/** Formatting parity with the web app's `convertTemp`/`formatTemp`/`formatTime`/`formatDate`. */
class FormattersTest {

    @Test
    fun `celsius is rounded, not truncated`() {
        assertEquals(21, Formatters.convertTemp(20.6, TemperatureUnit.CELSIUS))
        assertEquals(20, Formatters.convertTemp(20.4, TemperatureUnit.CELSIUS))
        assertEquals(-5, Formatters.convertTemp(-5.2, TemperatureUnit.CELSIUS))
    }

    @Test
    fun `fahrenheit conversion matches the web formula`() {
        assertEquals(32, Formatters.convertTemp(0.0, TemperatureUnit.FAHRENHEIT))
        assertEquals(212, Formatters.convertTemp(100.0, TemperatureUnit.FAHRENHEIT))
        assertEquals(70, Formatters.convertTemp(21.1, TemperatureUnit.FAHRENHEIT))
    }

    @Test
    fun `formatTemp appends the right unit`() {
        assertEquals("15°C", Formatters.formatTemp(15.0, TemperatureUnit.CELSIUS))
        assertEquals("59°F", Formatters.formatTemp(15.0, TemperatureUnit.FAHRENHEIT))
        assertEquals("15°", Formatters.formatTempDegrees(15.0, TemperatureUnit.CELSIUS))
    }

    @Test
    fun `24 hour times pass through untouched`() {
        assertEquals("14:00", Formatters.formatTime("14:00", TimeFormat.H24))
        assertEquals("00:00", Formatters.formatTime("00:00", TimeFormat.H24))
    }

    @Test
    fun `12 hour conversion handles midnight, noon and afternoon`() {
        assertEquals("12:00 AM", Formatters.formatTime("00:00", TimeFormat.H12))
        assertEquals("12:00 PM", Formatters.formatTime("12:00", TimeFormat.H12))
        assertEquals("1:30 PM", Formatters.formatTime("13:30", TimeFormat.H12))
        assertEquals("9:00 AM", Formatters.formatTime("09:00", TimeFormat.H12))
    }

    @Test
    fun `unparseable time is returned unchanged rather than crashing`() {
        assertEquals("later", Formatters.formatTime("later", TimeFormat.H12))
    }

    @Test
    fun `hour labels are compact`() {
        assertEquals("14", Formatters.formatHourLabel("14:00", TimeFormat.H24))
        assertEquals("2p", Formatters.formatHourLabel("14:00", TimeFormat.H12))
        assertEquals("9a", Formatters.formatHourLabel("09:00", TimeFormat.H12))
    }

    @Test
    fun `dates follow the selected format`() {
        assertEquals("06/09", Formatters.formatDate("2026-09-06", DateFormat.DD_MM))
        assertEquals("09/06", Formatters.formatDate("2026-09-06", DateFormat.MM_DD))
        assertEquals("2026-09-06", Formatters.formatDate("2026-09-06", DateFormat.ISO))
        assertEquals("nonsense", Formatters.formatDate("nonsense", DateFormat.DD_MM))
    }

    @Test
    fun `uv index keeps one decimal only when it has one`() {
        assertEquals("0", Formatters.formatUv(0.0))
        assertEquals("5", Formatters.formatUv(5.0))
        assertEquals("5.4", Formatters.formatUv(5.44))
        assertEquals("5.5", Formatters.formatUv(5.45))
    }

    @Test
    fun `wind speed is rounded to whole kmh`() {
        assertEquals(11, Formatters.formatWind(11.4))
        assertEquals(12, Formatters.formatWind(11.6))
    }
}
