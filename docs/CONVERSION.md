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
| Precipitation probability | data only | ✅ **shown** | The web fetched it but only used it for notifications; it is now shown as "rain chance" in the stats grid and per hour in the hourly strip |
| Hourly forecast | ✅ | ✅ | 12 columns, stem-and-dot encoding, plus per-hour rain chance |
| Daily forecast (7 days) | ✅ | ✅ | Same "tomorrow" + weekday labels and min→max gradient bars |
| Animated hero icon | ✅ | ✅ | Per-condition motion ported from the CSS keyframes |
| Day/night icon variants | ✅ | ✅ + storms and snow, see below |
| Five-taps "explode" Easter egg | ✅ | ✅ | Same six fragments and trajectories |
| Pull-up detail panel | ✅ | ✅ | Material 3 bottom sheet (anchored drag + nested scroll) |
| Refresh (new quote each time) | ✅ | ✅ | |
| Pokémon "Pallet Town" mode | ✅ | ✅ | Banner, Poké Ball, silhouettes, themed quotes, background tint — redrawn, see below |

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
| Forecast-location marker | ✅ | ✅ |
| Device-location marker | ❌ | ✅ (a "you are here" dot, shown when location is already permitted) |
| Map controls | zoom | zoom, re-centre on the forecast location, and centre on the device when its position is known |

## Platform translations

| Web mechanism | Android replacement |
| --- | --- |
| Browser geolocation | `LocationManager` (no Play Services), last-known fix reused, single live request |
| `localStorage` | DataStore Preferences |
| Service Worker + Notification API | Notification channel, `POST_NOTIFICATIONS`, WorkManager periodic check; the alert copy is written in the app's voice, with a per-type icon and an action that opens the radar |
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
6. **A first-run introduction was added.** The web app had none: it asked for
   browser geolocation the moment the page loaded. Three pages in the app's own
   voice explain what it does and what location is for *before* the system
   dialog appears, and declining leads to the city search rather than a
   permission wall. It is shown once, tracked by a flag in DataStore.
7. **The detail panel was reworked** (see below) — it shows the same data plus
   rain chance, but the presentation is not a copy of the web layout.

## Detail panel rework

The ported layout had problems the web version also had, which only became
obvious against real data:

| Problem | Fix |
| --- | --- |
| The day-range bar was a full-width cold→hot gradient whatever the weather — decoration shaped like data | It now spans today's low→high on a real scale, with a marker for the current temperature |
| The high and low were printed twice: beside the bar, then again as arrow chips below | The chips are gone; the bar carries both, and the marker says where "now" falls |
| The stats box mixed three alignments and left one orphaned cell with a gap beside it | Six uniform tiles in a 2×3 grid, each with an icon, a label and a value |
| Pressure was a bare number with no unit | Shown as hPa |
| "precipitation" was ambiguous next to a current-conditions readout | Relabelled "rain chance", and added per-hour rain chance to the hourly strip |
| Forecast colour was relative to whatever was on screen, so a 1 °C spread rendered as a full blue→red swing and the same temperature took a different colour in each card | Colour is anchored to actual degrees, so a temperature always looks the same; mild weather sits near-neutral and colour appears when it means something |
| The hourly chart normalised to the visible min and max, turning a flat evening into dramatic peaks | The strip holds a minimum span, so a flat night looks flat |

## Reaching the radar

The web app put the radar behind a button at the bottom of the detail panel:
swipe up, scroll down, tap. On a phone the radar is the second thing people
want after the headline, so it moved to a gesture on the home screen itself.

| Decision | Why |
| --- | --- |
| Swipe right to left on the home screen | The map "lives" to the right of home and slides in from there; back slides it out the same way, so gesture and motion agree |
| One drag detector for both swipes, classified on release | Up still opens the panel. A thumb swiping up drifts sideways, so a map swipe must be 1.5× more horizontal than vertical and travel 72dp (the panel needs 56dp): opening the map by accident costs a transition and a radar download, opening the panel costs nothing |
| Start away from the edge | With gesture navigation the outer strip of the screen belongs to the system's back gesture; a swipe that starts there is taken by Android before the app sees it |
| No visible control | An edge tab was tried and removed at the user's request: the swipe is the only visible route. TalkBack users cannot perform a custom swipe, so the same action is offered as an accessibility action on the home screen ("Open precipitation map" in TalkBack's actions menu) |
| The panel's map card is gone | One way in, found from the screen people already look at |

