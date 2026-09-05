import type { Express, Response } from "express";
import { createServer, type Server } from "http";
import { createHash } from "crypto";
import type { WeatherData, WeatherCondition } from "@shared/schema";

// --- Lightweight in-memory TTL cache -------------------------------------
// Avoids repeatedly hitting (and rate-limiting against) the upstream weather
// and geocoding providers for the same coordinates within a short window.
class TTLCache<T> {
  private store = new Map<string, { value: T; expires: number }>();
  constructor(private ttlMs: number, private maxEntries = 500) {}
  get(key: string): T | undefined {
    const hit = this.store.get(key);
    if (!hit) return undefined;
    if (Date.now() > hit.expires) {
      this.store.delete(key);
      return undefined;
    }
    return hit.value;
  }
  set(key: string, value: T): void {
    // Bound memory: evict the oldest entry (Map preserves insertion order)
    // once the cache is full, so one-off keys can't grow the heap unboundedly.
    if (this.store.size >= this.maxEntries && !this.store.has(key)) {
      const oldest = this.store.keys().next().value;
      if (oldest !== undefined) this.store.delete(oldest);
    }
    this.store.set(key, { value, expires: Date.now() + this.ttlMs });
  }
}

const weatherCache = new TTLCache<WeatherData>(5 * 60 * 1000); // 5 minutes
const reverseGeocodeCache = new TTLCache<string>(24 * 60 * 60 * 1000); // 24h
const geocodeCache = new TTLCache<{ latitude: number; longitude: number; name: string }>(
  24 * 60 * 60 * 1000,
); // 24h
// Dedupe concurrent identical weather requests so upstream is hit only once.
const inflightWeather = new Map<string, Promise<WeatherData>>();

// Regenerate the funny quote on every response — even cache hits — so the
// "refresh for a new quote" behavior stays identical from a user's perspective.
function sendWeather(res: Response, data: WeatherData) {
  const { quote, subtitle } = getRandomQuote(data.condition);
  res.set("Cache-Control", "no-store");
  res.json({ ...data, funnyQuote: quote, subtitle });
}

const funnyQuotes: Record<WeatherCondition, { quotes: string[]; subtitles: string[] }> = {
  clear: {
    quotes: [
      "The sun is absolutely **roasting** the sky right now",
      "Not a single **cloud** dared to show up today",
      "The sky said **perfection** and really meant it",
      "Dangerously **gorgeous** out there, proceed with caution",
    ],
    subtitles: [
      "The sun is trying too hard today.",
      "Perfect day to forget sunscreen and regret it.",
      "Nature's way of saying go outside, loser.",
      "Clear skies, unclear life choices.",
    ],
  },
  cloudy: {
    quotes: [
      "The sky looks **depressed** and honestly, same",
      "Clouds rolled in like they **own** the damn place",
      "It's giving overcast **sadness** with no end in sight",
      "The sun called in **sick** and left us with this mess",
    ],
    subtitles: [
      "Clouds everywhere. No escape.",
      "The sky needs therapy too.",
      "Nature's mood ring says 'blah'.",
      "Perfect weather for existential dread.",
    ],
  },
  rainy: {
    quotes: [
      "The sky is having a full **meltdown** on everyone",
      "It's pouring like the clouds are **heartbroken** again",
      "Everything outside is **soaked** beyond recognition",
      "Rain decided to make today absolutely **miserable**",
    ],
    subtitles: [
      "Bring an umbrella or embrace the chaos.",
      "The sky is as emotional as you.",
      "Free shower from the clouds.",
      "Perfect excuse to cancel plans.",
    ],
  },
  stormy: {
    quotes: [
      "All hell is **breaking** loose out there right now",
      "Thunder is throwing a massive **tantrum** overhead",
      "The sky is in full **destruction** mode, stay inside",
      "Nature picked today to go completely **unhinged**",
    ],
    subtitles: [
      "Maybe stay inside, just saying.",
      "The sky is having a breakdown.",
      "Thunder and drama everywhere.",
      "Nature's way of saying 'not today'.",
    ],
  },
  snowy: {
    quotes: [
      "Everything is covered in **frozen** nonsense again",
      "Snow is falling like nature's **dandruff** everywhere",
      "The world turned into a **freezing** white nightmare",
      "Winter just showed up and chose **violence** today",
    ],
    subtitles: [
      "Everything is cold and slippery.",
      "Time to pretend you like winter.",
      "Snowflakes like your excuses - everywhere.",
      "Bundle up or suffer.",
    ],
  },
  foggy: {
    quotes: [
      "Can't see a damn **thing** in any direction",
      "The world is hiding behind a wall of **nothing**",
      "Visibility is absolutely **gone**, good luck out there",
      "Fog rolled in like it's auditioning for a **horror** movie",
    ],
    subtitles: [
      "Vision? We don't know her.",
      "Silent Hill weather edition.",
      "The world is hiding from you.",
      "Perfect for dramatic walks.",
    ],
  },
  windy: {
    quotes: [
      "The wind is personally **attacking** everyone outside",
      "Hair's getting absolutely **destroyed** the second you step out",
      "The air has gone completely **berserk** today",
      "Wind is blowing like it has a **vendetta** against you",
    ],
    subtitles: [
      "Hold onto your stuff.",
      "Bad hair day guaranteed.",
      "The wind has personal beef with you.",
      "Nature's blow dryer on full blast.",
    ],
  },
  hot: {
    quotes: [
      "It's so hot the pavement is **melting** under your feet",
      "The sun is on a personal **warpath** against humanity",
      "Stepping outside feels like walking into an **inferno**",
      "Your skin will be **scorched** in approximately two minutes",
    ],
    subtitles: [
      "Your AC is your best friend now.",
      "Humans were not designed for this.",
      "Walking outside is a mistake.",
      "Ice cream is a survival necessity.",
    ],
  },
  cold: {
    quotes: [
      "It's so cold your bones are **shivering** independently",
      "The air is **biting** through every layer you own",
      "Stepping outside feels like entering an **icebox** of regret",
      "Your face will be completely **numb** in about ten seconds",
    ],
    subtitles: [
      "Layers on layers on layers.",
      "Your nose will be numb.",
      "Perfect weather to become a hermit.",
      "Even the cold is judging you.",
    ],
  },
};

