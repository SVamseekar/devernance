/** A click this soon after the hover that opened a menu is the same gesture, not a request to close it. */
export const HOVER_CLICK_GRACE_MS = 500;

/**
 * Next open menu after a click on `label`'s button. A mouse click always hovers first, so without the grace window
 * the hover opens the menu and the click immediately closes it again.
 */
export function menuAfterClick(
  open: string | null,
  label: string,
  hoverOpenedAt: number | null,
  now: number,
): string | null {
  if (open !== label) return label;
  if (hoverOpenedAt !== null && now - hoverOpenedAt < HOVER_CLICK_GRACE_MS) return label;
  return null;
}
