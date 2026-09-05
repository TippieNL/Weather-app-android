import type { WeatherCondition } from "@shared/schema";

/**
 * Icon logic — pure, side-effect free, easy to test.
 *
 * Maps a weather condition + day/night flag to a stable icon key. The key is
 * resolved to an actual SVG component in `@/components/weather-icon`. Keeping the
 * mapping here (logic) separate from the SVG rendering (component) lets us cache
 * and reason about icon decisions without touching React.
 */

export type WeatherIconKey =
  | "clear-day"
  | "clear-night"
  | "cloudy-day"
  | "cloudy-night"
  | "rainy-day"
  | "rainy-night"
  | "stormy"
  | "snowy"
  | "foggy"
  | "windy"
  | "hot"
  | "cold";

/**
 * Conditions whose appearance meaningfully changes with the sun being up or
 * down (sky-based). Temperature/air conditions (hot, cold, windy) and conditions
 * dominated by dark clouds (stormy, snowy, foggy) read the same day or night, so
 * they intentionally ignore `isDay`.
 */
const DAY_NIGHT_AWARE: ReadonlySet<WeatherCondition> = new Set<WeatherCondition>([
  "clear",
  "cloudy",
  "rainy",
]);

/**
 * Resolve the icon key for a condition and time of day.
 *
 * @param condition normalized weather condition from the API
 * @param isDay     true when the sun is above the horizon at the location
 */
export function getWeatherIcon(
  condition: WeatherCondition,
  isDay: boolean,
): WeatherIconKey {
  if (DAY_NIGHT_AWARE.has(condition)) {
    return `${condition}-${isDay ? "day" : "night"}` as WeatherIconKey;
  }
  return condition as WeatherIconKey;
}

/** Fallback day/night detection when the API gives us nothing (06:00–18:00 = day). */
export function isDayFromHour(hour: number): boolean {
  return hour >= 6 && hour < 18;
}