function mapWeatherCode(code: number, temp: number): WeatherCondition {
  if (code === 0 || code === 1) {
    if (temp > 30) return "hot";
    if (temp < 5) return "cold";
    return "clear";
  }
  if (code === 2 || code === 3) return "cloudy";
  if (code >= 45 && code <= 48) return "foggy";
  if (code >= 51 && code <= 67) return "rainy";
  if (code >= 71 && code <= 77) return "snowy";
  if (code >= 80 && code <= 82) return "rainy";
  if (code >= 85 && code <= 86) return "snowy";
  if (code >= 95 && code <= 99) return "stormy";
  return "cloudy";
}

function getRandomQuote(condition: WeatherCondition): { quote: string; subtitle: string } {
  const data = funnyQuotes[condition];
  const quoteIndex = Math.floor(Math.random() * data.quotes.length);
  const subtitleIndex = Math.floor(Math.random() * data.subtitles.length);
  return {
    quote: data.quotes[quoteIndex],
    subtitle: data.subtitles[subtitleIndex],
  };
}

async function reverseGeocode(lat: number, lon: number): Promise<string> {
  const key = `${lat.toFixed(3)},${lon.toFixed(3)}`;
  const cached = reverseGeocodeCache.get(key);
  if (cached) return cached;
  try {
    const response = await fetch(
      `https://nominatim.openstreetmap.org/reverse?format=json&lat=${lat}&lon=${lon}&zoom=10`,
      {
        headers: {
          "User-Agent": "WeatherQuotesApp/1.0",
        },
      }
    );
    if (!response.ok) throw new Error("Geocoding failed");
    const data = await response.json();
    const name = data.address?.city || data.address?.town || data.address?.village || data.address?.county || "Unknown location";
    reverseGeocodeCache.set(key, name);
    return name;
  } catch {
    return "Your location";
  }
}

