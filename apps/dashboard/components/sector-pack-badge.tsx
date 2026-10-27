import { cn } from "@/lib/utils";
import { resolveSectorPackId, sectorPackLabel } from "@/lib/sector-packs";

interface SectorPackBadgeProps {
  sector?: string | null;
  className?: string;
}

/**
 * Shows the enabled sector pack badge when the system sector resolves to
 * insurance, hr, or finance. Honest product framing: 3 packs + SPI only.
 */
export function SectorPackBadge({ sector, className }: SectorPackBadgeProps) {
  const packId = resolveSectorPackId(sector);
  if (!packId) {
    if (sector) {
      return (
        <span
          className={cn(
            "inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-medium",
            "bg-muted text-muted-foreground border border-border",
            className
          )}
          title="No enabled sector pack for this sector"
        >
          {sector}
        </span>
      );
    }
    return null;
  }

  return (
    <span
      className={cn(
        "inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-semibold border",
        packId === "insurance" &&
          "bg-sky-50 text-sky-800 border-sky-200 dark:bg-sky-950/40 dark:text-sky-300 dark:border-sky-900",
        packId === "hr" &&
          "bg-violet-50 text-violet-800 border-violet-200 dark:bg-violet-950/40 dark:text-violet-300 dark:border-violet-900",
        packId === "finance" &&
          "bg-teal-50 text-teal-800 border-teal-200 dark:bg-teal-950/40 dark:text-teal-300 dark:border-teal-900",
        className
      )}
      title={`${sectorPackLabel(packId)} pack (SPI overlay — not a live vendor connector)`}
    >
      <span>Pack</span>
      {sectorPackLabel(packId)}
    </span>
  );
}
