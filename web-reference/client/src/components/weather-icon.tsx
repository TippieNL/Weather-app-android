import {
  Sun,
  Moon,
  CloudSun,
  CloudMoon,
  CloudSunRain,
  CloudMoonRain,
  CloudLightning,
  CloudSnow,
  CloudFog,
  Wind,
  Flame,
  Snowflake,
  type LucideIcon,
} from "lucide-react";
import type { WeatherCondition } from "@shared/schema";
import { getWeatherIcon, type WeatherIconKey } from "@/lib/weatherIcons";

/**
 * Icon rendering — maps icon keys to a single, consistent SVG pack (lucide).
 * Using one pack keeps every weather glyph visually consistent and tree-shakeable.
 * lucide ships proper day/night variants for the sky-based conditions.
 */
const ICON_COMPONENTS: Record<WeatherIconKey, LucideIcon> = {
  "clear-day": Sun,
  "clear-night": Moon,
  "cloudy-day": CloudSun,
  "cloudy-night": CloudMoon,
  "rainy-day": CloudSunRain,
  "rainy-night": CloudMoonRain,
  stormy: CloudLightning,
  snowy: CloudSnow,
  foggy: CloudFog,
  windy: Wind,
  hot: Flame,
  cold: Snowflake,
};

export function getWeatherIconComponent(key: WeatherIconKey): LucideIcon {
  return ICON_COMPONENTS[key];
}

interface WeatherGlyphProps {
  condition: WeatherCondition;
  isDay: boolean;
  className?: string;
  size?: number;
  strokeWidth?: number;
  "data-testid"?: string;
}

/**
 * Lightweight inline SVG weather glyph for small/static placements (no animation).
 * For the large animated hero icon, use `AnimatedWeatherIcon`.
 */
export function WeatherGlyph({
  condition,
  isDay,
  className,
  size = 16,
  strokeWidth = 1.75,
  "data-testid": testId,
}: WeatherGlyphProps) {
  const Icon = getWeatherIconComponent(getWeatherIcon(condition, isDay));
  return (
    <Icon className={className} size={size} strokeWidth={strokeWidth} data-testid={testId} />
  );
}