async function fetchOpenWeatherMap(lat: number, lon: number, apiKey: string): Promise<{
  temperature: number;
  isDay: boolean;
  weatherCode: number;
  windSpeed: number;
  feelsLike: number;
  humidity: number;
  pressure: number;
  uvIndex: number;
  tempMax: number;
  tempMin: number;
  precipChance: number;
  hourlyForecast: { time: string; temperature: number; precipitationChance: number }[];
  dailyForecast: { day: string; date: string; temperatureMax: number; temperatureMin: number }[];
}> {
  const currentRes = await fetch(`https://api.openweathermap.org/data/2.5/weather?lat=${lat}&lon=${lon}&appid=${apiKey}&units=metric`);
  if (!currentRes.ok) {
    if (currentRes.status === 401) throw new Error("Invalid API key for OpenWeatherMap");
    throw new Error("Failed to fetch from OpenWeatherMap");
  }
  const current = await currentRes.json();

  const forecastRes = await fetch(`https://api.openweathermap.org/data/2.5/forecast?lat=${lat}&lon=${lon}&appid=${apiKey}&units=metric`);
  if (!forecastRes.ok) throw new Error("Failed to fetch forecast from OpenWeatherMap");
  const forecast = await forecastRes.json();

  const hourlyForecast = forecast.list.slice(0, 8).map((item: any) => ({
    time: new Date(item.dt * 1000).toISOString().slice(11, 13) + ":00",
    temperature: item.main.temp,
    precipitationChance: item.pop ? Math.round(item.pop * 100) : 0,
  }));

  const dailyMap = new Map<string, { temps: number[] }>();
  forecast.list.forEach((item: any) => {
    const date = new Date(item.dt * 1000).toISOString().slice(0, 10);
    if (!dailyMap.has(date)) dailyMap.set(date, { temps: [] });
    dailyMap.get(date)!.temps.push(item.main.temp);
  });

  const weekdays = ["sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday"];
  const dailyForecast = Array.from(dailyMap.entries()).slice(1, 7).map(([dateStr, data], index) => {
    const date = new Date(dateStr + "T00:00:00Z");
    return {
      day: index === 0 ? "tomorrow" : weekdays[date.getUTCDay()],
      date: dateStr,
      temperatureMax: Math.max(...data.temps),
      temperatureMin: Math.min(...data.temps),
    };
  });

  const owmId = current.weather[0].id;
  let weatherCode = 0;
  if (owmId >= 200 && owmId < 300) weatherCode = 95;
  else if (owmId >= 300 && owmId < 600) weatherCode = 61;
  else if (owmId >= 600 && owmId < 700) weatherCode = 71;
  else if (owmId >= 700 && owmId < 800) weatherCode = 45;
  else if (owmId === 800) weatherCode = 0;
  else if (owmId > 800) weatherCode = 2;

  // Day/night from OpenWeatherMap's icon suffix ('d'/'n'), with sunrise/sunset fallback.
  const owmIcon: string = current.weather?.[0]?.icon || "";
  let isDay: boolean;
  if (owmIcon.endsWith("d")) isDay = true;
  else if (owmIcon.endsWith("n")) isDay = false;
  else if (current.sys?.sunrise && current.sys?.sunset && current.dt) {
    isDay = current.dt >= current.sys.sunrise && current.dt < current.sys.sunset;
  } else {
    const localHour = Math.floor((((current.dt ?? Date.now() / 1000) + (current.timezone ?? 0)) % 86400) / 3600);
    isDay = localHour >= 6 && localHour < 18;
  }

  return {
    temperature: current.main.temp,
    isDay,
    weatherCode,
    windSpeed: current.wind.speed * 3.6,
    feelsLike: current.main.feels_like,
    humidity: current.main.humidity,
    pressure: current.main.pressure,
    uvIndex: 0,
    tempMax: current.main.temp_max,
    tempMin: current.main.temp_min,
    precipChance: forecast.list[0]?.pop ? Math.round(forecast.list[0].pop * 100) : 0,
    hourlyForecast,
    dailyForecast,
  };
}

