# Weather-Quips web reference

This is the original Replit/React/Express implementation, kept **for reference
only**. It is the source of truth for behaviour that the native Android app in
[`../android`](../android) reproduces:

| Behaviour | Web file | Android counterpart |
| --- | --- | --- |
| Funny quotes + subtitles | `server/routes.ts` | `domain/quotes/FunnyQuotes.kt` |
| Weather-code → condition mapping | `server/routes.ts` (`mapWeatherCode`) | `domain/model/WeatherCodeMapper.kt` |
| Open-Meteo / OpenWeatherMap / WeatherAPI fetching | `server/routes.ts` | `data/repository/*Provider.kt` |
| Geocoding (Nominatim) | `server/routes.ts` (`/api/geocode`) | `data/repository/GeocodingRepositoryImpl.kt` |
| Settings + formatting helpers | `client/src/contexts/settings.tsx` | `data/local/SettingsRepositoryImpl.kt`, `utils/Formatters.kt` |
| Home screen & detail panel | `client/src/pages/home.tsx` | `ui/home/*` |
| Precipitation radar | `client/src/pages/precipitation-map.tsx` | `ui/precipitation/*` |
| Weather icons | `client/src/lib/weatherIcons.ts`, `components/weather-icon.tsx` | `domain/model/WeatherIcons.kt`, `ui/components/WeatherIcon.kt` |
| Pokémon Easter egg | `client/src/lib/pokemon.ts` | `domain/quotes/PokemonQuips.kt` |
| Notifications | `client/src/lib/notifications.ts` | `notifications/*` |

Nothing here is built, bundled or required by the Android app — the Android
project has no dependency on Node, Vite, Express or Replit.