## Motion

Every animation takes its timing from `ui/theme/Motion.kt`: Material 3's
emphasized curves, three durations, and two springs. Things arriving
decelerate, things leaving accelerate, and anything a finger was holding is
finished by a spring so it keeps the finger's momentum. Every custom animation
honours the system's "remove animations" setting.

### The home screen

| Before | After |
| --- | --- |
| The icon's idle motion travelled 2.5% of its size — four pixels on a 160dp icon | Seven percent, with a second slower period on drifting conditions so the path never quite repeats, a squash on the rain bob, and a spring pop when the condition changes |
| Nothing around the icon | A per-condition atmosphere: rain falling past it, snow drifting, stars twinkling over a clear night, the sun's glow breathing with a ring of light rolling outward, ghost clouds crossing, fog bands, wind streaks, lightning glow timed to the icon's flicker. One looping clock drives the scene, read in the draw phase so it never recomposes |
| Everything appeared at once | A staggered entrance — icon, quote, subtitle, details — once per session, not on every return from another screen |
| A refresh swapped the quote in place | The old quip lifts away and the new one rises into its place |
| The swipe-up chevron bounced six *pixels*, about two dp on a modern phone | A 9dp double nudge and a rest, like a hand beckoning |
| The refresh arrow snapped upright when a refresh finished | It finishes its turn and eases to a stop |

### Home to radar

The swipe used to be judged when the finger lifted and then play a canned
320ms slide. It is now a physical pull:

- The radar panel slides in from the right edge exactly as far as the finger
  has pulled, and home drifts left at 30% of that pace: the parallax that
  makes the radar read as arriving on top.
- The drag locks to one axis after 10dp, so a swipe that starts sideways stays
  sideways.
- Crossing the point of no return ticks a haptic and lights the panel's icon;
  dragging back ticks again.
- A short fast flick counts: release velocity is projected 120ms ahead,
  provided the finger moved at least 24dp.
- On release, the real map starts from the exact position the panel reached
  and a spring carries it the rest of the way. Short of the threshold,
  everything springs back.
- Back from the map uses predictive back on Android 14+, so the return can be
  scrubbed under the thumb.

A fade on the sliding home content was tried and removed: fading a
translated layer clipped it at its resting edge, cutting the quote in half.

### Elsewhere

- Loading, errors and weather crossfade into each other instead of cutting.
- Settings come forward over home and fall back into it (a depth axis), where
  the radar moves sideways. Navigation's own 700ms default fade is replaced.
- Each time the detail panel opens, its cards rise in one after another, the
  hourly dots climb to their temperatures in a wave, and the week's bars draw
  outward from their middles.
- On the radar screen the play button turns over rather than swapping, the
  timeline's colour eases as playback sweeps along it with the active frame
  standing taller, and the clock rolls over like an odometer (keyed on the
  timestamp, so it rolls the right way across midnight).

## Home-screen widget

New to the Android version; the PWA had nothing equivalent.

| Aspect | Decision |
| --- | --- |
| Content | A precipitation-intensity graph for the next two hours, led by the fact that matters — "Rain in 30 min", "Raining now", "Dry for now" — with the app's remark as small print underneath and the current rate beside the place name |
| Copy | The notification puts the joke first because it is read once; the widget is glanced at all day, so the fact leads and the remark is secondary. The remark rotates with the hour, so it is never stale but never changes mid-refresh |
| Data | Renders from the same cache the app uses offline, so it shows something sensible with no network and without the app ever running |
| Refresh | WorkManager, hourly, started when the first widget is placed and cancelled when the last is removed. The app also redraws it whenever it caches a fresh result |
| Tap | Opens the radar on the place the widget is reporting, reusing the notification's deep link |
| Toolkit | Glance. It draws through RemoteViews, which cannot use a bundled font, so the widget is set in the system sans rather than Space Grotesk — every other identity cue (palette, weight, lowercase labels, blue for water) carries over |

