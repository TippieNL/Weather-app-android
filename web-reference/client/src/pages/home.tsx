import { useState, useEffect, useCallback, useRef, useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { MapPin, RefreshCw, ChevronUp, ChevronRight, Map, ArrowUp, ArrowDown } from "lucide-react";
import { Link } from "wouter";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Card, CardContent } from "@/components/ui/card";
import { ScrollArea, ScrollBar } from "@/components/ui/scroll-area";
import type { WeatherData } from "@shared/schema";
import { useSettings, convertTemp, formatTemp, formatTime, formatDate } from "@/contexts/settings";
import { SettingsSheet } from "@/components/settings-sheet";
import { AnimatedWeatherIcon } from "@/components/animated-weather-icon";
import { WeatherGlyph } from "@/components/weather-icon";
import { PokeballIcon, PokeballGlyph } from "@/components/pokeball-icon";
import { PokemonSilhouette } from "@/components/pokemon-silhouette";
import { sendNotification } from "@/lib/notifications";
import { getPokemonQuote, getRandomSilhouette, POKEMON_BANNER, POKEMON_TITLE } from "@/lib/pokemon";
import { useToast } from "@/hooks/use-toast";

function renderQuoteWithHighlight(quote: string, temperature: number) {
  const match = quote.match(/^(.*?)\*\*(.+?)\*\*(.*)$/);
  if (!match) return quote;

  const [, before, word, after] = match;
  const color = temperature <= 15 ? "text-blue-500" : "text-red-500";

  return (
    <>
      {before}
      <span className={color} data-testid="text-highlight-word">{word}</span>
      {after}
    </>
  );
}

function tempColor(normalized: number): string {
  const t = Math.max(0, Math.min(1, normalized));
  const r = Math.round(59 + (239 - 59) * t);
  const g = Math.round(130 + (68 - 130) * t);
  const b = Math.round(246 + (68 - 246) * t);
  return `rgb(${r},${g},${b})`;
}

const COLD_COLOR = "#3b82f6";
const HOT_COLOR  = "#ef4444";

function LoadingSkeleton() {
  return (
    <div className="flex flex-col items-start justify-end h-[100dvh] p-8 pb-16">
      <div className="w-full">
        <Skeleton className="w-32 h-32 mb-12" />
        <Skeleton className="h-20 w-full mb-4" />
        <Skeleton className="h-6 w-2/3" />
      </div>
    </div>
  );
}

function LocationPermission({ onRetry }: { onRetry: () => void }) {
  return (
    <div className="flex flex-col items-start justify-end h-[100dvh] p-8 pb-16 relative">
      <div className="absolute top-4 right-4">
        <SettingsSheet />
      </div>
      <div className="w-full">
        <MapPin className="w-32 h-32 mb-12 stroke-[1.5]" data-testid="icon-location" />
        <h1 className="font-display text-5xl md:text-7xl font-bold tracking-tight leading-none mb-4" data-testid="text-permission-title">
          Where are you?
        </h1>
        <p className="text-base text-muted-foreground mb-8 leading-relaxed" data-testid="text-permission-description">
          Allow location access to get weather for your area.
        </p>
        <Button 
          onClick={onRetry} 
          size="lg"
          data-testid="button-allow-location"
        >
          <MapPin className="w-5 h-5 mr-2" />
          Allow Location
        </Button>
      </div>
    </div>
  );
}

function ErrorState({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <div className="flex flex-col items-start justify-end h-[100dvh] p-8 pb-16 relative">
      <div className="absolute top-4 right-4">
        <SettingsSheet />
      </div>
      <div className="w-full">
        <span className="material-symbols-outlined mb-12" style={{ fontSize: 128, color: "currentColor" }} data-testid="icon-error">cloud_off</span>
        <h1 className="font-display text-5xl md:text-7xl font-bold tracking-tight leading-none mb-4" data-testid="text-error-title">
          Oops...
        </h1>
        <p className="text-base text-muted-foreground mb-8 leading-relaxed" data-testid="text-error-message">
          {message}
        </p>
        <Button 
          onClick={onRetry} 
          size="lg"
          data-testid="button-retry"
        >
          <RefreshCw className="w-5 h-5 mr-2" />
          Try Again
        </Button>
      </div>
    </div>
  );
}

