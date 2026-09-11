package com.weatherquips.app

import com.weatherquips.app.data.repository.BuienradarNowcast
import com.weatherquips.app.domain.model.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Buienradar's radar feed.
 *
 * The widget exists to disagree with a flat model forecast when it is
 * actually raining, so the arithmetic that turns a reflectivity byte into
 * millimetres per hour is worth pinning down exactly.
 */
class RadarNowcastTest {

    @Test
    fun `the reflectivity scale matches the published conversion`() {
        // value 109 is 1 mm/h, and every 32 steps is a factor of ten.
        val points = BuienradarNowcast.parse(
            """
            077|18:00
            109|18:05
            141|18:10
            """.trimIndent(),
        )
        assertEquals(listOf(0.1, 1.0, 10.0), points.map { it.millimetresPerHour })
    }

    @Test
    fun `a zero reading is dry, not a thousandth of a millimetre`() {
        val points = BuienradarNowcast.parse("000|18:00\n000|18:05\n")
        assertTrue(points.all { it.millimetresPerHour == 0.0 })
    }

    @Test
    fun `position in the feed is the offset, so no timezone is parsed`() {
        // The clock times are Dutch local; the phone need not be.
        val points = BuienradarNowcast.parse(
            "000|23:55\n000|00:00\n000|00:05\n000|00:10\n",
        )
        assertEquals(listOf(0, 5, 10, 15), points.map { it.minutesFromNow })
        assertEquals(listOf("23:55", "00:00", "00:05", "00:10"), points.map { it.time })
    }

    @Test
    fun `the out-of-coverage reply parses to nothing rather than garbage`() {
        // What the endpoint actually answers outside the Low Countries.
        val points = BuienradarNowcast.parse(
            "Not found: location must be inside the Netherlands or Belgium.",
        )
        assertTrue(points.isEmpty())
    }

    @Test
    fun `malformed lines are dropped without shifting the ones around them`() {
        val points = BuienradarNowcast.parse(
            """
            000|18:00
            oops
            109|18:10
            999
            |18:20
            000|18:25
            """.trimIndent(),
        )
        assertEquals(listOf("18:00", "18:10", "18:25"), points.map { it.time })
        // 18:10 is the third line, so ten minutes out — not five.
        assertEquals(listOf(0, 10, 25), points.map { it.minutesFromNow })
    }

    @Test
    fun `coverage is claimed for the Low Countries and nowhere else`() {
        assertTrue(BuienradarNowcast.covers(Coordinates(52.99, 6.56)))   // Assen
        assertTrue(BuienradarNowcast.covers(Coordinates(50.85, 4.35)))   // Brussels
        assertFalse(BuienradarNowcast.covers(Coordinates(51.51, -0.13))) // London
        assertFalse(BuienradarNowcast.covers(Coordinates(52.52, 13.40))) // Berlin
        assertFalse(BuienradarNowcast.covers(Coordinates(-33.87, 151.21)))
    }
}
