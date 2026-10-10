# UI Revamp Brief — Team Ops Board

Goal: a premium, human-designed Tritusa product. It must not look like a generic AI or admin template. Visual reference: docs/ui-reference/Dashref.jpg (take its layout quality, spacing and chart style, not its exact look).

## Brand colours
- Navy #00024D: sidebar background, page headings, primary text in light mode, dark-mode base
- Blue #304EE4: primary buttons, links, active navigation, focus rings, main chart series
- Amber #FFBF00: accent only — target progress fills, active-nav indicator, highlighted bar; never as text on white; text on amber is navy
- Generate a full 50–950 scale for each brand colour in the Tailwind config and CSS variables
- Neutrals: cool greys tinted slightly towards navy, not pure grey
- Status colours stay separate and semantic (brief §62): green = good, orange = warning, red = critical, grey = inactive; orange must be clearly different from brand amber
- Light theme is the default; dark theme uses deep navy backgrounds derived from #00024D, never pure black
- Remove the current indigo/purple theme completely

## Typography
- Headings: Plus Jakarta Sans; body, tables and numbers: Inter (load from Google Fonts)
- All numbers use tabular figures so columns line up
- Clear type scale: page title 28px, card title 16px semibold, labels 13px medium, KPI value 32px semibold

## Grid, spacing and cards
- 12-column grid, 24px gutters, 8px spacing scale used everywhere
- Cards: 14px corner radius, 1px subtle border, soft layered shadow in light mode (0 1px 2px rgba(0,2,77,.04), 0 6px 20px rgba(0,2,77,.06)); in dark mode use border and a slightly lighter surface instead of shadow
- Cards in the same row are equal height
- Sidebar: navy background, white text, blue active item with a small amber left indicator
- Top bar: white (light) / navy surface (dark), search centred, clean icon buttons

## KPI cards
- Label never truncated: wrap to two lines or use a shorter label with a tooltip
- Layout order: icon + label, big value, delta line ("↑ 30.7% vs Sep"), helper text — each on its own line, nothing cut off
- Positive delta green, negative red, always with an arrow icon

## Tables
- Numeric columns and their headers right-aligned; text columns left-aligned
- Change column on one line with an arrow ("↓ 5", "↑ 10", "— 0"), never wrapping
- Names such as "Paid campaign" never wrap; set sensible column widths
- Row height 52px, subtle dividers, sticky header, muted uppercase header text
- Select boxes and filters never truncate their text

## Charts
- Donut: rounded segment ends (cornerRadius), 2–3° padding between segments, thicker ring, total in the centre
- Donut legend: one clean aligned list — dot, label, count, % — no duplicated pills, nothing clipped
- Bar charts: rounded top corners, brand blue, current period highlighted in amber, light gridlines, muted axis labels
- Line charts: smooth curve with a soft gradient fill
- One shared chart palette built from the brand colours, used consistently

## Issues seen in the current screens (must be fixed)
- Truncated card labels and helper text ("Campaigns…", "Lead conve…", "Unique opens ÷ d…")
- Table headers not aligned with numbers; Change and "No change" wrapping; "Paid campaign" wrapping
- Donut legend pills clipped and duplicated with dots; square donut segments
- Generic purple theme, flat shadows, pure-black dark mode
- "Compare with the previous" select truncated

## Rules
- UI only: no business logic, API or database changes
- Keep all existing tests passing; update snapshot/text tests only where wording changes
- Accessibility: 4.5:1 text contrast, status always shown with a label or icon, not colour alone