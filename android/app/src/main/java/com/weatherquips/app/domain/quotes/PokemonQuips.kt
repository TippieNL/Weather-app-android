package com.weatherquips.app.domain.quotes

import com.weatherquips.app.domain.model.Coordinates
import kotlin.random.Random

/**
 * The hidden "Pallet Town" mode, ported from `client/src/lib/pokemon.ts`.
 *
 * Detection, stand-in coordinates, themed quotes and the decorative silhouettes
 * live here so the weather pipeline never needs to know about the Easter egg.
 */
object PokemonQuips {

    /** A proper noun, the same in every language the games ship in. */
    const val PALLET_TOWN_LABEL = "Pallet Town"

    /**
     * Pallet Town is fictional and can't be geocoded, so it maps to a calm
     * coastal spot in Japan — real, live weather while the mode is active.
     */
    val PALLET_TOWN_COORDS = Coordinates(latitude = 34.6794, longitude = 138.9476)

    fun isPalletTown(name: String): Boolean = name.trim().lowercase() == "pallet town"

    // The themed lines live in res/values/pokemon.xml, one array per
    // condition and time of day, so they can be translated and added to
    // without touching this file.

    enum class Silhouette { SPARK, LEAF, FLAME, SPLASH }

    fun randomSilhouette(random: Random = Random.Default): Silhouette =
        Silhouette.entries[random.nextInt(Silhouette.entries.size)]
}