async function fetchWeatherAPI(lat: number, lon: number, apiKey: string): Promise<{
  temperature: number;
  isDay: boolean;
  weatherCode: number;
  windSpeed: number;
  feelsLike: number;
  humidity: number;
  pressure: number;
  uvIndex: number;
  tempMax: number;
  tempMin: number;
  precipChance: number;
  hourlyForecast: { time: string; temperature: number; precipitationChance: number }[];
  dailyForecast: { day: string; date: string; temperatureMax: number; temperatureMin: number }[];
}> {
  const res = await fetch(`https://api.weatherapi.com/v1/forecast.json?key=${apiKey}&q=${lat},${lon}&days=7&aqi=no`);
  if (!res.ok) {
    if (res.status === 401 || res.status === 403) throw new Error("Invalid API key for WeatherAPI");
    throw new Error("Failed to fetch from WeatherAPI");
  }
  const data = await res.json();

  const current = data.current;
  const today = data.forecast.forecastday[0];

  const code = current.condition.code;
  let weatherCode = 0;
  if (code === 1000) weatherCode = 0;
  else if (code >= 1003 && code <= 1009) weatherCode = 2;
  else if (code >= 1030 && code <= 1035) weatherCode = 45;
  else if (code >= 1063 && code <= 1201) weatherCode = 61;
  else if (code >= 1204 && code <= 1264) weatherCode = 71;
  else if (code >= 1273) weatherCode = 95;

  // WeatherAPI provides an authoritative is_day flag (1 = day, 0 = night).
  let isDay: boolean;
  if (current.is_day === 1 || current.is_day === 0) {
    isDay = current.is_day === 1;
  } else {
    const localHour = parseInt((data.location?.localtime || "").slice(11, 13), 10);
    isDay = Number.isNaN(localHour) ? true : localHour >= 6 && localHour < 18;
  }

  const currentHour = new Date().getHours();
  const hourlyForecast = today.hour
    .filter((_: any, i: number) => i >= currentHour)
    .slice(0, 24)
    .map((h: any) => ({
      time: h.time.slice(11, 13) + ":00",
      temperature: h.temp_c,
      precipitationChance: h.chance_of_rain || 0,
    }));

  const weekdays = ["sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday"];
  const dailyForecast = data.forecast.forecastday.slice(1).map((d: any, index: number) => {
    const date = new Date(d.date + "T00:00:00Z");
    return {
      day: index === 0 ? "tomorrow" : weekdays[date.getUTCDay()],
      date: d.date,
      temperatureMax: d.day.maxtemp_c,
      temperatureMin: d.day.mintemp_c,
    };
  });

  return {
    temperature: current.temp_c,
    isDay,
    weatherCode,
    windSpeed: current.wind_kph,
    feelsLike: current.feelslike_c,
    humidity: current.humidity,
    pressure: Math.round(current.pressure_mb),
    uvIndex: current.uv,
    tempMax: today.day.maxtemp_c,
    tempMin: today.day.mintemp_c,
    precipChance: today.day.daily_chance_of_rain || 0,
    hourlyForecast,
    dailyForecast,
  };
}

