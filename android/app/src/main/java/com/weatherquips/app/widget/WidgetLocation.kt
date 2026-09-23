package com.weatherquips.app.widget

import com.weatherquips.app.domain.model.AppSettings
import com.weatherquips.app.domain.model.Coordinates
import com.weatherquips.app.domain.model.LocationMode

/**
 * Where the widget's background refresh should fetch for.
 *
 * The device's own position is very nearly unavailable here. Since Android 10
 * an app without `ACCESS_BACKGROUND_LOCATION` gets nothing at all from the
 * location APIs while it has no visible process — not an error, just null —
 * and a weather widget has no business asking the user for a permission that
 * serious.
 *
 * So the attempt is still made, because it does pay off while the app has
 * been open recently, and the place the app last showed stands in when it
 * does not. Without that fallback the refresh simply gave up, the cache aged
 * past the graph's window, and the widget went blank until the app was next
 * opened by hand.
 */
internal object WidgetLocation {

    suspend fun resolve(
        settings: AppSettings,
        deviceFix: suspend () -> Coordinates?,
        lastKnownPlace: suspend () -> Coordinates?,
    ): Coordinates? {
        if (settings.locationMode == LocationMode.MANUAL) {
            settings.manualCoords?.let { return it }
        } else {
            deviceFix()?.let { return it }
        }
        return lastKnownPlace()
    }
}
