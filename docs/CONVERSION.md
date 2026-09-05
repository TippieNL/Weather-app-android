# Weather-Quips → native Android: feature checklist

Every row was verified against the original implementation in
[`web-reference/`](../web-reference), not against the project's own docs.

## Home screen

| Feature | Web | Android | Notes |
| --- | --- | --- | --- |
| Current location name | ✅ | ✅ | Nominatim reverse geocoding, same `city → town → village → county` fallback |
| Current temperature | ✅ | ✅ | |
| Weather condition + description | ✅ | ✅ | Same nine conditions |
| Funny quote with `**highlight**` | ✅ | ✅ | Compose `AnnotatedString`; blue ≤ 15 °C, red above — the web rule exactly |
| Subtitle | ✅ | ✅ | |
| Feels-like | ✅ | ✅ | In the detail card, as on the web |
| Today min / max | ✅ | ✅ | |
| Humidity, wind, UV, pressure | ✅ | ✅ | |
| Precipitation probability | data only | ✅ **shown** | The web fetched it but only used it for notifications; it is now displayed in the stats grid |
| Hourly forecast | ✅ | ✅ | 12 columns, same stem-and-dot encoding and colour ramp |
| Daily forecast (7 days) | ✅ | ✅ | Same "tomorrow" + weekday labels and min→max gradient bars |
| Animated hero icon | ✅ | ✅ | Per-condition motion ported from the CSS keyframes |
| Day/night icon variants | ✅ | ✅ | Only for clear/cloudy/rainy, as in `weatherIcons.ts` |
| Five-taps "explode" Easter egg | ✅ | ✅ | Same six fragments and trajectories |
| Pull-up detail panel | ✅ | ✅ | Material 3 bottom sheet (anchored drag + nested scroll) |
| Refresh (new quote each time) | ✅ | ✅ | |
| Pokémon "Pallet Town" mode | ✅ | ✅ | Banner, Poké Ball, silhouettes, themed quotes, background tint |

## Settings

| Feature | Web | Android |
| --- | --- | --- |
| Temperature unit (C/F) | ✅ | ✅ |
| Time format (12h/24h) | ✅ | ✅ |
| Date format (DD/MM, MM/DD, YYYY-MM-DD) | ✅ | ✅ |
| Location mode (device / manual) | ✅ | ✅ |
| Manual city search | ✅ | ✅ (plus a picker when several places match) |
| Weather provider selection | ✅ | ✅ |
| API key entry | ✅ | ✅ (masked, encrypted at rest) |
| Precipitation notifications toggle | ✅ | ✅ |
| Test notification | ✅ | ✅ |
| Exit Pokémon mode | ✅ | ✅ |
| Persistence | localStorage | DataStore (survives restart, process death, rotation) |

## Precipitation map

| Feature | Web | Android |
| --- | --- | --- |
| Map | Leaflet | osmdroid `MapView` |
| Base tiles | CARTO light | OpenStreetMap, desaturated (and inverted in dark mode) |
| RainViewer radar overlay | ✅ | ✅ (native tile overlay, same tile flavour `…/256/{z}/{x}/{y}/2/1_1.png`) |
| Animated timeline | ✅ | ✅ (800 ms per frame, same as the web) |
| Play / pause | ✅ | ✅ |
| Frame scrubbing + hour labels | ✅ | ✅ |
| Past vs nowcast distinction | ✅ | ✅ |
| Precipitation legend | ✅ | ✅ (same colour stops) |
| Current-location marker | ✅ | ✅ |
| Map controls | zoom | zoom + re-centre |

## Platform translations

| Web mechanism | Android replacement |
| --- | --- |
| Browser geolocation | `LocationManager` (no Play Services), last-known fix reused, single live request |
| `localStorage` | DataStore Preferences |
| Service Worker + Notification API | Notification channel, `POST_NOTIFICATIONS`, WorkManager periodic check |
| PWA offline page / cache | Cached last result + "showing saved weather" labelling |
| Express `/api/weather` | `WeatherRepository` with three providers |
| Express `/api/geocode` | `GeocodingRepository` (Nominatim, with a descriptive User-Agent) |
| Server-side quote generation | `FunnyQuotes` in the app |
| `?lat=&lon=` query params | Navigation Compose route arguments |

## Deliberate differences

1. **Base map tiles.** The web app used CARTO's `light_all` tiles, which now
   require an API key — the reference screenshot shows "API KEY REQUIRED"
   watermarks. The Android app uses OpenStreetMap tiles and desaturates them, so
   the map is key-free and still reads as monochrome.
2. **Hourly slicing.** The web indexed the location's hourly array with the
   *device's* current hour, which is wrong whenever the selected city is in
   another timezone. The Android app matches on the timestamp instead and falls
   back to the old behaviour if timestamps cannot be compared.
3. **Precipitation probability is displayed** on the home screen (see above).
4. **Settings is a screen, not a slide-in sheet**, which is the Android
   convention and makes back navigation unambiguous.
5. **Sunrise/sunset** are requested from Open-Meteo (the web app did not) and
   used only as a day/night fallback.

## Verification

- `./gradlew test` — 109 unit and host-side UI tests (formatting, condition
  mapping, quote content and selection, provider response mapping, repository
  caching and error translation, settings persistence including "the API key is
  never written in the clear", geocoding, alert wording, the home state machine, the radar timeline,
  and Compose rendering of every UI state).
- `./gradlew assembleDebug` / `assembleRelease` — both build; release runs R8 and
  `lintVitalRelease` clean.
- `./gradlew assembleDebugAndroidTest` — instrumentation tests compile; they
  cover navigation, system back and the radar screen and need a device to run.
- Screens are rendered to PNGs by `ScreenshotRenderTest` and compared against the
  original design; the results are in [`screenshots/`](screenshots).
