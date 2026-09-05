import type { WeatherCondition } from "@shared/schema";

/**
 * Pokémon Easter Egg module.
 *
 * Self-contained logic for the hidden "Pallet Town" mode: detection, the
 * fictional town's stand-in coordinates (so real weather still loads), themed
 * quotes per weather condition, decorative silhouettes, and a short jingle.
 *
 * Designed to be extended later (backgrounds, music, more locations) without
 * touching the weather-fetching pipeline.
 */

export const POKEMON_BANNER = "Welcome to Pallet Town, Trainer!";
export const POKEMON_TITLE = "Weather Quips: Pokémon Edition";

/**
 * Pallet Town is fictional, so it can't be geocoded. We map it to a calm
 * coastal location in Japan (the region that inspired the games) so the app
 * still shows real, live weather while Pokémon mode is active.
 */
export const PALLET_TOWN_COORDS = { latitude: 34.6794, longitude: 138.9476 };
export const PALLET_TOWN_LABEL = "Pallet Town";

/** True when the typed place name is the secret trigger (case-insensitive). */
export function isPalletTown(name: string): boolean {
  return name.trim().toLowerCase() === "pallet town";
}

/** Themed quotes keyed by the app's normalized weather conditions. */
const POKEMON_QUOTES: Record<WeatherCondition, string[]> = {
  clear: [
    "Professor Oak says it's a perfect day to start your Pokémon journey!",
    "Pikachu is soaking up the sunshine outside.",
    "Clear skies ahead, Trainer. Adventure awaits!",
  ],
  hot: [
    "Charmander is thriving in this heat.",
    "Fire-types are feeling right at home today.",
    "Stay hydrated, Trainer — even Growlithe is panting.",
  ],
  cloudy: [
    "A mysterious day for exploring tall grass.",
    "Somewhere, a wild Pokémon is waiting to be discovered.",
    "The weather may be gray, but adventure never is.",
  ],
  rainy: [
    "Looks like Squirtle called in some extra water support today.",
    "A rainy route is perfect for Water-type Pokémon.",
    "Don't forget your Pokédex, Trainer. Rain won't stop an adventure.",
  ],
  stormy: [
    "Pikachu seems unusually excited about today's forecast.",
    "Electric-type Pokémon are having the time of their lives.",
    "Watch out, Trainer. The skies are using Thunder!",
  ],
  snowy: [
    "Looks like an Ice-type Pokémon convention outside.",
    "Perfect weather for a visit to Snowpoint City.",
    "Even Lapras might be feeling chilly today.",
  ],
  cold: [
    "Glaceon would feel right at home in this chill.",
    "Bundle up, Trainer — it's a frosty route ahead.",
    "Ice-type Pokémon are loving the cold snap.",
  ],
  foggy: [
    "A wild Pokémon could be hiding just beyond the mist.",
    "The fog rolls in like a slow Confuse Ray.",
    "Tread carefully, Trainer — visibility is low on this route.",
  ],
  windy: [
    "Pidgeotto are riding the air currents today.",
    "Flying-type Pokémon are out in full force.",
    "The wind carries the promise of a new adventure.",
  ],
};

const POKEMON_SUBTITLES: Record<WeatherCondition, string> = {
  clear: "Route 1 is wide open. Go catch 'em all.",
  hot: "Quick — grab a Fresh Water from the vending machine.",
  cloudy: "Keep your eyes on the tall grass.",
  rainy: "Great fishing weather down by the route.",
  stormy: "The Power Plant is buzzing with energy.",
  snowy: "Better pack some Burn Heals... and a coat.",
  cold: "Mt. Coronet weather, right here at home.",
  foggy: "Bring a Pokémon that knows Defog.",
  windy: "A perfect tailwind for your next adventure.",
};

export interface PokemonQuote {
  quote: string;
  subtitle: string;
}

/** Pick a themed quote + subtitle for the current condition. */
export function getPokemonQuote(condition: WeatherCondition): PokemonQuote {
  const list = POKEMON_QUOTES[condition] ?? POKEMON_QUOTES.clear;
  const quote = list[Math.floor(Math.random() * list.length)];
  return { quote, subtitle: POKEMON_SUBTITLES[condition] ?? POKEMON_SUBTITLES.clear };
}

/** Available decorative silhouette ids (see PokemonSilhouette component). */
export const SILHOUETTE_IDS = ["spark", "leaf", "flame", "splash"] as const;
export type SilhouetteId = (typeof SILHOUETTE_IDS)[number];

export function getRandomSilhouette(): SilhouetteId {
  return SILHOUETTE_IDS[Math.floor(Math.random() * SILHOUETTE_IDS.length)];
}

/**
 * Play a short, upbeat "item get" style jingle using the Web Audio API.
 * No external asset needed; gracefully no-ops if audio isn't available.
 * Must be called from a user gesture (e.g. a click) to satisfy autoplay rules.
 */
export function playPokemonSound(): void {
  try {
    const Ctx =
      window.AudioContext ||
      (window as unknown as { webkitAudioContext?: typeof AudioContext })
        .webkitAudioContext;
    if (!Ctx) return;

    const ctx = new Ctx();
    const notes = [659.25, 783.99, 987.77, 1318.51]; // E5, G5, B5, E6
    const noteLength = 0.12;

    notes.forEach((freq, i) => {
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.type = "square";
      osc.frequency.value = freq;

      const start = ctx.currentTime + i * noteLength;
      const end = start + noteLength;
      gain.gain.setValueAtTime(0.0001, start);
      gain.gain.exponentialRampToValueAtTime(0.18, start + 0.02);
      gain.gain.exponentialRampToValueAtTime(0.0001, end);

      osc.connect(gain);
      gain.connect(ctx.destination);
      osc.start(start);
      osc.stop(end);
    });

    setTimeout(() => ctx.close().catch(() => {}), notes.length * noteLength * 1000 + 200);
  } catch {
    // Audio not supported — silently ignore.
  }
}
