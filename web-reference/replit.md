# Weather Quotes - Brutally Honest Weather App

## Overview

Weather Quotes is a full-stack web application that provides local weather forecasts paired with humorous, brutally honest commentary. Users grant location access, and the app fetches weather data and displays it with funny quotes matched to the current weather condition. The app has a minimalist black-and-white design with a bold typographic layout.

## User Preferences

Preferred communication style: Simple, everyday language.

## System Architecture

### Frontend
- **Framework**: React 18 with TypeScript
- **Routing**: Wouter (lightweight client-side router)
- **State/Data Fetching**: TanStack React Query for server state management
- **Styling**: Tailwind CSS with CSS variables for theming (light/dark mode support)
- **UI Components**: shadcn/ui component library (new-york style) built on Radix UI primitives
- **Build Tool**: Vite with React plugin
- **Path Aliases**: `@/` maps to `client/src/`, `@shared/` maps to `shared/`

The frontend lives in `client/src/` with pages in `client/src/pages/` and reusable UI components in `client/src/components/ui/`. The app has three routes: Home (weather display), Precipitation Map (/precipitation), and a 404 page.

### Pages
- **Home** (`client/src/pages/home.tsx`): Main weather display with funny quotes. Swipe/scroll up reveals a full-screen detail panel with cards: main weather card (location, temp, condition, stats grid with min/max, humidity, wind, UV, pressure), today's hourly forecast card with link to precipitation map, and 7-day forecast card
- **Precipitation Map** (`client/src/pages/precipitation-map.tsx`): Full-screen Leaflet map with RainViewer radar overlay, animated timeline with play/pause, precipitation legend. Uses `?lat=XX&lon=YY` query params for location.
- **Not Found** (`client/src/pages/not-found.tsx`): 404 page

### Settings
- **Settings Context** (`client/src/contexts/settings.tsx`): React context with localStorage persistence for all app preferences: temperature unit (C/F), time format (12h/24h), date format (DD/MM, MM/DD, YYYY-MM-DD), location mode (device GPS / manual city), weather API service selection, API key storage, and precipitation notifications toggle.
- **Settings Sheet** (`client/src/components/settings-sheet.tsx`): Slide-in panel (Sheet) accessible from gear icon on home page, containing all settings controls.
- Helper functions exported: `convertTemp`, `formatTemp`, `formatTime`, `formatDate`

### Backend
- **Framework**: Express 5 (TypeScript, running via tsx)
- **HTTP Server**: Node.js `http.createServer` wrapping Express
- **API Design**: REST API endpoints registered in `server/routes.ts`
- **Weather Logic**: Server-side weather condition mapping with hardcoded funny quotes per condition type (clear, cloudy, rainy, stormy, snowy, foggy, windy, hot, cold). Quotes contain `**word**` markers for frontend highlight rendering.
- **Weather API Services**: Supports Open-Meteo (free, default), OpenWeatherMap, and WeatherAPI via `?service=` query parameter and `X-Weather-Api-Key` header
- **Geocoding**: `/api/geocode?query=city` endpoint using Nominatim for manual location lookup
- **Development**: Vite dev server middleware integrated with Express for HMR (`server/vite.ts`)
- **Production**: Static file serving from `dist/public` (`server/static.ts`)

### Data Storage
- **ORM**: Drizzle ORM with PostgreSQL dialect
- **Schema**: Defined in `shared/schema.ts` — currently has a `users` table (id, username, password)
- **Storage Layer**: `server/storage.ts` provides an `IStorage` interface with a `MemStorage` in-memory implementation (users stored in a Map). The database schema exists but the app currently uses in-memory storage.
- **Migrations**: Drizzle Kit configured to output to `./migrations`, schema push via `npm run db:push`
- **Database URL**: Required via `DATABASE_URL` environment variable for Drizzle Kit operations

### Shared Code
- `shared/schema.ts` contains both database table definitions (Drizzle) and Zod validation schemas for weather data types. This is imported by both frontend and backend.