### The graph

| Decision | Why |
| --- | --- |
| Intensity, not probability | "70% chance" does not distinguish a drizzle from a downpour. The graph plots millimetres per hour, the figure a rain radar prints |
| Radar first, model second | The app's map is observed radar; a forecast model is not. Asked about a point with a visible echo on the map, Open-Meteo answered 0.0 mm/h while the radar read 1.8 — that gap is the whole complaint. The widget uses the German weather service's radar composite and RV nowcast (five-minute steps, an hour of observation behind and two hours of extrapolated radar ahead) via [Bright Sky](https://brightsky.dev), and Open-Meteo's `minutely_15` outside its coverage |
| Coverage is Germany and its neighbours | DWD's composite answers for Germany, the Low Countries, Denmark, Austria and Switzerland, and returns an explicit "outside the radar data range" elsewhere, which reads as "no radar here" rather than as a failure. A reply is about 400 bytes |
| The wettest cell touching the point, not the cell under it | Radar cells are a kilometre across and a shower's position is uncertain by about that much. A core one cell over is rain that is about to be overhead, and under-reporting is the failure this widget exists to avoid |
| Labelled in the weather's timezone | Radar frames are stamped in UTC, so the location's own UTC offset rides along on the forecast and the axis reads in the local time of the place being shown, not the phone's |
| Half an hour of history on the Open-Meteo path | A "now" line needs something behind it, otherwise it sits on the left edge and means nothing. `past_minutely_15=2` supplies it; the radar feed brings its own observed frames. Where a series does start at now, the left edge is labelled rather than ruled |
| A visibility floor at the bottom of the axis | The drizzle that actually passed over Lübeck was 0.12 mm/h. On a linear bottom band that is two pixels and indistinguishable from dry, so the axis climbs out of zero steeply enough to give anything the radar can measure a shape |
| The series is aged against the cache | The samples are stamped relative to the moment they were fetched and the widget draws from cache, so on every redraw the series slides left by the age of the cache and anything past 45 minutes of history is dropped. Without it the "now" line marks where now *was* |
| Refreshed every 15 minutes | WorkManager's floor, and the right end of it: an hourly refresh leaves a two-hour minute-resolution graph half stale and misses a shower that arrived since |

### Keeping it refreshed

A widget that draws from cache is only as good as the thing that refills the
cache. Four ways that stopped, all found from one screenshot of a blank card:

| Fault | Fix |
| --- | --- |
| The refresh could not find a location. Since Android 10 an app without `ACCESS_BACKGROUND_LOCATION` gets nothing from the location APIs while it has no visible process — not an error, just null — and the worker gave up rather than fetching. With the default device-location mode that meant the widget only ever refreshed while the app was open | Try the device, then fall back to the coordinates of the place the app last showed, which are already in the cache. A weather widget has no business asking for background location |
| The schedule could be lost for good. It was armed only when a widget was placed, and the "re-arm on update" path never ran because `updatePeriodMillis` was 0, so a force stop or an OEM battery manager switched it off permanently | Re-arm on app start when a widget exists, and give the system its own half-hourly update as an independent backstop |
| Background work gets throttled whatever the schedule says | Being drawn is the one moment the widget knows it matters, so a draw with data older than half an hour queues a catch-up fetch. A glance at the home screen repairs it |
| Failure was invisible: every path ended in a silent redraw of older and older data | The face carries the age once the data passes half an hour, in amber. "3h ago" is the difference between a quiet afternoon and a widget that has quietly stopped |

And the drawing itself turned all of the above into a blank rectangle:

| Fault | Fix |
| --- | --- |
| The nowcast is stamped relative to its fetch, so about 160 minutes without a refresh slid every sample off the left of the graph, leaving the headline above an empty box | When the minute-level series expires, fall back to the hourly forecast, which is stamped in wall-clock hours and stays meaningful for as long as it covers |
| The hourly fallback was itself placed by list position, so it was wrong by exactly the age of the cache | Placed by counting hours from the fetch instead. Reading the clock label and picking the nearest occurrence was the other candidate and is worse: it decides a half-day-old forecast is about to happen tomorrow |
| The headline read the raw hourly list, so a three-hour-old widget announced rain for a time that had already passed | Drop the hours that have elapsed since the fetch before choosing what to announce |
| With nothing to plot, the graph's space was simply left empty | Say what is wrong in it instead — "No forecast to draw. Tap to refresh." |
| A quantised zero is not a dry forecast | Open-Meteo reports `minutely_15` to a tenth of a millimetre per quarter-hour, so anything under 0.4 mm/h lands on exactly zero while the hourly field resolves the same drizzle four times finer. When the minute series is flat and the hourly one is not, the graph uses the hourly one |
| A non-linear vertical axis | On a linear 0–15 mm/h scale a 0.3 mm/h drizzle is two percent of the height and effectively invisible. Light, moderate, heavy and violent each get a quarter of the plot, which also gives evenly spaced gridlines to label |
| Drawn to a bitmap | RemoteViews has no canvas and no path support, so anything beyond boxes and text has to arrive as an image. It is rendered at a fixed 2.5 px/dp and scaled to fit, which keeps it identical in tests and on a phone |
| One palette for both themes | The bitmap is drawn before Glance resolves a theme, and a Glance composable cannot read the resolved colour without a context. Every colour in the graph is picked to work on both the near-white and the near-black card |
| Whole hours are labelled first | On a 180 dp widget there is room for about three clock labels. Half hours fill what is left rather than crowding the hours out |
| Hourly fallback | OpenWeatherMap and WeatherAPI have no sub-hourly feed, so the graph falls back to their hourly millimetre totals and labels itself hourly instead of pretending to minute resolution |

## Night rework

The web app had one set of quotes per condition, written for daylight, and
day/night icons only for clear, cloudy and rainy. At half past ten at night a
clear sky was therefore described as being roasted by a sun that had set hours
earlier.

| Change | Detail |
| --- | --- |
| Night quotes | A second set of four quotes and four subtitles per condition, in the same voice, used when the provider reports `is_day = 0`. The daytime lines are the web app's originals, untouched. |
| Night quotes in Pallet Town | The Easter egg had the same problem — "Pikachu is soaking up the sunshine" at midnight — so it gets night lines too. |
| Night icons for storms and snow | The rule is whether the sky is part of the picture: storms and snow fall out of a sky you can still see, so they now show a crescent. Fog replaces the sky rather than sitting under it, and wind, heat and cold describe the air, so those are unchanged. |

Tests assert the split holds as content, not just as plumbing: no night line
claims the sun is out, no daytime line talks about stars or the moon, and the
two sets never share a quote.

## Easter-egg rework

| Problem | Fix |
| --- | --- |
| Turning the mode on left the quote and subtitle blank: the themed quip was only generated during a weather load, and switching the mode changes no input that triggers one | The quip is derived from whatever weather is already loaded, so it appears the moment the mode is switched on and reverts when it is switched off |
| The Poké Ball rotated a full 360°, which tilted its band until it stopped reading as a Poké Ball | A gentle float and a ±6° wobble; the shell keeps a gloss highlight and a properly proportioned button |
| Sparkles sat on top of the shell rather than around it | The shell is inset, so the sparkles have room outside it |
| The silhouettes were a circle with two spikes, and the translucent fill stacked at every overlap into visible seams | Four creatures sharing one body — head, torso, feet, plus a distinguishing tail or crest — each unioned into a single flat path |
| The mode's wash stopped at the sheet's peek height, leaving a band along the bottom | The wash runs behind the whole screen, with a wide clear middle so it tints the edges rather than the artwork |

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
