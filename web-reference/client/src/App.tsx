import { lazy, Suspense } from "react";
import { Switch, Route } from "wouter";
import { queryClient } from "./lib/queryClient";
import { QueryClientProvider } from "@tanstack/react-query";
import { Toaster } from "@/components/ui/toaster";
import { TooltipProvider } from "@/components/ui/tooltip";
import { SettingsProvider } from "@/contexts/settings";
import { InstallPrompt } from "@/components/install-prompt";
import Home from "@/pages/home";
import NotFound from "@/pages/not-found";

// The precipitation map pulls in Leaflet (a large dependency). Lazy-load it so
// it's only fetched when the user actually opens the map, keeping the initial
// home-page bundle small.
const PrecipitationMap = lazy(() => import("@/pages/precipitation-map"));

function Router() {
  return (
    <Switch>
      <Route path="/" component={Home} />
      <Route path="/precipitation">
        <Suspense fallback={<div className="h-[100dvh] w-full bg-background" />}>
          <PrecipitationMap />
        </Suspense>
      </Route>
      <Route component={NotFound} />
    </Switch>
  );
}

function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <TooltipProvider>
        <SettingsProvider>
          <Toaster />
          <Router />
          <InstallPrompt />
        </SettingsProvider>
      </TooltipProvider>
    </QueryClientProvider>
  );
}

export default App;
