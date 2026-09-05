import { createRoot } from "react-dom/client";
import App from "./App";
import "./index.css";
import { registerServiceWorker } from "@/lib/notifications";

registerServiceWorker();

// Lightweight performance guard rail: log initial page load time and flag it
// if it crosses 500ms.
window.addEventListener("load", () => {
  const nav = performance.getEntriesByType("navigation")[0] as
    | PerformanceNavigationTiming
    | undefined;
  const loadMs = Math.round(nav ? nav.loadEventEnd - nav.startTime : performance.now());
  if (loadMs > 500) {
    console.warn(`[perf] SLOW initial load: ${loadMs}ms`);
  } else {
    console.log(`[perf] initial load: ${loadMs}ms`);
  }
});

createRoot(document.getElementById("root")!).render(<App />);
