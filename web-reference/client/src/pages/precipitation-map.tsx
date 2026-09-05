import { useState, useEffect, useRef, useCallback, useMemo } from "react";
import { Link, useSearch } from "wouter";
import { MapContainer, TileLayer, CircleMarker, useMap } from "react-leaflet";
import L from "leaflet";
import "leaflet/dist/leaflet.css";
import { ArrowLeft, Play, Pause } from "lucide-react";
import { Button } from "@/components/ui/button";

interface RadarFrame {
  path: string;
  time: number;
}

interface RainViewerData {
  radar: {
    past: RadarFrame[];
    nowcast: RadarFrame[];
  };
}

function MapInvalidator() {
  const map = useMap();
  useEffect(() => {
    const invalidate = () => map.invalidateSize();
    // Multiple passes: mobile browsers settle their toolbar/viewport late,
    // so a single early invalidate can leave the map mis-sized (gray tiles).
    const t1 = setTimeout(invalidate, 100);
    const t2 = setTimeout(invalidate, 500);
    window.addEventListener("resize", invalidate);
    window.addEventListener("orientationchange", invalidate);
    return () => {
      clearTimeout(t1);
      clearTimeout(t2);
      window.removeEventListener("resize", invalidate);
      window.removeEventListener("orientationchange", invalidate);
    };
  }, [map]);
  return null;
}

const RADAR_OPACITY = 0.6;

function RadarLayer({
  frames,
  currentIndex,
}: {
  frames: RadarFrame[];
  currentIndex: number;
}) {
  const map = useMap();
  // Cache one tile layer per frame so radar tiles are loaded once and then
  // just toggled via opacity. Recreating layers every frame caused the radar
  // to never finish loading on slower mobile connections.
  const layersRef = useRef<Record<string, L.TileLayer>>({});

  const currentFrame = frames[currentIndex] || null;

  // Remove cached layers for frames that no longer exist.
  useEffect(() => {
    const validPaths = new Set(frames.map((f) => f.path));
    for (const path of Object.keys(layersRef.current)) {
      if (!validPaths.has(path)) {
        try { map.removeLayer(layersRef.current[path]); } catch (_) {}
        delete layersRef.current[path];
      }
    }
  }, [frames, map]);

  // Show the current frame (creating its layer lazily and caching it), hide
  // all others. Cached layers keep their tiles, so looping is instant.
  useEffect(() => {
    if (!currentFrame) return;

    if (!layersRef.current[currentFrame.path]) {
      const layer = L.tileLayer(
        `https://tilecache.rainviewer.com${currentFrame.path}/256/{z}/{x}/{y}/2/1_1.png`,
        {
          opacity: 0,
          zIndex: 100,
          tileSize: 256,
          maxNativeZoom: 12,
          maxZoom: 18,
        }
      );
      layer.addTo(map);
      layersRef.current[currentFrame.path] = layer;
    }

    for (const [path, layer] of Object.entries(layersRef.current)) {
      layer.setOpacity(path === currentFrame.path ? RADAR_OPACITY : 0);
    }
  }, [currentFrame, map]);

  // Clean up every cached layer when the map goes away.
  useEffect(() => {
    return () => {
      for (const layer of Object.values(layersRef.current)) {
        try { map.removeLayer(layer); } catch (_) {}
      }
      layersRef.current = {};
    };
  }, [map]);

  return null;
}

function formatTime(unix: number): string {
  const date = new Date(unix * 1000);
  return date.toLocaleTimeString([], { hour: "numeric", minute: "2-digit" });
}

function formatHour(unix: number): string {
  const date = new Date(unix * 1000);
  return date.toLocaleTimeString([], { hour: "numeric" });
}

