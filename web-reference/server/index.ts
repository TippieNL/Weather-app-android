import express, { type Request, Response, NextFunction } from "express";
import compression from "compression";
import helmet from "helmet";
import rateLimit from "express-rate-limit";
import { registerRoutes } from "./routes";
import { serveStatic } from "./static";
import { createServer } from "http";

const app = express();
const httpServer = createServer(app);

const isProd = process.env.NODE_ENV === "production";

// Trust the first proxy hop (Replit's load balancer) so req.ip reflects the
// real client address — required for correct per-client rate limiting.
app.set("trust proxy", 1);

// Security headers (clickjacking, MIME sniffing, referrer, HSTS, etc.).
// The Content-Security-Policy is only enforced in production: in development
// Vite's HMR and the Replit dev plugins inject inline scripts and use
// eval/websockets that a strict policy would block.
app.use(
  helmet({
    contentSecurityPolicy: isProd
      ? {
          directives: {
            defaultSrc: ["'self'"],
            scriptSrc: ["'self'"],
            // Tailwind/Leaflet inject inline styles; Google Fonts CSS is remote.
            styleSrc: ["'self'", "'unsafe-inline'", "https://fonts.googleapis.com"],
            fontSrc: ["'self'", "https://fonts.gstatic.com"],
            imgSrc: [
              "'self'",
              "data:",
              "blob:",
              "https://tilecache.rainviewer.com",
              "https://*.basemaps.cartocdn.com",
            ],
            connectSrc: ["'self'", "https://api.rainviewer.com"],
            workerSrc: ["'self'"],
            manifestSrc: ["'self'"],
            objectSrc: ["'none'"],
            baseUri: ["'self'"],
            frameAncestors: ["'self'"],
          },
        }
      : false,
    // Let cross-origin fonts and map tiles load.
    crossOriginEmbedderPolicy: false,
  }),
);

// Gzip/Brotli-style compression for all responses (JSON API payloads and static
// assets). Cuts transfer size significantly with negligible CPU cost.
app.use(compression());

declare module "http" {
  interface IncomingMessage {
    rawBody: unknown;
  }
}

app.use(
  express.json({
    verify: (req, _res, buf) => {
      req.rawBody = buf;
    },
  }),
);

app.use(express.urlencoded({ extended: false }));

// Basic abuse protection for the public API proxy endpoints — caps how many
// requests a single client can make per minute.
const apiLimiter = rateLimit({
  windowMs: 60 * 1000,
  limit: 120,
  standardHeaders: "draft-7",
  legacyHeaders: false,
  message: { message: "Too many requests, please slow down." },
});
app.use("/api", apiLimiter);

export function log(message: string, source = "express") {
  const formattedTime = new Date().toLocaleTimeString("en-US", {
    hour: "numeric",
    minute: "2-digit",
    second: "2-digit",
    hour12: true,
  });

  console.log(`${formattedTime} [${source}] ${message}`);
}

app.use((req, res, next) => {
  const start = Date.now();
  const path = req.path;
  let capturedJsonResponse: Record<string, any> | undefined = undefined;

  const originalResJson = res.json;
  res.json = function (bodyJson, ...args) {
    capturedJsonResponse = bodyJson;
    return originalResJson.apply(res, [bodyJson, ...args]);
  };

  res.on("finish", () => {
    const duration = Date.now() - start;
    if (path.startsWith("/api")) {
      let logLine = `${req.method} ${path} ${res.statusCode} in ${duration}ms`;
      if (capturedJsonResponse) {
        logLine += ` :: ${JSON.stringify(capturedJsonResponse)}`;
      }

      log(logLine);

      // Flag slow API operations so they're easy to spot in the logs.
      if (duration > 500) {
        log(`SLOW (${duration}ms): ${req.method} ${path}`, "perf");
      }
    }
  });

  next();
});

(async () => {
  await registerRoutes(httpServer, app);

  app.use((err: any, _req: Request, res: Response, next: NextFunction) => {
    const status = err.status || err.statusCode || 500;

    // Log the full error server-side for debugging...
    console.error("Internal Server Error:", err);

    if (res.headersSent) {
      return next(err);
    }

    // ...but never leak internal error details to the client on server faults.
    // Intentional 4xx messages (e.g. validation) are safe to pass through.
    const message =
      status < 500 ? err.message || "Request error" : "Internal Server Error";

    return res.status(status).json({ message });
  });

  // importantly only setup vite in development and after
  // setting up all the other routes so the catch-all route
  // doesn't interfere with the other routes
  if (process.env.NODE_ENV === "production") {
    serveStatic(app);
  } else {
    const { setupVite } = await import("./vite");
    await setupVite(httpServer, app);
  }

  // ALWAYS serve the app on the port specified in the environment variable PORT
  // Other ports are firewalled. Default to 5000 if not specified.
  // this serves both the API and the client.
  // It is the only port that is not firewalled.
  const port = parseInt(process.env.PORT || "5000", 10);
  httpServer.listen(
    {
      port,
      host: "0.0.0.0",
      reusePort: true,
    },
    () => {
      log(`serving on port ${port}`);
    },
  );
})();
