package com.weatherquips.app.domain.quotes

import com.weatherquips.app.domain.model.Coordinates
import com.weatherquips.app.domain.model.WeatherCondition
import kotlin.random.Random

/**
 * The hidden "Pallet Town" mode, ported from `client/src/lib/pokemon.ts`.
 *
 * Detection, stand-in coordinates, themed quotes and the decorative silhouettes
 * live here so the weather pipeline never needs to know about the Easter egg.
 */
object PokemonQuips {

    const val BANNER = "Welcome to Pallet Town, Trainer!"
    const val TITLE = "Weather Quips: Pokémon Edition"
    const val PALLET_TOWN_LABEL = "Pallet Town"

    /**
     * Pallet Town is fictional and can't be geocoded, so it maps to a calm
     * coastal spot in Japan — real, live weather while the mode is active.
     */
    val PALLET_TOWN_COORDS = Coordinates(latitude = 34.6794, longitude = 138.9476)

    fun isPalletTown(name: String): Boolean = name.trim().lowercase() == "pallet town"

    private val quotes: Map<WeatherCondition, List<String>> = mapOf(
        WeatherCondition.CLEAR to listOf(
            "Professor Oak says it's a perfect day to start your Pokémon journey!",
            "Pikachu is soaking up the sunshine outside.",
            "Clear skies ahead, Trainer. Adventure awaits!",
        ),
        WeatherCondition.HOT to listOf(
            "Charmander is thriving in this heat.",
            "Fire-types are feeling right at home today.",
            "Stay hydrated, Trainer — even Growlithe is panting.",
        ),
        WeatherCondition.CLOUDY to listOf(
            "A mysterious day for exploring tall grass.",
            "Somewhere, a wild Pokémon is waiting to be discovered.",
            "The weather may be gray, but adventure never is.",
        ),
        WeatherCondition.RAINY to listOf(
            "Looks like Squirtle called in some extra water support today.",
            "A rainy route is perfect for Water-type Pokémon.",
            "Don't forget your Pokédex, Trainer. Rain won't stop an adventure.",
        ),
        WeatherCondition.STORMY to listOf(
            "Pikachu seems unusually excited about today's forecast.",
            "Electric-type Pokémon are having the time of their lives.",
            "Watch out, Trainer. The skies are using Thunder!",
        ),
        WeatherCondition.SNOWY to listOf(
            "Looks like an Ice-type Pokémon convention outside.",
            "Perfect weather for a visit to Snowpoint City.",
            "Even Lapras might be feeling chilly today.",
        ),
        WeatherCondition.COLD to listOf(
            "Glaceon would feel right at home in this chill.",
            "Bundle up, Trainer — it's a frosty route ahead.",
            "Ice-type Pokémon are loving the cold snap.",
        ),
        WeatherCondition.FOGGY to listOf(
            "A wild Pokémon could be hiding just beyond the mist.",
            "The fog rolls in like a slow Confuse Ray.",
            "Tread carefully, Trainer — visibility is low on this route.",
        ),
        WeatherCondition.WINDY to listOf(
            "Pidgeotto are riding the air currents today.",
            "Flying-type Pokémon are out in full force.",
            "The wind carries the promise of a new adventure.",
        ),
    )

    private val subtitles: Map<WeatherCondition, String> = mapOf(
        WeatherCondition.CLEAR to "Route 1 is wide open. Go catch 'em all.",
        WeatherCondition.HOT to "Quick — grab a Fresh Water from the vending machine.",
        WeatherCondition.CLOUDY to "Keep your eyes on the tall grass.",
        WeatherCondition.RAINY to "Great fishing weather down by the route.",
        WeatherCondition.STORMY to "The Power Plant is buzzing with energy.",
        WeatherCondition.SNOWY to "Better pack some Burn Heals... and a coat.",
        WeatherCondition.COLD to "Mt. Coronet weather, right here at home.",
        WeatherCondition.FOGGY to "Bring a Pokémon that knows Defog.",
        WeatherCondition.WINDY to "A perfect tailwind for your next adventure.",
    )

    fun quote(condition: WeatherCondition, random: Random = Random.Default): Quip {
        val list = quotes[condition] ?: quotes.getValue(WeatherCondition.CLEAR)
        return Quip(
            quote = list[random.nextInt(list.size)],
            subtitle = subtitles[condition] ?: subtitles.getValue(WeatherCondition.CLEAR),
        )
    }

    fun quotesFor(condition: WeatherCondition): List<String> =
        quotes[condition] ?: quotes.getValue(WeatherCondition.CLEAR)

    enum class Silhouette { SPARK, LEAF, FLAME, SPLASH }

    fun randomSilhouette(random: Random = Random.Default): Silhouette =
        Silhouette.entries[random.nextInt(Silhouette.entries.size)]
}
