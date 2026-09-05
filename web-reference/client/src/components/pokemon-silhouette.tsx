import type { SilhouetteId } from "@/lib/pokemon";

interface SilhouetteProps {
  id: SilhouetteId;
  size?: number;
  className?: string;
  "data-testid"?: string;
}

/**
 * Minimalist, original "mystery creature" silhouettes shown next to the quote
 * in Pokémon mode — a nod to the classic "Who's that Pokémon?" reveal without
 * copying any real character. Rendered as a single solid shape in currentColor.
 */
const PATHS: Record<SilhouetteId, JSX.Element> = {
  // round body with two pointy ears + a lightning tail
  spark: (
    <>
      <polygon points="22,30 30,4 40,28" />
      <polygon points="78,30 70,4 60,28" />
      <circle cx="50" cy="58" r="34" />
      <polygon points="84,46 98,40 88,54 100,60 80,70" />
    </>
  ),
  // round body with a sprouting leaf
  leaf: (
    <>
      <path d="M50 6c10 4 14 14 6 24-10-2-12-14-6-24z" />
      <circle cx="50" cy="60" r="32" />
    </>
  ),
  // round body with a flame tail
  flame: (
    <>
      <circle cx="46" cy="60" r="32" />
      <path d="M78 30c10 8 14 22 4 30-2-6-6-8-6-8 2 8-2 12-6 12 4-12-2-22 8-34z" />
    </>
  ),
  // round body with a water-drop crest
  splash: (
    <>
      <path d="M50 8c8 10 14 18 14 26a14 14 0 0 1-28 0c0-8 6-16 14-26z" />
      <circle cx="50" cy="64" r="28" />
    </>
  ),
};

export function PokemonSilhouette({ id, size = 40, className, "data-testid": testId }: SilhouetteProps) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 100 100"
      className={className}
      fill="currentColor"
      data-testid={testId}
      role="img"
      aria-label="Mystery Pokémon silhouette"
    >
      {PATHS[id]}
    </svg>
  );
}
