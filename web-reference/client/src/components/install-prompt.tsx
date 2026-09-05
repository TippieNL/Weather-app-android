import { useState, useEffect } from "react";
import { X, Download } from "lucide-react";
import { Button } from "@/components/ui/button";

const DISMISSED_KEY = "pwa-install-dismissed";

interface BeforeInstallPromptEvent extends Event {
  prompt(): Promise<void>;
  userChoice: Promise<{ outcome: "accepted" | "dismissed" }>;
}

export function InstallPrompt() {
  const [deferredPrompt, setDeferredPrompt] = useState<BeforeInstallPromptEvent | null>(null);
  const [visible, setVisible] = useState(false);
  const [installed, setInstalled] = useState(false);

  useEffect(() => {
    // Don't show if already dismissed permanently
    if (localStorage.getItem(DISMISSED_KEY) === "true") return;

    // Don't show if already running as installed PWA
    if (window.matchMedia("(display-mode: standalone)").matches) return;

    const handler = (e: Event) => {
      e.preventDefault();
      setDeferredPrompt(e as BeforeInstallPromptEvent);
      // Small delay so the prompt doesn't feel instant/jarring
      setTimeout(() => setVisible(true), 3000);
    };

    window.addEventListener("beforeinstallprompt", handler);

    window.addEventListener("appinstalled", () => {
      setInstalled(true);
      setVisible(false);
      setDeferredPrompt(null);
    });

    return () => window.removeEventListener("beforeinstallprompt", handler);
  }, []);

  async function handleInstall() {
    if (!deferredPrompt) return;
    await deferredPrompt.prompt();
    const { outcome } = await deferredPrompt.userChoice;
    if (outcome === "accepted") {
      setInstalled(true);
    }
    setVisible(false);
    setDeferredPrompt(null);
  }

  function handleDismiss() {
    setVisible(false);
    localStorage.setItem(DISMISSED_KEY, "true");
  }

  if (!visible || installed) return null;

  return (
    <div
      className="fixed bottom-4 left-4 right-4 z-[2000] flex items-center gap-3 px-4 py-3 rounded-xl bg-foreground text-background shadow-xl"
      style={{ animation: "slideUp 0.3s ease-out" }}
      role="banner"
      aria-label="Install app prompt"
      data-testid="install-prompt"
    >
      <style>{`
        @keyframes slideUp {
          from { transform: translateY(100%); opacity: 0; }
          to   { transform: translateY(0);    opacity: 1; }
        }
      `}</style>

      <div className="w-9 h-9 rounded-lg bg-background/10 flex items-center justify-center shrink-0">
        <Download className="w-4 h-4 text-background" />
      </div>

      <div className="flex-1 min-w-0">
        <p className="text-sm font-display font-semibold leading-tight">Add to home screen</p>
        <p className="text-xs opacity-60 leading-tight">Get the full app experience</p>
      </div>

      <Button
        size="sm"
        variant="ghost"
        className="shrink-0 text-background hover:bg-background/10 hover:text-background font-semibold text-xs px-3 h-8"
        onClick={handleInstall}
        data-testid="button-install"
      >
        Install
      </Button>

      <button
        className="shrink-0 p-1 rounded-md opacity-60 hover:opacity-100 transition-opacity"
        onClick={handleDismiss}
        aria-label="Dismiss install prompt"
        data-testid="button-dismiss-install"
      >
        <X className="w-4 h-4 text-background" />
      </button>
    </div>
  );
}
