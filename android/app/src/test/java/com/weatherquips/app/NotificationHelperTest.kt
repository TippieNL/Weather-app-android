package com.weatherquips.app

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.weatherquips.app.domain.model.Coordinates
import com.weatherquips.app.notifications.NotificationHelper
import com.weatherquips.app.notifications.PrecipitationAlert
import com.weatherquips.app.notifications.PrecipitationKind
import com.weatherquips.app.ui.navigation.Routes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** What the notification actually looks like once posted. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationHelperTest {

    private lateinit var context: Context
    private lateinit var helper: NotificationHelper

    private val alert = PrecipitationAlert(
        kind = PrecipitationKind.RAIN,
        headline = "The sky is about to ruin this",
        summary = "96% chance of rain, heaviest around 16:00",
        detail = "96% chance of rain today. Expect it between 14:00 and 18:00. " +
            "It peaks at 96% around 16:00.\n\nTake the umbrella you will leave somewhere.",
        location = "Assen",
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        helper = NotificationHelper(context)
    }

    private fun posted(): Notification? {
        val manager = context.getSystemService(NotificationManager::class.java)
        return shadowOf(manager).allNotifications.firstOrNull()
    }

    @Test
    fun `the joke is the title and the figures are the visible line`() {
        assertTrue(helper.notifyPrecipitation(alert))

        val notification = posted()
        assertNotNull(notification)
        val extras = notification!!.extras
        assertEquals("The sky is about to ruin this", extras.getString(Notification.EXTRA_TITLE))
        assertEquals(
            "96% chance of rain, heaviest around 16:00",
            extras.getString(Notification.EXTRA_TEXT),
        )
    }

    @Test
    fun `expanding shows the window, the peak and the parting shot`() {
        helper.notifyPrecipitation(alert)

        val bigText = posted()!!.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        assertNotNull("no expanded text", bigText)
        assertTrue(bigText!!.contains("between 14:00 and 18:00"))
        assertTrue(bigText.contains("peaks at 96%"))
        assertTrue(bigText.contains("Take the umbrella you will leave somewhere."))
    }

    @Test
    fun `the place is shown in the header`() {
        helper.notifyPrecipitation(alert)
        assertEquals("Assen", posted()!!.extras.getString(Notification.EXTRA_SUB_TEXT))
    }

    @Test
    fun `each kind of weather brings its own icon`() {
        val icons = PrecipitationKind.entries.map { kind ->
            context.getSystemService(NotificationManager::class.java).cancelAll()
            helper.notifyPrecipitation(alert.copy(kind = kind))
            posted()!!.smallIcon.resId
        }
        assertEquals("icons should differ per kind", icons.size, icons.distinct().size)
    }

    @Test
    fun `an alert with coordinates offers to open the radar there`() {
        helper.notifyPrecipitation(alert, Coordinates(52.99, 6.56))

        val actions = posted()!!.actions
        assertNotNull("no action on the notification", actions)
        assertEquals(1, actions.size)
        assertEquals("See the radar", actions[0].title.toString())

        val intent = shadowOf(actions[0].actionIntent).savedIntent
        assertEquals(
            NotificationHelper.DESTINATION_RADAR,
            intent.getStringExtra(NotificationHelper.EXTRA_DESTINATION),
        )
        assertEquals(52.99, intent.getDoubleExtra(NotificationHelper.EXTRA_LATITUDE, 0.0), 0.001)
        assertEquals(6.56, intent.getDoubleExtra(NotificationHelper.EXTRA_LONGITUDE, 0.0), 0.001)
    }

    @Test
    fun `without coordinates there is nothing to open, so no action`() {
        helper.notifyPrecipitation(alert, coordinates = null)
        val actions = posted()!!.actions
        assertTrue("unexpected action", actions == null || actions.isEmpty())
    }

    @Test
    fun `tapping the body just opens the app, not the radar`() {
        helper.notifyPrecipitation(alert, Coordinates(52.99, 6.56))

        val intent = shadowOf(posted()!!.contentIntent).savedIntent
        assertFalse(intent.hasExtra(NotificationHelper.EXTRA_DESTINATION))
    }

    @Test
    fun `a later alert replaces the previous one rather than stacking`() {
        helper.notifyPrecipitation(alert)
        helper.notifyPrecipitation(alert.copy(headline = "Rain incoming. Act surprised."))

        val manager = context.getSystemService(NotificationManager::class.java)
        val all = shadowOf(manager).allNotifications
        assertEquals(1, all.size)
        assertEquals(
            "Rain incoming. Act surprised.",
            all.first().extras.getString(Notification.EXTRA_TITLE),
        )
    }

    @Test
    fun `the radar action lands on the screen the app expects`() {
        // The two halves are written in different files; a mistyped extra key
        // would pass either test alone and still open nothing.
        helper.notifyPrecipitation(alert, Coordinates(52.99, 6.56))
        val intent = shadowOf(posted()!!.actions[0].actionIntent).savedIntent

        val destination = intent.readDestination()
        assertNotNull("the app could not read its own intent", destination)
        assertEquals(Routes.PRECIPITATION, destination!!.route)
        assertEquals(52.99, destination.coordinates?.latitude ?: 0.0, 0.001)
        assertEquals(6.56, destination.coordinates?.longitude ?: 0.0, 0.001)
    }

    @Test
    fun `an ordinary tap asks for no particular screen`() {
        helper.notifyPrecipitation(alert, Coordinates(52.99, 6.56))
        val intent = shadowOf(posted()!!.contentIntent).savedIntent
        assertEquals(null, intent.readDestination())
    }

    @Test
    fun `nothing is posted without permission`() {
        shadowOf(context as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertFalse(helper.notifyPrecipitation(alert))
    }
}