// Fetches and normalizes weather from the selected provider. The funny quote
// here is a placeholder — sendWeather() always overrides it per response.
async function computeWeatherData(
  service: string,
  lat: number,
  lon: number,
  apiKey: string,
): Promise<WeatherData> {
  if (service === "openweathermap") {
    const weatherData = await fetchOpenWeatherMap(lat, lon, apiKey);
    let condition = mapWeatherCode(weatherData.weatherCode, weatherData.temperature);
    if (weatherData.windSpeed > 40 && condition !== "stormy") condition = "windy";
    const { quote, subtitle } = getRandomQuote(condition);
    const location = await reverseGeocode(lat, lon);
    return {
      condition,
      isDay: weatherData.isDay,
      temperature: weatherData.temperature,
      description: condition,
      location,
      funnyQuote: quote,
      subtitle,
      feelsLike: weatherData.feelsLike,
      temperatureMax: weatherData.tempMax,
      temperatureMin: weatherData.tempMin,
      humidity: weatherData.humidity,
      precipitationChance: weatherData.precipChance,
      windSpeed: weatherData.windSpeed,
      uvIndex: Math.round(weatherData.uvIndex * 10) / 10,
      pressure: weatherData.pressure,
      dailyForecast: weatherData.dailyForecast,
      hourlyForecast: weatherData.hourlyForecast,
    };
  }

  if (service === "weatherapi") {
    const weatherData = await fetchWeatherAPI(lat, lon, apiKey);
    let condition = mapWeatherCode(weatherData.weatherCode, weatherData.temperature);
    if (weatherData.windSpeed > 40 && condition !== "stormy") condition = "windy";
    const { quote, subtitle } = getRandomQuote(condition);
    const location = await reverseGeocode(lat, lon);
    return {
      condition,
      isDay: weatherData.isDay,
      temperature: weatherData.temperature,
      description: condition,
      location,
      funnyQuote: quote,
      subtitle,
      feelsLike: weatherData.feelsLike,
      temperatureMax: weatherData.tempMax,
      temperatureMin: weatherData.tempMin,
      humidity: weatherData.humidity,
      precipitationChance: weatherData.precipChance,
      windSpeed: weatherData.windSpeed,
      uvIndex: Math.round(weatherData.uvIndex * 10) / 10,
      pressure: weatherData.pressure,
      dailyForecast: weatherData.dailyForecast,
      hourlyForecast: weatherData.hourlyForecast,
    };
  }

  const weatherResponse = await fetch(
    `https://api.open-meteo.com/v1/forecast?latitude=${lat}&longitude=${lon}&current=temperature_2m,weather_code,wind_speed_10m,apparent_temperature,relative_humidity_2m,surface_pressure,uv_index,is_day&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max,uv_index_max,weather_code&hourly=temperature_2m,precipitation_probability&timezone=auto&forecast_days=7`
  );

  if (!weatherResponse.ok) {
    throw new Error("Failed to fetch weather data");
  }

  const weatherData = await weatherResponse.json();
  const temperature = weatherData.current.temperature_2m;
  const weatherCode = weatherData.current.weather_code;
  const windSpeed = weatherData.current.wind_speed_10m;
  // Open-Meteo's is_day is computed from the sun's position at the location
  // (timezone=auto), so it's authoritative. Fall back to the local hour if absent.
  let isDay: boolean;
  if (weatherData.current.is_day === 1 || weatherData.current.is_day === 0) {
    isDay = weatherData.current.is_day === 1;
  } else {
    const localHour = parseInt((weatherData.current.time || "").slice(11, 13), 10);
    isDay = Number.isNaN(localHour) ? true : localHour >= 6 && localHour < 18;
  }

  let condition = mapWeatherCode(weatherCode, temperature);

  if (windSpeed > 40 && condition !== "stormy") {
    condition = "windy";
  }

  const { quote, subtitle } = getRandomQuote(condition);
  const location = await reverseGeocode(lat, lon);

  const currentHour = new Date().getHours();
  const hourlyTimes: string[] = weatherData.hourly.time;
  const hourlyTemps: number[] = weatherData.hourly.temperature_2m;
  const hourlyPrecipProb: number[] = weatherData.hourly.precipitation_probability || [];
  const hourlyForecast = hourlyTimes
    .map((t: string, i: number) => ({ time: t, temperature: hourlyTemps[i], precipitationChance: hourlyPrecipProb[i] ?? 0 }))
    .filter((_: any, i: number) => i >= currentHour)
    .slice(0, 24)
    .map((entry) => ({
      time: entry.time.slice(11, 13) + ":00",
      temperature: entry.temperature,
      precipitationChance: entry.precipitationChance,
    }));

  const dailyTimes: string[] = weatherData.daily.time;
  const dailyMaxTemps: number[] = weatherData.daily.temperature_2m_max;
  const dailyMinTemps: number[] = weatherData.daily.temperature_2m_min;

  const dailyForecast = dailyTimes
    .slice(1)
    .map((dateStr: string, index: number) => {
      let dayLabel: string;
      if (index === 0) {
        dayLabel = "tomorrow";
      } else {
        const date = new Date(dateStr + "T00:00:00Z");
        const weekdays = ["sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday"];
        dayLabel = weekdays[date.getUTCDay()];
      }
      return {
        day: dayLabel,
        date: dateStr,
        temperatureMax: dailyMaxTemps[index + 1],
        temperatureMin: dailyMinTemps[index + 1],
      };
    });

  return {
    condition,
    isDay,
    temperature,
    description: condition,
    location,
    funnyQuote: quote,
    subtitle,
    feelsLike: weatherData.current.apparent_temperature,
    temperatureMax: weatherData.daily.temperature_2m_max[0],
    temperatureMin: weatherData.daily.temperature_2m_min[0],
    humidity: weatherData.current.relative_humidity_2m,
    precipitationChance: weatherData.daily.precipitation_probability_max[0],
    windSpeed: weatherData.current.wind_speed_10m,
    uvIndex: Math.round(weatherData.current.uv_index * 10) / 10,
    pressure: Math.round(weatherData.current.surface_pressure),
    dailyForecast,
    hourlyForecast,
  };
}

