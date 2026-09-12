package com.weatherquips.app

import com.weatherquips.app.data.model.BrightskyGridPosition
import com.weatherquips.app.data.model.BrightskyRadarFrame
import com.weatherquips.app.data.model.BrightskyRadarResponse
import com.weatherquips.app.data.repository.DwdRadar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * The DWD radar feed.
 *
 * The widget exists to disagree with a flat model forecast when radar can see
 * rain, so the sampling and the unit conversion are the part worth pinning
 * down exactly.
 */
class RadarNowcastTest {

    private val now: Instant = Instant.parse("2026-09-12T00:10:00Z")

    /** A 3x3 grid of hundredths of a millimetre per five minutes; dry by default. */
    private fun frame(minutesFromNow: Long, vararg cells: Int) = BrightskyRadarFrame(
        timestamp = now.plusSeconds(minutesFromNow * 60).toString().replace("Z", "+00:00"),
        precipitation5 = (if (cells.isEmpty()) IntArray(9) else cells).toList().chunked(3),
    )

    private fun response(vararg frames: BrightskyRadarFrame) = BrightskyRadarResponse(
        radar = frames.toList(),
        position = BrightskyGridPosition(x = 1.046, y = 1.436),
    )

    @Test
    fun `hundredths of a millimetre per five minutes become millimetres per hour`() {
        // 15 hundredths in five minutes is 0.15 mm, which is 1.8 mm/h.
        val points = DwdRadar.points(
            response(frame(0, 0, 0, 0, 0, 15, 0, 0, 0, 0)),
            now,
            utcOffsetSeconds = 0,
        )
        assertEquals(1.8, points.single().millimetresPerHour, 0.0001)
    }

    @Test
    fun `a dry grid reads as dry, not as a fraction of a millimetre`() {
        val points = DwdRadar.points(
            response(frame(0, 0, 0, 0, 0, 0, 0, 0, 0, 0)),
            now,
            utcOffsetSeconds = 0,
        )
        assertEquals(0.0, points.single().millimetresPerHour, 0.0001)
    }

    @Test
    fun `a shower one cell away counts, because it is a kilometre away`() {
        // Nothing in the centre cell; a core in the corner of the 3x3.
        val points = DwdRadar.points(
            response(frame(0, 25, 0, 0, 0, 0, 0, 0, 0, 0)),
            now,
            utcOffsetSeconds = 0,
        )
        assertEquals(3.0, points.single().millimetresPerHour, 0.0001)
    }

    @Test
    fun `frames are placed by their timestamp, not their position in the list`() {
        val points = DwdRadar.points(
            response(frame(-10), frame(-5), frame(0), frame(5), frame(35)),
            now,
            utcOffsetSeconds = 0,
        )
        assertEquals(listOf(-10, -5, 0, 5, 35), points.map { it.minutesFromNow })
    }

    @Test
    fun `radar is stamped in UTC but labelled where the weather is`() {
        val berlin = DwdRadar.points(response(frame(0)), now, utcOffsetSeconds = 2 * 3600)
        assertEquals("02:10", berlin.single().time)

        val utc = DwdRadar.points(response(frame(0)), now, utcOffsetSeconds = 0)
        assertEquals("00:10", utc.single().time)
    }

    @Test
    fun `history beyond the window the widget can use is dropped`() {
        val points = DwdRadar.points(
            response(frame(-120), frame(-90), frame(-30), frame(0), frame(115)),
            now,
            utcOffsetSeconds = 0,
        )
        assertEquals(listOf(-30, 0, 115), points.map { it.minutesFromNow })
    }

    @Test
    fun `a frame with no grid at all is skipped rather than read as dry`() {
        val empty = BrightskyRadarFrame(
            timestamp = now.toString().replace("Z", "+00:00"),
            precipitation5 = emptyList(),
        )
        assertTrue(DwdRadar.points(response(empty), now, utcOffsetSeconds = 0).isEmpty())
    }

    @Test
    fun `a reply with no grid position yields nothing rather than guessing`() {
        val orphaned = BrightskyRadarResponse(radar = listOf(frame(0)), position = null)
        assertTrue(DwdRadar.points(orphaned, now, utcOffsetSeconds = 0).isEmpty())
    }

    @Test
    fun `an unparseable timestamp drops its frame and leaves the rest alone`() {
        val points = DwdRadar.points(
            response(
                frame(-5),
                BrightskyRadarFrame("not a time", List(3) { List(3) { 0 } }),
                frame(5),
            ),
            now,
            utcOffsetSeconds = 0,
        )
        assertEquals(listOf(-5, 5), points.map { it.minutesFromNow })
    }

    @Test
    fun `the request reaches two hours past now`() {
        assertEquals("2026-09-12T02:10:00Z", DwdRadar.lastDate(now))
    }
}