function WeatherDisplay({ data, onRefresh, isRefreshing, location, isPokemonMode }: { data: WeatherData; onRefresh: () => void; isRefreshing: boolean; location: { latitude: number; longitude: number }; isPokemonMode: boolean }) {
  const [expanded, setExpanded] = useState(false);
  const panelRef = useRef<HTMLDivElement>(null);
  const startYRef = useRef(0);
  const isDraggingRef = useRef(false);
  const { settings } = useSettings();

  // Themed quote + a random mystery silhouette, recomputed only when the
  // condition (or mode) changes so they stay stable across re-renders.
  const pokemonQuote = useMemo(
    () => getPokemonQuote(data.condition),
    [data.condition, isPokemonMode],
  );
  const silhouetteId = useMemo(
    () => getRandomSilhouette(),
    [data.condition, isPokemonMode],
  );

  // Precompute forecast min/max ranges so they aren't recalculated on every
  // re-render (e.g. when expanding/collapsing the detail panel).
  const hourlyStats = useMemo(() => {
    const hours = data.hourlyForecast.slice(0, 12);
    const temps = hours.map((h) => h.temperature);
    const minT = Math.min(...temps);
    const maxT = Math.max(...temps);
    return { hours, minT, range: maxT - minT || 1 };
  }, [data.hourlyForecast]);

  const weeklyStats = useMemo(() => {
    const weekMin = Math.min(...data.dailyForecast.map((d) => d.temperatureMin));
    const weekMax = Math.max(...data.dailyForecast.map((d) => d.temperatureMax));
    return { weekMin, weekRange: weekMax - weekMin || 1 };
  }, [data.dailyForecast]);

  const displayQuote = isPokemonMode ? pokemonQuote.quote : data.funnyQuote;
  const displaySubtitle = isPokemonMode ? pokemonQuote.subtitle : data.subtitle;

  const handleTouchStart = useCallback((e: React.TouchEvent) => {
    startYRef.current = e.touches[0].clientY;
    isDraggingRef.current = true;
  }, []);

  const handleTouchEnd = useCallback((e: React.TouchEvent) => {
    if (!isDraggingRef.current) return;
    isDraggingRef.current = false;
    const deltaY = startYRef.current - e.changedTouches[0].clientY;
    if (deltaY > 50) {
      setExpanded(true);
    } else if (deltaY < -50) {
      setExpanded(false);
    }
  }, []);

  const handleWheel = useCallback((e: React.WheelEvent) => {
    if (expanded && panelRef.current) {
      const el = panelRef.current;
      const atTop = el.scrollTop <= 0;
      if (atTop && e.deltaY < 0) {
        setExpanded(false);
        return;
      }
      if (!atTop || e.deltaY > 0) return;
    }
    if (e.deltaY > 30) {
      setExpanded(true);
    }
  }, [expanded]);

  return (
    <div
      className={`h-[100dvh] relative overflow-hidden ${isPokemonMode ? "pokemon-mode" : ""}`}
      onTouchStart={handleTouchStart}
      onTouchEnd={handleTouchEnd}
      onWheel={handleWheel}
    >
      {isPokemonMode && !expanded && (
        <div
          className="pokemon-banner absolute top-0 left-0 right-0 flex justify-center pt-16 z-20 pointer-events-none px-4"
          data-testid="banner-pokemon"
        >
          <div className="rounded-full bg-[#ee1515] text-white font-display font-bold text-sm md:text-base px-5 py-2 shadow-lg text-center">
            {POKEMON_BANNER}
          </div>
        </div>
      )}
      <div className="absolute top-0 left-0 right-0 flex justify-end gap-1 p-4 z-10"
        style={{
          opacity: expanded ? 0 : 1,
          pointerEvents: expanded ? "none" : "auto",
          transition: "opacity 0.5s ease-out",
        }}
      >
        <SettingsSheet />
        <Button
          variant="ghost"
          size="icon"
          onClick={onRefresh}
          disabled={isRefreshing}
          data-testid="button-refresh"
        >
          <RefreshCw className={`w-5 h-5 ${isRefreshing ? "animate-spin" : ""}`} />
        </Button>
      </div>

      <div
        className="absolute inset-0 flex flex-col items-start justify-end p-8 pb-20 transition-all duration-500 ease-out"
        style={{
          transform: expanded ? "translateY(-40%)" : "translateY(0)",
          opacity: expanded ? 0 : 1,
          pointerEvents: expanded ? "none" : "auto",
        }}
      >
        <div className="w-full">
          {isPokemonMode ? (
            <PokeballIcon
              className="mb-8"
              size={160}
              data-testid="icon-weather"
            />
          ) : (
            <AnimatedWeatherIcon
              condition={data.condition}
              isDay={data.isDay}
              className="w-40 h-40 md:w-48 md:h-48 mb-8"
              size={160}
              data-testid="icon-weather"
            />
          )}

          <div className="flex items-start gap-4">
            <h1
              className="font-display text-5xl md:text-7xl lg:text-8xl font-bold tracking-tight leading-none mb-4 flex-1"
              data-testid="text-funny-quote"
            >
              {isPokemonMode ? displayQuote : renderQuoteWithHighlight(displayQuote, data.temperature)}
            </h1>
            {isPokemonMode && (
              <PokemonSilhouette
                id={silhouetteId}
                size={64}
                className="pokemon-silhouette shrink-0 mt-2 text-foreground/20"
                data-testid="img-pokemon-silhouette"
              />
            )}
          </div>

          <p
            className="text-base md:text-lg text-muted-foreground leading-relaxed"
            data-testid="text-subtitle"
          >
            {displaySubtitle}
          </p>

          <div className="flex items-center gap-4 mt-8 text-muted-foreground">
            <div className="flex items-center gap-2" data-testid="text-temperature">
              <span className="material-symbols-outlined" style={{ fontSize: 16 }}>thermostat</span>
              <span className="font-display text-base font-medium">{formatTemp(data.temperature, settings.temperatureUnit)}</span>
            </div>
            <div className="flex items-center gap-2" data-testid="text-location">
              <MapPin className="w-4 h-4" />
              <span className="text-base">{data.location}</span>
            </div>
          </div>
        </div>
      </div>

      <div
        className="flex justify-center transition-all duration-500 ease-out absolute left-0 right-0"
        style={{
          bottom: expanded ? "calc(100dvh - 2.5rem)" : "0.75rem",
          opacity: expanded ? 0 : 0.5,
          pointerEvents: expanded ? "none" : "auto",
        }}
      >
        <button
          onClick={() => setExpanded(true)}
          className="flex flex-col items-center gap-0.5 p-2"
          data-testid="button-expand"
        >
          <ChevronUp className="w-5 h-5 text-muted-foreground animate-bounce" />
        </button>
      </div>

      <div
        ref={panelRef}
        className="absolute left-0 right-0 bottom-0 bg-background transition-all duration-500 ease-out overflow-y-auto"
        style={{
          height: expanded ? "100dvh" : "0",
          opacity: expanded ? 1 : 0,
        }}
      >
        <div className="sticky top-0 z-10 bg-background/80 backdrop-blur-md">
          <div className="flex justify-center pt-3 pb-1">
            <button
              onClick={() => setExpanded(false)}
              className="w-10 h-1 rounded-full bg-muted-foreground/30"
              data-testid="button-collapse"
            />
          </div>
        </div>

        <div className="px-5 pb-8">
          {isPokemonMode && (
            <div className="flex justify-center mb-4" data-testid="banner-pokemon-detail">
              <div className="rounded-full bg-[#ee1515] text-white font-display font-bold text-sm px-5 py-2 shadow-lg text-center">
                {POKEMON_BANNER}
              </div>
            </div>
          )}
          <Card className="mb-4" data-testid="card-main">
            <CardContent className="p-5">
              <div className="flex items-center justify-between gap-2 mb-1">
                <p className="text-sm text-muted-foreground lowercase tracking-wide" data-testid="text-detail-location">{data.location}</p>
                <Button
                  variant="ghost"
                  size="icon"
                  onClick={onRefresh}
                  disabled={isRefreshing}
                  data-testid="button-refresh-detail"
                >
                  <RefreshCw className={`w-4 h-4 ${isRefreshing ? "animate-spin" : ""}`} />
                </Button>
              </div>
              <p className="font-display text-7xl font-bold tracking-tighter leading-none mb-2" data-testid="text-detail-temp">
                {formatTemp(data.temperature, settings.temperatureUnit)}
              </p>
              <div className="flex items-center gap-2 text-sm text-muted-foreground mb-4">
                {isPokemonMode ? (
                  <PokeballGlyph size={16} />
                ) : (
                  <WeatherGlyph condition={data.condition} isDay={data.isDay} className="w-4 h-4" />
                )}
                <span data-testid="text-detail-condition">{data.condition}, feels like {formatTemp(data.feelsLike, settings.temperatureUnit)}</span>
              </div>

              {/* Day temperature range bar */}
              <div className="flex items-center gap-3 mb-5">
                <span className="text-sm font-display font-bold tabular-nums" style={{ color: COLD_COLOR }}>
                  {formatTemp(data.temperatureMin, settings.temperatureUnit)}
                </span>
                <div className="flex-1 h-2 rounded-full" style={{ background: `linear-gradient(to right, ${COLD_COLOR}, ${HOT_COLOR})` }} />
                <span className="text-sm font-display font-bold tabular-nums" style={{ color: HOT_COLOR }}>
                  {formatTemp(data.temperatureMax, settings.temperatureUnit)}
                </span>
              </div>

              <div className="bg-secondary/50 rounded-md p-4" data-testid="section-stats">
                <div className="grid grid-cols-2 gap-y-3 gap-x-6">
                  <div className="flex items-center gap-2">
                    <ArrowUp className="w-4 h-4" style={{ color: HOT_COLOR }} />
                    <span className="font-display font-semibold" data-testid="text-temp-max" style={{ color: HOT_COLOR }}>{convertTemp(data.temperatureMax, settings.temperatureUnit)}°</span>
                  </div>
                  <div className="flex items-center gap-2">
                    <ArrowDown className="w-4 h-4" style={{ color: COLD_COLOR }} />
                    <span className="font-display font-semibold" data-testid="text-temp-min" style={{ color: COLD_COLOR }}>{convertTemp(data.temperatureMin, settings.temperatureUnit)}°</span>
                  </div>
                  <div className="flex items-center justify-between gap-2">
                    <span className="text-sm text-muted-foreground">humidity</span>
                    <span className="text-sm font-display font-semibold" data-testid="text-humidity">{data.humidity}%</span>
                  </div>
                  <div className="flex items-center justify-between gap-2">
                    <span className="text-sm text-muted-foreground">wind</span>
                    <span className="text-sm font-display font-semibold" data-testid="text-wind">{Math.round(data.windSpeed)} km/h</span>
                  </div>
                  <div className="flex items-center justify-between gap-2">
                    <span className="text-sm text-muted-foreground">uv index</span>
                    <span className="text-sm font-display font-semibold" data-testid="text-uv">{data.uvIndex}</span>
                  </div>
                  <div className="flex items-center justify-between gap-2">
                    <span className="text-sm text-muted-foreground">pressure</span>
                    <span className="text-sm font-display font-semibold" data-testid="text-pressure">{data.pressure}</span>
                  </div>
                </div>
              </div>
            </CardContent>
          </Card>

          <Link href={`/precipitation?lat=${location.latitude}&lon=${location.longitude}`}>
            <Card className="mb-4 hover-elevate active-elevate-2 cursor-pointer" data-testid="card-precipitation-map">
              <CardContent className="p-4 flex items-center justify-between gap-3">
                <div className="flex items-center gap-3">
                  <div className="w-9 h-9 rounded-md bg-secondary flex items-center justify-center">
                    <Map className="w-4 h-4" />
                  </div>
                  <div>
                    <p className="text-sm font-display font-semibold lowercase">precipitation map</p>
                    <p className="text-xs text-muted-foreground">live radar overlay</p>
                  </div>
                </div>
                <ChevronRight className="w-4 h-4 text-muted-foreground" />
              </CardContent>
            </Card>
          </Link>

          <Card className="mb-4" data-testid="card-today">
            <CardContent className="p-5">
              <h3 className="font-display text-lg font-bold lowercase mb-4">today</h3>
              <ScrollArea className="w-full whitespace-nowrap">
                <div className="flex gap-5 pb-2">
                  {(() => {
                    const { hours, minT, range } = hourlyStats;
                    return hours.map((hour, i) => {
                      const norm = (hour.temperature - minT) / range;
                      const pct = norm * 100;
                      const color = tempColor(norm);
                      return (
                        <div key={i} className="flex flex-col items-center gap-2 min-w-[2.5rem]" data-testid={`hourly-item-${i}`}>
                          <span className="text-xs text-muted-foreground">{i === 0 ? "now" : formatTime(hour.time, settings.timeFormat).replace(":00", "").replace(" AM", "a").replace(" PM", "p")}</span>
                          <div className="relative h-16 flex items-end justify-center">
                            <div
                              className="w-1 rounded-full"
                              style={{ height: `${Math.max(pct, 10)}%`, backgroundColor: color, opacity: 0.35 }}
                            />
                            <div
                              className="absolute w-2.5 h-2.5 rounded-full border-2 border-background"
                              style={{ bottom: `${Math.max(pct, 5)}%`, backgroundColor: color }}
                            />
                          </div>
                          <span className="text-xs font-display font-bold tabular-nums" style={{ color }}>{convertTemp(hour.temperature, settings.temperatureUnit)}°</span>
                        </div>
                      );
                    });
                  })()}
                </div>
                <ScrollBar orientation="horizontal" />
              </ScrollArea>
            </CardContent>
          </Card>

          <Card data-testid="card-weekly">
            <CardContent className="p-5">
              <h3 className="font-display text-lg font-bold mb-5 lowercase">next 7 days</h3>
              <div className="flex flex-col gap-4">
                {(() => {
                  const { weekMin, weekRange } = weeklyStats;
                  return data.dailyForecast.map((day, i) => {
                    const leftPct = ((day.temperatureMin - weekMin) / weekRange) * 100;
                    const widthPct = ((day.temperatureMax - day.temperatureMin) / weekRange) * 100;
                    const minNorm = (day.temperatureMin - weekMin) / weekRange;
                    const maxNorm = (day.temperatureMax - weekMin) / weekRange;
                    return (
                      <div key={i} className="flex items-center gap-3" data-testid={`daily-item-${i}`}>
                        <div className="w-20 shrink-0">
                          <p className="text-sm font-display font-semibold lowercase leading-tight">{day.day}</p>
                          <p className="text-xs text-muted-foreground">{formatDate(day.date, settings.dateFormat)}</p>
                        </div>
                        <span className="text-xs font-display font-semibold tabular-nums w-8 text-right" style={{ color: tempColor(minNorm) }}>{convertTemp(day.temperatureMin, settings.temperatureUnit)}°</span>
                        <div className="flex-1 h-2 rounded-full bg-secondary relative overflow-hidden">
                          <div
                            className="absolute h-full rounded-full"
                            style={{
                              left: `${leftPct}%`,
                              width: `${Math.max(widthPct, 4)}%`,
                              background: `linear-gradient(to right, ${tempColor(minNorm)}, ${tempColor(maxNorm)})`,
                            }}
                          />
                        </div>
                        <span className="text-xs font-display font-bold tabular-nums w-8" style={{ color: tempColor(maxNorm) }} data-testid={`daily-temps-${i}`}>{convertTemp(day.temperatureMax, settings.temperatureUnit)}°</span>
                      </div>
                    );
                  });
                })()}
              </div>
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  );
}

