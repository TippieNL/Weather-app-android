let swRegistration: ServiceWorkerRegistration | null = null;

let isReloading = false;

export async function registerServiceWorker(): Promise<void> {
  if (!("serviceWorker" in navigator)) return;

  // When a new service worker takes control (e.g. after a deploy), reload once
  // so any page being served by a stale/broken worker recovers automatically.
  navigator.serviceWorker.addEventListener("controllerchange", () => {
    if (isReloading) return;
    isReloading = true;
    window.location.reload();
  });

  try {
    const reg = await navigator.serviceWorker.register("/sw.js");
    swRegistration = reg;
    // Force an immediate update check so a fixed worker replaces any stale one.
    reg.update().catch(() => {});
    await navigator.serviceWorker.ready;
    swRegistration = (await navigator.serviceWorker.getRegistration()) ?? reg;
  } catch (e) {
    console.warn("Service Worker registration failed:", e);
  }
}

export async function sendNotification(
  title: string,
  body: string,
  icon?: string,
): Promise<boolean> {
  const opts = { body, icon: icon || "/favicon.png" };

  if (!("Notification" in window)) {
    return false;
  }

  if (Notification.permission === "default") {
    const perm = await Notification.requestPermission();
    if (perm !== "granted") return false;
  } else if (Notification.permission !== "granted") {
    return false;
  }

  try {
    const reg = swRegistration || (await navigator.serviceWorker?.getRegistration());
    if (reg) {
      await reg.showNotification(title, opts);
      return true;
    }
  } catch {}

  try {
    new Notification(title, opts);
    return true;
  } catch {}

  return false;
}
