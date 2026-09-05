import { useState, useRef, useCallback, useMemo, memo } from "react";
import { Cloud, Sun, Snowflake, Zap, Droplet, Wind, type LucideIcon } from "lucide-react";
import type { WeatherCondition } from "@shared/schema";
import { getWeatherIcon } from "@/lib/weatherIcons";
import { getWeatherIconComponent } from "@/components/weather-icon";

interface AnimatedWeatherIconProps {
  condition: WeatherCondition;
  isDay: boolean;
  className?: string;
  size?: number;
  "data-testid"?: string;
}

const animationMap: Record<WeatherCondition, string> = {
  clear: "weather-icon-spin-slow",
  hot: "weather-icon-pulse",
  cloudy: "weather-icon-drift",
  rainy: "weather-icon-bounce-subtle",
  stormy: "weather-icon-flash",
  snowy: "weather-icon-drift",
  cold: "weather-icon-spin-slow",
  foggy: "weather-icon-fade-pulse",
  windy: "weather-icon-sway",
};

const EXPLODE_FRAGMENTS: LucideIcon[] = [Cloud, Sun, Snowflake, Zap, Droplet, Wind];

function AnimatedWeatherIconImpl({
  condition,
  isDay,
  className,
  size,
  "data-testid": testId,
}: AnimatedWeatherIconProps) {
  const [exploding, setExploding] = useState(false);
  const clickTimesRef = useRef<number[]>([]);

  const handleClick = useCallback(() => {
    if (exploding) return;

    const now = Date.now();
    const times = clickTimesRef.current;

    if (times.length > 0 && now - times[times.length - 1] > 300) {
      clickTimesRef.current = [now];
      return;
    }

    times.push(now);

    if (times.length >= 5) {
      clickTimesRef.current = [];
      setExploding(true);
      setTimeout(() => {
        setExploding(false);
      }, 1000);
    }
  }, [exploding]);

  // Cache the icon decision so it only recomputes when the inputs actually change.
  const iconKey = useMemo(() => getWeatherIcon(condition, isDay), [condition, isDay]);
  const Icon = getWeatherIconComponent(iconKey);
  const animClass = animationMap[condition] || "";
  const fontSize = size || 96;

  if (exploding) {
    const fragSize = Math.round(fontSize * 0.3);
    return (
      <div className={className} data-testid={testId}>
        <div className="weather-explode-container" style={{ width: fontSize, height: fontSize, position: "relative" }}>
          {EXPLODE_FRAGMENTS.map((Frag, i) => (
            <div
              key={i}
              className={`weather-fragment weather-fragment-${i + 1}`}
              style={{
                position: "absolute",
                left: "50%",
                top: "50%",
                marginLeft: -fragSize / 2,
                marginTop: -fragSize / 2,
              }}
            >
              <Frag size={fragSize} strokeWidth={1.5} style={{ display: "block" }} />
            </div>
          ))}
        </div>
      </div>
    );
  }

  return (
    <div className={className} data-testid={testId} onClick={handleClick} style={{ cursor: "pointer" }}>
      {/* Fixed-size wrapper prevents layout shift; keyed by iconKey so the fade/scale
          enter animation replays only when the icon truly changes. */}
      <div
        key={iconKey}
        className="weather-icon-enter"
        style={{ width: fontSize, height: fontSize, display: "flex", alignItems: "center", justifyContent: "center" }}
      >
        <Icon className={animClass} size={fontSize} strokeWidth={1.5} style={{ color: "currentColor" }} />
      </div>
    </div>
  );
}

export const AnimatedWeatherIcon = memo(AnimatedWeatherIconImpl);