export default function Home() {
  const [location, setLocation] = useState<{ latitude: number; longitude: number } | null>(null);
  const [locationError, setLocationError] = useState<string | null>(null);
  const [locationRequested, setLocationRequested] = useState(false);
  const { settings } = useSettings();
  const { toast } = useToast();

  // Pokémon mode is persisted in settings (localStorage), so it survives a
  // refresh while Pallet Town stays selected. Only active in manual mode.
  const isPokemonMode = settings.pokemonMode && settings.locationMode === "manual";

  useEffect(() => {
    const original = document.title;
    if (isPokemonMode) {
      document.title = POKEMON_TITLE;
    }
    return () => {
      document.title = original;
    };
  }, [isPokemonMode]);

  const requestLocation = useCallback(() => {
    setLocationRequested(true);
    setLocationError(null);
    
    if (!navigator.geolocation) {
      setLocationError("Geolocation is not supported by your browser.");
      return;
    }

    navigator.geolocation.getCurrentPosition(
      (position) => {
        setLocation({
          latitude: position.coords.latitude,
          longitude: position.coords.longitude,
        });
      },
      (error) => {
        switch (error.code) {
          case error.PERMISSION_DENIED:
            setLocationError("Location access was denied. Please allow it in your browser settings.");
            break;
          case error.POSITION_UNAVAILABLE:
            setLocationError("Location information is unavailable.");
            break;
          case error.TIMEOUT:
            setLocationError("The request for location timed out.");
            break;
          default:
            setLocationError("An unknown error occurred while getting location.");
        }
      },
      {
        enableHighAccuracy: false,
        timeout: 10000,
        maximumAge: 300000,
      }
    );
  }, []);

  useEffect(() => {
    if (settings.locationMode === "manual" && settings.manualCoords) {
      setLocation(settings.manualCoords);
      setLocationError(null);
      setLocationRequested(true);
    } else if (settings.locationMode === "device") {
      requestLocation();
    }
  }, [settings.locationMode, settings.manualCoords, requestLocation]);

  const { data, isLoading, isError, error, refetch, isRefetching } = useQuery<WeatherData>({
    queryKey: ["/api/weather", location?.latitude, location?.longitude, settings.weatherService, settings.weatherApiKey],
    queryFn: async ({ signal }) => {
      const url = `/api/weather/${location!.latitude}/${location!.longitude}?service=${settings.weatherService}`;
      const headers: Record<string, string> = {};
      if (settings.weatherService !== "openmeteo" && settings.weatherApiKey) {
        headers["X-Weather-Api-Key"] = settings.weatherApiKey;
      }
      // `signal` aborts the request if the query is cancelled (e.g. location
      // changes or the component unmounts mid-flight).
      const t0 = performance.now();
      const res = await fetch(url, { headers, credentials: "include", signal });
      const ms = Math.round(performance.now() - t0);
      if (ms > 500) {
        console.warn(`[perf] SLOW weather fetch: ${ms}ms`);
      } else {
        console.log(`[perf] weather fetch: ${ms}ms`);
      }
      if (!res.ok) {
        const text = await res.text().catch(() => "");
        throw new Error(text || "Failed to fetch weather data");
      }
      return res.json();
    },
    enabled: !!location,
  });

  useEffect(() => {
    if (!settings.notificationsEnabled || !data) return;
    
    const hasPrecipSoon = data.precipitationChance > 50 || 
      data.condition === "rainy" || 
      data.condition === "snowy" || 
      data.condition === "stormy";
    
    if (hasPrecipSoon) {
      const lastNotified = sessionStorage.getItem("last-precip-notification");
      const now = Date.now();
      if (!lastNotified || now - parseInt(lastNotified) > 1800000) {
        const title = "Precipitation Alert";
        const precipType = data.condition === "snowy" ? "Snow" : data.condition === "stormy" ? "Storms" : "Rain";
        let body = `${precipType} expected — ${data.precipitationChance}% chance today.`;

        if (data.hourlyForecast.length > 0) {
          const upcoming = data.hourlyForecast.filter(h => (h.precipitationChance ?? 0) > 30).slice(0, 6);
          const peakHour = data.hourlyForecast.reduce((max, h) => (h.precipitationChance ?? 0) > (max.precipitationChance ?? 0) ? h : max, data.hourlyForecast[0]);
          const peakChance = peakHour?.precipitationChance ?? 0;

          if (upcoming.length > 0) {
            const startTime = upcoming[0].time;
            const endTime = upcoming[upcoming.length - 1].time;
            if (startTime === endTime) {
              body += ` Most likely around ${startTime}.`;
            } else {
              body += ` Expected between ${startTime}–${endTime}.`;
            }
          }
          if (peakChance > 0 && peakHour) {
            body += ` Peak: ${peakChance}% at ${peakHour.time}.`;
          }
        }

        body += data.condition === "snowy"
          ? " Bundle up and watch for slippery conditions."
          : data.condition === "stormy"
          ? " Stay indoors if you can."
          : " You might want an umbrella.";

        sendNotification(title, body).then((sent) => {
          if (!sent) {
            toast({ title, description: body });
          }
        });
        sessionStorage.setItem("last-precip-notification", String(now));
      }
    }
  }, [data, settings.notificationsEnabled, toast]);

  if (!locationRequested || (!location && !locationError)) {
    return <LoadingSkeleton />;
  }

  if (locationError) {
    return <LocationPermission onRetry={requestLocation} />;
  }

  if (isLoading) {
    return <LoadingSkeleton />;
  }

  if (isError) {
    return (
      <ErrorState 
        message={error?.message || "Failed to fetch weather data."} 
        onRetry={() => refetch()} 
      />
    );
  }

  if (!data) {
    return (
      <ErrorState 
        message="No weather data available." 
        onRetry={() => refetch()} 
      />
    );
  }

  return (
    <WeatherDisplay 
      data={data} 
      onRefresh={() => refetch()} 
      isRefreshing={isRefetching}
      location={location!}
      isPokemonMode={isPokemonMode}
    />
  );
}