### Build Process
- **Development**: `npm run dev` runs the Express server with Vite middleware for hot reloading
- **Production Build**: Custom `script/build.ts` that runs Vite build for the client and esbuild for the server, outputting to `dist/`. Server bundles key dependencies to reduce cold start syscalls.
- **Production Start**: `npm start` runs the compiled `dist/index.cjs`

### PWA (Progressive Web App)
- **Manifest** (`client/public/manifest.json`): Web app manifest with name, icons, theme color, display: standalone
- **Icons**: `icon.svg` (any purpose) and `icon-maskable.svg` (maskable, safe zone padded) — SVG-based for any size
- **Install Prompt** (`client/src/components/install-prompt.tsx`): Handles `beforeinstallprompt` event, shows custom dark banner after 3s delay, dismiss permanently stored in localStorage, auto-hides on `appinstalled` event
- **Offline Page** (`client/public/offline.html`): Shown when navigation fails offline — dark-themed with retry button
- **Meta tags**: `theme-color`, `apple-mobile-web-app-capable`, `apple-touch-icon`, `manifest` link in `index.html`

### Notifications
- **Service Worker** (`client/public/sw.js`): Minimal Service Worker registered at app startup for mobile notification support. Handles `notificationclick` to focus/open the app window.
- **Notification Helper** (`client/src/lib/notifications.ts`): Centralized `sendNotification()` function that tries Service Worker `showNotification` first (required for mobile), falls back to `new Notification()` constructor, and returns `false` if neither works. Also handles permission requests.
- **Toast Fallback**: When native notifications aren't available (e.g. iOS Safari without PWA install, permissions denied), both the home page and settings test button show an in-app toast notification instead.

### Key Design Decisions
1. **Monorepo structure** with `client/`, `server/`, and `shared/` directories — keeps frontend, backend, and shared types in one place
2. **In-memory storage over database** for the current simple use case — the Drizzle/Postgres setup is scaffolded but not actively used for the main weather feature
3. **Server-side quote generation** — funny quotes are stored as arrays on the server and randomly selected per weather condition, no external AI API needed
4. **Geolocation-based** — the frontend requests browser geolocation permission and sends coordinates to the API, or uses manually entered city via geocoding
5. **Settings persistence** — all user preferences stored in localStorage via React context, no database needed
6. **Multi-provider weather** — supports Open-Meteo (free), OpenWeatherMap, and WeatherAPI with API key management
7. **Service Worker for notifications** — registered at startup to enable `showNotification()` on mobile browsers that don't support the `new Notification()` constructor

## External Dependencies

### Database
- **PostgreSQL** via `DATABASE_URL` environment variable (required for Drizzle Kit schema operations)
- **connect-pg-simple** for session storage (available but may not be actively used)

### Key NPM Packages
- **drizzle-orm** + **drizzle-kit**: Database ORM and migration tooling
- **zod** + **drizzle-zod**: Schema validation
- **@tanstack/react-query**: Async state management
- **wouter**: Client-side routing
- **shadcn/ui** components (Radix UI primitives + Tailwind CSS)
- **express**: HTTP server framework
- **vaul**: Drawer component
- **embla-carousel-react**: Carousel functionality
- **recharts**: Charting library
- **react-day-picker**: Calendar/date picker
- **date-fns**: Date utility library

### Replit-Specific
- **@replit/vite-plugin-runtime-error-modal**: Error overlay in development
- **@replit/vite-plugin-cartographer**: Replit integration (dev only)
- **@replit/vite-plugin-dev-banner**: Development banner (dev only)

### External APIs
- **Open-Meteo** (default, free, no API key): Weather data via `api.open-meteo.com`
- **OpenWeatherMap** (optional, requires API key): Weather via `api.openweathermap.org`
- **WeatherAPI** (optional, requires API key): Weather via `api.weatherapi.com`
- **Nominatim**: Geocoding (forward + reverse) via `nominatim.openstreetmap.org`
- **RainViewer**: Precipitation radar tiles for the map page