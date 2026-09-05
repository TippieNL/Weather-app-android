interface PokeballProps {
  size?: number;
  className?: string;
  "data-testid"?: string;
}

/**
 * Clean, modern Poké Ball drawn as an inline SVG so it scales crisply at any
 * size and inherits the app's minimalist line style. Used to replace every
 * weather icon while Pokémon mode is active.
 */
function PokeballSvg({ size = 100, className }: { size?: number; className?: string }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 100 100"
      className={className}
      role="img"
      aria-label="Poké Ball"
    >
      {/* bottom (white) half */}
      <path d="M2 50a48 48 0 0 0 96 0Z" fill="hsl(var(--card))" />
      {/* top (red) half */}
      <path d="M2 50a48 48 0 0 1 96 0Z" fill="#ee1515" />
      {/* outer ring */}
      <circle cx="50" cy="50" r="48" fill="none" stroke="currentColor" strokeWidth="4" />
      {/* center band */}
      <rect x="2" y="46" width="96" height="8" fill="currentColor" />
      {/* center button */}
      <circle cx="50" cy="50" r="16" fill="currentColor" />
      <circle cx="50" cy="50" r="11" fill="hsl(var(--card))" />
      <circle cx="50" cy="50" r="11" fill="none" stroke="currentColor" strokeWidth="2.5" />
      <circle cx="50" cy="50" r="5" fill="hsl(var(--card))" stroke="currentColor" strokeWidth="2.5" />
    </svg>
  );
}

const SPARKLES = [
  { top: "2%", left: "12%", delay: "0s", scale: 1 },
  { top: "10%", left: "86%", delay: "0.6s", scale: 0.7 },
  { top: "70%", left: "0%", delay: "1.1s", scale: 0.85 },
  { top: "82%", left: "78%", delay: "0.3s", scale: 0.6 },
  { top: "44%", left: "96%", delay: "0.9s", scale: 0.75 },
];

function Sparkle({ scale }: { scale: number }) {
  return (
    <svg width={16 * scale} height={16 * scale} viewBox="0 0 24 24" fill="currentColor">
      <path d="M12 0l2.5 9.5L24 12l-9.5 2.5L12 24l-2.5-9.5L0 12l9.5-2.5z" />
    </svg>
  );
}

/**
 * Large hero Poké Ball: gentle float + slow spin, with sparkles twinkling
 * around it. Fixed-size wrapper so swapping it in causes no layout shift.
 */
export function PokeballIcon({ size = 160, className, "data-testid": testId }: PokeballProps) {
  return (
    <div
      className={className}
      data-testid={testId}
      style={{ width: size, height: size, position: "relative" }}
    >
      <div
        className="pokeball-float"
        style={{ width: size, height: size, position: "relative" }}
      >
        <div className="pokeball-spin" style={{ width: size, height: size }}>
          <PokeballSvg size={size} />
        </div>
        {SPARKLES.map((s, i) => (
          <div
            key={i}
            className="pokeball-sparkle"
            style={{ position: "absolute", top: s.top, left: s.left, animationDelay: s.delay }}
          >
            <Sparkle scale={s.scale} />
          </div>
        ))}
      </div>
    </div>
  );
}

/** Small static Poké Ball for inline/detail placements. */
export function PokeballGlyph({ size = 16, className, "data-testid": testId }: PokeballProps) {
  return (
    <span data-testid={testId} style={{ display: "inline-flex" }}>
      <PokeballSvg size={size} className={className} />
    </span>
  );
}
