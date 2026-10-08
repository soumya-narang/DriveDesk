# DriveDesk: UI / UX Guide

**Direction: quiet and professional.** A neutral, dense operations UI. One typeface, near-white surfaces, thin borders, and colour used only for data and status. The identity comes from how the data is drawn (pace markers, the whole-drive flow chart, the expiry countdown), not from decoration.

## 1. Principles

1. **Subtle first.** No display fonts, no accent colour, no gimmicks. If a detail is not helping someone read or act, remove it.
2. **Colour means something.** The chrome is neutral. Colour appears only as a category (chart marks, dots) or a status (good / warning / critical).
3. **Dense but calm.** A 12-column grid, 16 px gaps, 16 px panel padding, 36 px controls. Panels share the same border, radius and header.
4. **Status is never colour alone.** Every status has an icon and a word.
5. **Numbers align.** Tabular figures everywhere, proportional figures on the large KPI values.

## 2. Typography

One family: **Inter** (400 / 500 / 600), loaded from Google Fonts with a system-sans fallback.

| Role | Size / weight |
|---|---|
| Page title | 22 px / 600, −0.02em |
| Panel title | 14 px / 600 |
| Body, tables | 13.5–14 px / 400 |
| Labels, notes, axis text | 11–12.5 px / 400–500, muted |
| KPI value | 26 px / 600 |

Report text alone uses the system monospace.

## 3. Colour

Tokens live at the top of `styles.css`. Light is the default; the OS preference is followed until the user picks a theme with the toggle (saved as `dd-theme`). Dark is a separate selection, not an inversion.

| Token | Light | Dark | Use |
|---|---|---|---|
| `--bg` / `--panel` | `#F6F7F8` / `#FFFFFF` | `#101215` / `#171A1E` | Page, panels |
| `--border` / `--soft` | `#E3E6EA` / `#EEF0F3` | `#272B31` / `#20242A` | Panel edges, row dividers, empty bars |
| `--text` / `--text-2` / `--muted` | `#1B1F24` / `#4A525D` / `#6B7380` | `#E6E8EB` / `#B4B9C1` / `#8A919B` | Text tiers |

The primary button, active tab and "today" marker all use the text colour. There is no accent colour.

### Categories (identity)

| Category | Light | Dark |
|---|---|---|
| Food | `#2F55C8` | `#6A88EE` |
| Clothing | `#D9582B` | `#E8683A` |
| Medicine | `#14907F` | `#22A392` |
| Books | `#8A4FC7` | `#A578E2` |

Validated with the dataviz `validate_palette.js` (lightness band, chroma floor, adjacent colour-blind separation ≥ 8, normal-vision separation ≥ 15, contrast ≥ 3:1). Order is the server's category order and must not change. A category keeps its colour on every screen.

### Status (reserved)

Critical (expired, urgent, blocked), warning (expiring soon, behind pace, urgent priority) and good (handed over, on pace, target met). Each has a text colour, a pale background tint and a mark colour.

### Sequential (heatmap)

One hue, five steps; the scale flips in dark mode.

## 4. Data visualisation

Rules for every chart: thin marks, 2 px gaps between touching fills, rounded data-ends, hairline solid grids, a legend whenever there are 2+ series, selective labels, a tooltip on hover **and** keyboard focus, and a table or text equivalent.

| Feature | Form |
|---|---|
| Headline numbers | A single KPI strip (one panel, four cells) with thin meters |
| Flow of goods | Two column panels (received, handed out) over one day axis for the whole drive, stacked by category. Separate scales because the volumes differ by an order of magnitude, so no dual axis. Today line, "days to go" band, peak label, tooltip, arrow-key reading, Chart / Table toggle |
| Stock by category | One bar per category in its own colour (Inventory page uses a single stacked strip) |
| Targets | Bar in the category colour with a **pace tick** marking how far through the drive we are; "met", "on pace" or "behind pace", plus the units/day needed to finish |
| Expiry | One row per lot, bar length = days left on a 0–7 day axis, urgent zone shaded, severity icon + value |
| Distribution by day | Heatmap by category and day, row totals, unit legend, table view |
| Activity | Time, small status tag, text |

No pies or donuts, no dual axes, no gradients.

## 5. Layout

- Sticky white top bar: wordmark, text tabs (active = underline), search (`/` to focus), theme toggle.
- A thin context row under it: drive name, a 30-segment day bar and "Day 19 of 30".
- Content: 1200 px max, 12-column grid. Overview rows: KPI strip, flow chart, targets beside stock and expiry, heatmap beside activity, recent distributions.
- Below 960 px the grid stacks, tabs scroll horizontally, KPIs go 2×2. Wide tables and the heatmap scroll inside their panel; the page never scrolls sideways.

### Drive page and the countdown

- The context row reads "Day 19 of 30 · 11 days left". On the last day it says "last day". After that it says "ended", fills the day bar and adds an amber "Start the next drive" link. The drive name links to the Drive page.
- **Drive** tab: left column shows this drive (status tag, dates, received, handed out, stock) above the Past drives table; the right column is the "close and start the next" form. Two options, each with a live plain-number hint: carry over usable stock, keep the targets.
- Closing is a two-step action: the first press shows a warning that names both drives, the second press does it. Editing any field cancels the confirmation.
- The "Today" marker on the flow chart is hidden once a drive has ended.

## 6. Components

- **Panel:** white, 1 px border, 8 px radius, no shadow. Header = title left, note or controls right.
- **Buttons:** 34 px, 6 px radius. One primary (solid text colour) per screen.
- **Inputs:** 36 px, 1 px border, soft focus ring.
- **Tags:** small pills with a tinted background; used for status and priority.
- **Filters:** underlined text tabs with counts.
- **Tooltip:** one shared dark element, value first, category keyed by a short colour stroke.

## 7. Accessibility

- Text contrast ≥ 4.5:1 in both themes; visible focus ring on every control.
- Status = icon + word. Charts have summaries; the flow chart and heatmap have table views; the flow chart is keyboard-operable (←/→, Home/End, Esc).
- `prefers-reduced-motion` turns off the bar-grow animation.

## 8. Files

| File | Role |
|---|---|
| `web/public/index.html` | Shell: top bar, context row, tooltip element |
| `web/public/styles.css` | Tokens (light / dark), layout, components, chart styles |
| `web/public/app.js` | Router, API calls, views, chart renderers (`buildFlow`, `flowSvg`, `catBars`, `paceBar`, `expiryChart`, `heatmap`) |

The UI only calls `/api`; every rule stays on the Java side.
