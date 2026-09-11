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

    private val dayQuotes: Map<WeatherCondition, List<String>> = mapOf(
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

    private val daySubtitles: Map<WeatherCondition, String> = mapOf(
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

    /**
     * After dark. The daytime lines talk about sunshine and setting off on a
     * journey, which reads oddly at midnight.
     */
    private val nightQuotes: Map<WeatherCondition, List<String>> = mapOf(
        WeatherCondition.CLEAR to listOf(
            "Clear night. Perfect for spotting a Clefairy.",
            "The stars are out over Pallet Town, Trainer.",
            "Professor Oak is still awake in the lab, apparently.",
        ),
        WeatherCondition.HOT to listOf(
            "Even the night is warm. Fire-types are delighted.",
            "A muggy night on Route 1. Nobody is sleeping.",
            "Growlithe has given up and sprawled on the floor.",
        ),
        WeatherCondition.CLOUDY to listOf(
            "No stars tonight. The tall grass looks darker than usual.",
            "Something rustled out there. Probably a Rattata.",
            "A good night to stay in the Pokémon Center.",
        ),
        WeatherCondition.RAINY to listOf(
            "Rain on the roof all night. Squirtle approves.",
            "Water-types are having the time of their lives out there.",
            "A wet night for anyone caught between towns.",
        ),
        WeatherCondition.STORMY to listOf(
            "Thunder after dark. Pikachu is wide awake.",
            "The Power Plant is lit up from here.",
            "Electric-types do not believe in bedtime tonight.",
        ),
        WeatherCondition.SNOWY to listOf(
            "Snow falling in the dark. Very Snowpoint of it.",
            "Ice-types are out there enjoying the quiet.",
            "Everything will be buried by sunrise, Trainer.",
        ),
        WeatherCondition.COLD to listOf(
            "A freezing night. Glaceon is thriving, you are not.",
            "Cold enough to see your breath on Route 1.",
            "Even the Pokémon Center heating is struggling.",
        ),
        WeatherCondition.FOGGY to listOf(
            "Fog at night. Anything could be out in that.",
            "Visibility zero. Bring a Pokémon that knows Flash.",
            "The route signs have vanished entirely, Trainer.",
        ),
        WeatherCondition.WINDY to listOf(
            "The wind is rattling the windows all night.",
            "Flying-types are somewhere up there, enjoying it.",
            "A rough night to be camping between towns.",
        ),
    )

    private val nightSubtitles: Map<WeatherCondition, String> = mapOf(
        WeatherCondition.CLEAR to "Save your game and look up for a minute.",
        WeatherCondition.HOT to "Grab a Fresh Water. Sleep is optional.",
        WeatherCondition.CLOUDY to "Stay on the path after dark.",
        WeatherCondition.RAINY to "Good night for fishing, if you are brave.",
        WeatherCondition.STORMY to "Unplug the PC box. Just in case.",
        WeatherCondition.SNOWY to "Pack Burn Heals and a very thick coat.",
        WeatherCondition.COLD to "Mt. Coronet weather, right outside.",
        WeatherCondition.FOGGY to "Defog would be extremely useful right now.",
        WeatherCondition.WINDY to "Tie down the tent, Trainer.",
    )

    fun quote(
        condition: WeatherCondition,
        isDay: Boolean = true,
        random: Random = Random.Default,
    ): Quip {
        val list = quotesFor(condition, isDay)
        val subtitles = if (isDay) daySubtitles else nightSubtitles
        return Quip(
            quote = list[random.nextInt(list.size)],
            subtitle = subtitles[condition] ?: subtitles.getValue(WeatherCondition.CLEAR),
        )
    }

    fun quotesFor(condition: WeatherCondition, isDay: Boolean = true): List<String> {
        val quotes = if (isDay) dayQuotes else nightQuotes
        return quotes[condition] ?: quotes.getValue(WeatherCondition.CLEAR)
    }

    enum class Silhouette { SPARK, LEAF, FLAME, SPLASH }

    fun randomSilhouette(random: Random = Random.Default): Silhouette =
        Silhouette.entries[random.nextInt(Silhouette.entries.size)]
}
