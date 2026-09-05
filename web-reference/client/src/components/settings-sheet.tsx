import { useState } from "react";
import { Settings as SettingsIcon } from "lucide-react";
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from "@/components/ui/sheet";
import { Switch } from "@/components/ui/switch";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Separator } from "@/components/ui/separator";
import { useSettings } from "@/contexts/settings";
import { sendNotification } from "@/lib/notifications";
import { useToast } from "@/hooks/use-toast";
import { isPalletTown, PALLET_TOWN_COORDS, POKEMON_BANNER, playPokemonSound } from "@/lib/pokemon";

export function SettingsSheet() {
  const { settings, updateSettings } = useSettings();
  const [geocodeStatus, setGeocodeStatus] = useState<string>("");
  const [geocodeLoading, setGeocodeLoading] = useState(false);
  const { toast } = useToast();

  async function handleFindLocation() {
    if (!settings.manualLocation.trim()) return;

    // Secret Pallet Town Easter egg — bypass geocoding (it's fictional). Keep
    // the weather for whatever city was already selected; only fall back to a
    // stand-in coastal location if no location has been set yet.
    if (isPalletTown(settings.manualLocation)) {
      updateSettings({
        manualCoords: settings.manualCoords ?? { ...PALLET_TOWN_COORDS },
        pokemonMode: true,
      });
      setGeocodeStatus(POKEMON_BANNER);
      playPokemonSound();
      return;
    }

    setGeocodeLoading(true);
    setGeocodeStatus("");
    try {
      const res = await fetch(
        `/api/geocode?query=${encodeURIComponent(settings.manualLocation)}`
      );
      if (!res.ok) {
        const data = await res.json().catch(() => ({}));
        setGeocodeStatus(data.message || "Location not found");
        return;
      }
      const data = await res.json();
      updateSettings({
        manualCoords: { latitude: data.latitude, longitude: data.longitude },
        pokemonMode: false,
      });
      setGeocodeStatus("Location found");
    } catch {
      setGeocodeStatus("Failed to find location");
    } finally {
      setGeocodeLoading(false);
    }
  }

  function handleExitPokemonMode() {
    // Turn off the Easter egg. Weather location (manualCoords) is left intact;
    // we just clear the "Pallet Town" text so the search box isn't stale.
    updateSettings({ pokemonMode: false, manualLocation: "" });
    setGeocodeStatus("");
  }

  async function handleNotificationToggle(checked: boolean) {
    if (checked) {
      if (!("Notification" in window)) {
        return;
      }
      const permission = await Notification.requestPermission();
      if (permission !== "granted") {
        setGeocodeStatus("");
        updateSettings({ notificationsEnabled: false });
        return;
      }
    }
    updateSettings({ notificationsEnabled: checked });
  }

  return (
    <Sheet>
      <SheetTrigger asChild>
        <Button
          variant="ghost"
          size="icon"
          data-testid="button-settings"
        >
          <SettingsIcon />
        </Button>
      </SheetTrigger>
      <SheetContent side="right" className="overflow-y-auto">
        <SheetHeader>
          <SheetTitle className="font-display">Settings</SheetTitle>
        </SheetHeader>

        <div className="flex flex-col gap-6 mt-6">
          <div className="flex flex-col gap-3">
            <span className="text-sm font-display font-semibold">Temperature Unit</span>
            <div className="flex flex-col gap-1.5">
              <Label className="text-sm text-muted-foreground">Unit</Label>
              <Select
                value={settings.temperatureUnit}
                onValueChange={(v) =>
                  updateSettings({ temperatureUnit: v as "celsius" | "fahrenheit" })
                }
              >
                <SelectTrigger data-testid="select-temp-unit">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="celsius">Celsius (&deg;C)</SelectItem>
                  <SelectItem value="fahrenheit">Fahrenheit (&deg;F)</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>

          <Separator />

          <div className="flex flex-col gap-3">
            <span className="text-sm font-display font-semibold">Date &amp; Time</span>
            <div className="flex flex-col gap-1.5">
              <Label className="text-sm text-muted-foreground">Time format</Label>
              <Select
                value={settings.timeFormat}
                onValueChange={(v) =>
                  updateSettings({ timeFormat: v as "12h" | "24h" })
                }
              >
                <SelectTrigger data-testid="select-time-format">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="12h">12-hour</SelectItem>
                  <SelectItem value="24h">24-hour</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="flex flex-col gap-1.5">
              <Label className="text-sm text-muted-foreground">Date format</Label>
              <Select
                value={settings.dateFormat}
                onValueChange={(v) =>
                  updateSettings({ dateFormat: v as "DD/MM" | "MM/DD" | "YYYY-MM-DD" })
                }
              >
                <SelectTrigger data-testid="select-date-format">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="DD/MM">DD/MM</SelectItem>
                  <SelectItem value="MM/DD">MM/DD</SelectItem>
                  <SelectItem value="YYYY-MM-DD">YYYY-MM-DD</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>

          <Separator />

          <div className="flex flex-col gap-3">
            <span className="text-sm font-display font-semibold">Location</span>
            <div className="flex flex-col gap-1.5">
              <Label className="text-sm text-muted-foreground">Mode</Label>
              <Select
                value={settings.locationMode}
                onValueChange={(v) =>
                  updateSettings({ locationMode: v as "device" | "manual" })
                }
              >
                <SelectTrigger data-testid="select-location-mode">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="device">Device GPS</SelectItem>
                  <SelectItem value="manual">Manual</SelectItem>
                </SelectContent>
              </Select>
            </div>
            {settings.locationMode === "manual" && (
              <div className="flex flex-col gap-1.5">
                <Label className="text-sm text-muted-foreground">City / Place</Label>
                <div className="flex gap-2">
                  <Input
                    data-testid="input-manual-location"
                    placeholder="Enter city name"
                    value={settings.manualLocation}
                    onChange={(e) =>
                      updateSettings({ manualLocation: e.target.value })
                    }
                  />
                  <Button
                    variant="secondary"
                    data-testid="button-find-location"
                    onClick={handleFindLocation}
                    disabled={geocodeLoading}
                  >
                    Find
                  </Button>
                </div>
                {geocodeStatus && (
                  <p className="text-xs text-muted-foreground">{geocodeStatus}</p>
                )}
              </div>
            )}
            {settings.pokemonMode && (
              <Button
                variant="outline"
                className="border-[#ee1515] text-[#ee1515] hover:bg-[#ee1515]/10"
                data-testid="button-exit-pokemon"
                onClick={handleExitPokemonMode}
              >
                Exit Pokémon Mode
              </Button>
            )}
          </div>

          <Separator />

          <div className="flex flex-col gap-3">
            <span className="text-sm font-display font-semibold">Weather Service</span>
            <div className="flex flex-col gap-1.5">
              <Label className="text-sm text-muted-foreground">Provider</Label>
              <Select
                value={settings.weatherService}
                onValueChange={(v) =>
                  updateSettings({
                    weatherService: v as "openmeteo" | "openweathermap" | "weatherapi",
                  })
                }
              >
                <SelectTrigger data-testid="select-weather-service">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="openmeteo">Open-Meteo (Free)</SelectItem>
                  <SelectItem value="openweathermap">OpenWeatherMap</SelectItem>
                  <SelectItem value="weatherapi">WeatherAPI</SelectItem>
                </SelectContent>
              </Select>
            </div>
            {(settings.weatherService === "openweathermap" ||
              settings.weatherService === "weatherapi") && (
              <div className="flex flex-col gap-1.5">
                <Label className="text-sm text-muted-foreground">API Key</Label>
                <Input
                  data-testid="input-api-key"
                  placeholder="Enter API key"
                  value={settings.weatherApiKey}
                  onChange={(e) =>
                    updateSettings({ weatherApiKey: e.target.value })
                  }
                />
              </div>
            )}
          </div>

          <Separator />

          <div className="flex flex-col gap-3">
            <span className="text-sm font-display font-semibold">Notifications</span>
            <div className="flex items-center justify-between gap-2">
              <div className="flex flex-col gap-1">
                <Label className="text-sm">Precipitation alerts</Label>
                <p className="text-xs text-muted-foreground">
                  Get notified when rain or snow is approaching
                </p>
              </div>
              <Switch
                data-testid="switch-notifications"
                checked={settings.notificationsEnabled}
                onCheckedChange={handleNotificationToggle}
              />
            </div>
            {settings.notificationsEnabled && (
              <Button
                variant="outline"
                data-testid="button-test-notification"
                onClick={async () => {
                  const title = "Precipitation Alert";
                  const body = "Rain expected — 75% chance today. Expected between 14:00–18:00. Peak: 90% at 16:00. You might want an umbrella.";
                  const sent = await sendNotification(title, body);
                  if (!sent) {
                    toast({
                      title,
                      description: body,
                    });
                  }
                }}
              >
                Test notification
              </Button>
            )}
            {settings.notificationsEnabled === false &&
              "Notification" in window &&
              Notification.permission === "denied" && (
                <p className="text-xs text-muted-foreground">
                  Notifications are blocked. Please enable them in your browser settings.
                </p>
              )}
          </div>
        </div>
      </SheetContent>
    </Sheet>
  );
}
