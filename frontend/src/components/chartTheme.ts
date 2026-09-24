/** Reads chart colors from CSS custom properties so charts follow the light/dark theme. */
export function chartColors() {
  const s = getComputedStyle(document.documentElement);
  const v = (name: string) => s.getPropertyValue(name).trim();
  return {
    bg: v('--surface'),
    text: v('--text-muted'),
    grid: v('--border'),
    up: v('--up'),
    down: v('--down'),
    upSoft: v('--up-soft'),
    downSoft: v('--down-soft'),
    muted: v('--text-muted'),
    lines: [v('--accent'), v('--series-2'), v('--series-3')],
  };
}