export default function PrecipitationMap() {
  const [frames, setFrames] = useState<RadarFrame[]>([]);
  const [pastCount, setPastCount] = useState(0);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [isPlaying, setIsPlaying] = useState(false);
  const [radarError, setRadarError] = useState(false);
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const searchString = useSearch();
  const params = useMemo(() => new URLSearchParams(searchString), [searchString]);
  const lat = parseFloat(params.get("lat") || "40.7");
  const lon = parseFloat(params.get("lon") || "-74.0");

  useEffect(() => {
    let cancelled = false;
    // Abort the radar metadata request if it stalls — on flaky mobile networks
    // a hanging fetch must not block the rest of the page.
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 12000);

    fetch("https://api.rainviewer.com/public/weather-maps.json", {
      signal: controller.signal,
    })
      .then((res) => {
        if (!res.ok) throw new Error(`RainViewer responded ${res.status}`);
        return res.json();
      })
      .then((data: RainViewerData) => {
        if (cancelled) return;
        const allFrames = [
          ...(data?.radar?.past ?? []),
          ...(data?.radar?.nowcast ?? []),
        ];
        if (allFrames.length === 0) {
          setRadarError(true);
          return;
        }
        setFrames(allFrames);
        setPastCount(data.radar.past.length);
        setCurrentIndex(0);
        setIsPlaying(true);
      })
      .catch(() => {
        if (cancelled) return;
        setRadarError(true);
      })
      .finally(() => {
        clearTimeout(timeout);
      });

    return () => {
      cancelled = true;
      clearTimeout(timeout);
      controller.abort();
    };
  }, []);

  const togglePlay = useCallback(() => {
    setIsPlaying((prev) => !prev);
  }, []);

  useEffect(() => {
    if (isPlaying && frames.length > 0) {
      intervalRef.current = setInterval(() => {
        setCurrentIndex((prev) => (prev + 1) % frames.length);
      }, 800);
    } else if (intervalRef.current) {
      clearInterval(intervalRef.current);
      intervalRef.current = null;
    }

    return () => {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
        intervalRef.current = null;
      }
    };
  }, [isPlaying, frames.length]);

  const currentFrame = frames[currentIndex] || null;

  const timeLabels = useMemo(() => {
    if (frames.length === 0) return [];
    const labels: { index: number; label: string }[] = [];
    let lastHour = -1;
    frames.forEach((frame, i) => {
      const date = new Date(frame.time * 1000);
      const hour = date.getHours();
      if (hour !== lastHour) {
        labels.push({ index: i, label: formatHour(frame.time) });
        lastHour = hour;
      }
    });
    return labels;
  }, [frames]);

  return (
    <div
      className="fixed inset-0 flex flex-col bg-background"
      style={{
        animation: "fadeIn 0.4s ease-out",
      }}
    >
      <style>{`
        @keyframes fadeIn {
          from { opacity: 0; }
          to { opacity: 1; }
        }
        @keyframes pulse-dot {
          0%, 100% { transform: scale(1); opacity: 0.9; }
          50% { transform: scale(1.5); opacity: 0.4; }
        }
        .leaflet-tile-pane .leaflet-layer {
          transition: opacity 0.4s ease;
        }
        .leaflet-container {
          background: hsl(var(--background));
        }
      `}</style>

      <div className="absolute top-0 left-0 right-0 z-[1000] p-4">
        <div className="flex items-center gap-3">
          <Button
            variant="ghost"
            size="icon"
            className="bg-background/70 backdrop-blur-sm"
            data-testid="button-back"
            asChild
          >
            <Link href="/">
              <ArrowLeft />
            </Link>
          </Button>
          <h1 className="font-display text-lg font-semibold bg-background/70 backdrop-blur-sm px-3 py-1.5 rounded-md">
            Precipitation forecast
          </h1>
        </div>
      </div>

      <div className="flex-1" data-testid="map-container">
        <MapContainer
            center={[lat, lon]}
            zoom={7}
            style={{ height: "100%", width: "100%" }}
            zoomControl={false}
            attributionControl={true}
          >
            <TileLayer
              url="https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png"
              attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors &copy; <a href="https://carto.com/">CARTO</a>'
            />
            <MapInvalidator />
            <RadarLayer frames={frames} currentIndex={currentIndex} />
            <CircleMarker
              center={[lat, lon]}
              radius={8}
              pathOptions={{
                color: "hsl(0, 0%, 9%)",
                fillColor: "hsl(0, 0%, 9%)",
                fillOpacity: 0.9,
                weight: 2,
              }}
            />
            <CircleMarker
              center={[lat, lon]}
              radius={14}
              pathOptions={{
                color: "hsl(0, 0%, 9%)",
                fillColor: "transparent",
                fillOpacity: 0,
                weight: 1.5,
                opacity: 0.3,
              }}
            />
          </MapContainer>
      </div>

      <div className="absolute bottom-0 left-0 right-0 z-[1000]">
        <div
          className="mx-4 mb-2 flex items-center gap-3 px-4 py-2 rounded-md bg-background/70 backdrop-blur-sm"
          data-testid="legend-precipitation"
        >
          <span className="text-xs text-muted-foreground whitespace-nowrap">Light</span>
          <div
            className="flex-1 h-3 rounded-full"
            style={{
              background:
                "linear-gradient(to right, #88ff88, #ffff00, #ff8800, #ff0000, #cc00cc, #0000ff)",
            }}
          />
          <span className="text-xs text-muted-foreground whitespace-nowrap">Heavy</span>
        </div>

        <div className="bg-background border-t px-4 pt-3 pb-4" data-testid="section-timeline">
          {/* Controls row */}
          <div className="flex items-center gap-3 mb-3">
            <Button
              variant="outline"
              size="icon"
              className="rounded-full shrink-0"
              onClick={togglePlay}
              disabled={frames.length === 0}
              data-testid="button-play-pause"
            >
              {isPlaying ? <Pause className="w-4 h-4" /> : <Play className="w-4 h-4" />}
            </Button>
            <div className="flex items-baseline gap-2">
              <span
                className="font-display text-base font-semibold tabular-nums"
                data-testid="text-current-time"
              >
                {currentFrame ? formatTime(currentFrame.time) : "--:--"}
              </span>
              {currentFrame ? (
                <span className="text-xs text-muted-foreground">
                  {currentIndex >= pastCount ? "forecast" : "radar"}
                </span>
              ) : radarError ? (
                <span className="text-xs text-muted-foreground" data-testid="text-radar-status">
                  Radar unavailable
                </span>
              ) : (
                <span className="text-xs text-muted-foreground" data-testid="text-radar-status">
                  Loading radar…
                </span>
              )}
            </div>
          </div>

          {/* Fixed-height bar scrubber — no layout shift */}
          <div className="flex items-stretch gap-[2px] h-5 mb-1.5">
            {frames.map((_, i) => {
              const isActive = i === currentIndex;
              const isPast = i < currentIndex;
              const isNowcast = i >= pastCount;
              let bgColor: string;
              if (isActive) {
                bgColor = "hsl(var(--foreground))";
              } else if (isPast) {
                bgColor = isNowcast
                  ? "hsl(var(--foreground) / 0.25)"
                  : "hsl(var(--foreground) / 0.35)";
              } else {
                bgColor = isNowcast
                  ? "hsl(var(--foreground) / 0.12)"
                  : "hsl(var(--muted-foreground) / 0.2)";
              }
              return (
                <button
                  key={i}
                  data-testid={`button-frame-${i}`}
                  className="flex-1 rounded-[2px] cursor-pointer transition-colors duration-100"
                  style={{ backgroundColor: bgColor }}
                  onClick={() => {
                    setCurrentIndex(i);
                    setIsPlaying(false);
                  }}
                />
              );
            })}
          </div>

          {/* Time labels — fixed height relative container, no overflow */}
          <div className="relative h-4">
            {timeLabels.map((label, idx) => {
              const pct = (label.index / Math.max(frames.length - 1, 1)) * 100;
              const isFirst = idx === 0;
              const isLast = idx === timeLabels.length - 1;
              const transform = isFirst
                ? "translateX(0%)"
                : isLast
                ? "translateX(-100%)"
                : "translateX(-50%)";
              return (
                <span
                  key={label.index}
                  className="absolute top-0 text-[10px] text-muted-foreground whitespace-nowrap"
                  style={{ left: `${pct}%`, transform }}
                >
                  {label.label}
                </span>
              );
            })}
          </div>
        </div>
      </div>
    </div>
  );
}