export async function registerRoutes(
  httpServer: Server,
  app: Express
): Promise<Server> {
  
  app.get("/api/geocode", async (req, res) => {
    try {
      const query = req.query.query as string;
      if (!query || typeof query !== "string") {
        return res.status(400).json({ message: "Query parameter required" });
      }
      // Cap length to keep upstream requests sane and prevent abuse.
      if (query.length > 200) {
        return res.status(400).json({ message: "Query too long" });
      }
      const cacheKey = query.trim().toLowerCase();
      const cached = geocodeCache.get(cacheKey);
      if (cached) {
        // City coordinates are stable, so let the browser cache them too.
        res.set("Cache-Control", "public, max-age=86400");
        return res.json(cached);
      }
      const response = await fetch(
        `https://nominatim.openstreetmap.org/search?format=json&q=${encodeURIComponent(query)}&limit=1`,
        { headers: { "User-Agent": "WeatherQuotesApp/1.0" } }
      );
      if (!response.ok) throw new Error("Geocoding failed");
      const data = await response.json();
      if (!data.length) {
        return res.status(404).json({ message: "Location not found" });
      }
      const result = {
        latitude: parseFloat(data[0].lat),
        longitude: parseFloat(data[0].lon),
        name: data[0].display_name.split(",")[0],
      };
      geocodeCache.set(cacheKey, result);
      res.set("Cache-Control", "public, max-age=86400");
      res.json(result);
    } catch {
      res.status(500).json({ message: "Geocoding failed" });
    }
  });

  app.get("/api/weather/:latitude/:longitude", async (req, res) => {
    try {
      const { latitude, longitude } = req.params;
      
      if (!latitude || !longitude) {
        return res.status(400).json({ message: "Latitude and longitude are required" });
      }

      const lat = parseFloat(latitude);
      const lon = parseFloat(longitude);

      // Validate coordinates are real numbers within valid geographic bounds.
      if (
        !Number.isFinite(lat) ||
        !Number.isFinite(lon) ||
        lat < -90 || lat > 90 ||
        lon < -180 || lon > 180
      ) {
        return res.status(400).json({ message: "Invalid coordinates" });
      }

      const service = (req.query.service as string) || "openmeteo";
      const apiKey = req.headers["x-weather-api-key"] as string || "";

      // Only accept known providers so unexpected input can't reach upstream.
      const allowedServices = ["openmeteo", "openweathermap", "weatherapi"];
      if (!allowedServices.includes(service)) {
        return res.status(400).json({ message: "Unsupported weather service" });
      }

      if (service !== "openmeteo" && !apiKey) {
        return res.status(400).json({ message: "API key required for " + service });
      }

      // Round coords so nearby requests share a cache entry. Hash the API key
      // into the cache key so results are NEVER shared across different keys
      // (different keys can map to different accounts/providers and results).
      const keyId = apiKey
        ? createHash("sha256").update(apiKey).digest("hex").slice(0, 16)
        : "none";
      const cacheKey = `${service}:${lat.toFixed(3)}:${lon.toFixed(3)}:${keyId}`;

      let data = weatherCache.get(cacheKey);
      if (!data) {
        // Dedupe concurrent identical requests so upstream is only hit once.
        let pending = inflightWeather.get(cacheKey);
        if (!pending) {
          pending = computeWeatherData(service, lat, lon, apiKey)
            .then((result) => {
              weatherCache.set(cacheKey, result);
              return result;
            })
            .finally(() => {
              inflightWeather.delete(cacheKey);
            });
          inflightWeather.set(cacheKey, pending);
        }
        data = await pending;
      }

      sendWeather(res, data);
    } catch (error) {
      // Log details server-side; return a generic message to the client.
      console.error("Weather API error:", error);
      res.status(502).json({ message: "Failed to fetch weather data" });
    }
  });

  return httpServer;
}
