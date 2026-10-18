import { siteConfig } from "@/lib/site-config";
import type { Deadline } from "@/lib/deadlines";

function escape(text: string): string {
  return text.replace(/\\/g, "\\\\").replace(/;/g, "\;").replace(/,/g, "\\,").replace(/\n/g, "\\n");
}

/** RFC 5545 calendar with one all-day event per deadline. Lines use CRLF as the spec requires. */
export function deadlinesToIcs(deadlines: Deadline[], now: Date = new Date()): string {
  const stamp = now.toISOString().replace(/[-:]/g, "").replace(/\.\d{3}/, "");
  const lines = [
    "BEGIN:VCALENDAR",
    "VERSION:2.0",
    `PRODID:-//${siteConfig.shortName}//AI regulation deadlines//EN`,
    "CALSCALE:GREGORIAN",
    "X-WR-CALNAME:AI regulation deadlines",
  ];
  for (const d of deadlines) {
    const day = d.date.replace(/-/g, "");
    const next = new Date(`${d.date}T00:00:00Z`);
    next.setUTCDate(next.getUTCDate() + 1);
    lines.push(
      "BEGIN:VEVENT",
      // Kept from before the Devernance rename: calendars match re-imports by UID, so a new UID duplicates every event.
      `UID:${d.id}@assurance-os`,
      `DTSTAMP:${stamp}`,
      `DTSTART;VALUE=DATE:${day}`,
      `DTEND;VALUE=DATE:${next.toISOString().slice(0, 10).replace(/-/g, "")}`,
      `SUMMARY:${escape(`${d.law}: ${d.label}`)}`,
      `DESCRIPTION:${escape(`Source: ${d.source.title} ${d.source.url}`)}`,
      `URL:${d.source.url}`,
      "END:VEVENT",
    );
  }
  lines.push("END:VCALENDAR");
  return lines.join("\r\n") + "\r\n";
}
