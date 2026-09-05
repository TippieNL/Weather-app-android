import { createContext, useContext, useState, useEffect, type ReactNode } from "react";

export type TemperatureUnit = "celsius" | "fahrenheit";
export type TimeFormat = "12h" | "24h";
export type DateFormat = "MM/DD" | "DD/MM" | "YYYY-MM-DD";
export type LocationMode = "device" | "manual";
export type WeatherService = "openmeteo" | "openweathermap" | "weatherapi";

export interface Settings {
  temperatureUnit: TemperatureUnit;
  timeFormat: TimeFormat;
  dateFormat: DateFormat;
  locationMode: LocationMode;
  manualLocation: string;
  manualCoords: { latitude: number; longitude: number } | null;
  weatherService: WeatherService;
  weatherApiKey: string;
  notificationsEnabled: boolean;
  pokemonMode: boolean;
}

const defaultSettings: Settings = {
  temperatureUnit: "celsius",
  timeFormat: "24h",
  dateFormat: "DD/MM",
  locationMode: "device",
  manualLocation: "",
  manualCoords: null,
  weatherService: "openmeteo",
  weatherApiKey: "",
  notificationsEnabled: false,
  pokemonMode: false,
};

const STORAGE_KEY = "weather-app-settings";

function loadSettings(): Settings {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored) {
      return { ...defaultSettings, ...JSON.parse(stored) };
    }
  } catch {
    // ignore parse errors
  }
  return defaultSettings;
}

function saveSettings(settings: Settings) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(settings));
  } catch {
    // ignore storage errors
  }
}

interface SettingsContextValue {
  settings: Settings;
  updateSettings: (partial: Partial<Settings>) => void;
}

const SettingsContext = createContext<SettingsContextValue | null>(null);

export function SettingsProvider({ children }: { children: ReactNode }) {
  const [settings, setSettings] = useState<Settings>(loadSettings);

  useEffect(() => {
    saveSettings(settings);
  }, [settings]);

  const updateSettings = (partial: Partial<Settings>) => {
    setSettings((prev) => ({ ...prev, ...partial }));
  };

  return (
    <SettingsContext.Provider value={{ settings, updateSettings }}>
      {children}
    </SettingsContext.Provider>
  );
}

export function useSettings() {
  const context = useContext(SettingsContext);
  if (!context) {
    throw new Error("useSettings must be used within a SettingsProvider");
  }
  return context;
}

export function convertTemp(celsius: number, unit: TemperatureUnit): number {
  if (unit === "fahrenheit") {
    return Math.round(celsius * 9 / 5 + 32);
  }
  return Math.round(celsius);
}

export function formatTemp(celsius: number, unit: TemperatureUnit): string {
  const value = convertTemp(celsius, unit);
  return unit === "fahrenheit" ? `${value}\u00B0F` : `${value}\u00B0C`;
}

export function formatTime(timeStr: string, format: TimeFormat): string {
  if (format === "24h") {
    return timeStr;
  }
  const [hourStr, minuteStr] = timeStr.split(":");
  let hour = parseInt(hourStr, 10);
  const suffix = hour >= 12 ? "PM" : "AM";
  if (hour === 0) {
    hour = 12;
  } else if (hour > 12) {
    hour -= 12;
  }
  return `${hour}:${minuteStr} ${suffix}`;
}

export function formatDate(dateStr: string, format: DateFormat): string {
  const [year, month, day] = dateStr.split("-");
  switch (format) {
    case "MM/DD":
      return `${month}/${day}`;
    case "DD/MM":
      return `${day}/${month}`;
    case "YYYY-MM-DD":
      return dateStr;
    default:
      return dateStr;
  }
}
