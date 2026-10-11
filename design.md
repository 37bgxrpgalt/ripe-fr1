# Ripe FR‑1 — Design Style Guide (locked)

Reference: **Braun ET66 calculator / T3 radio (Dieter Rams)** and **Teenage
Engineering OP‑1** — light warm-gray appliance bodies, graphite displays,
precise small type, one functional accent. This is the rulebook the UI must
not drift from. Every change to `web/index.html` is checked against this file.

---

## 1. Color

One gray temperature: **warm (hue ≈ 40°)**. Never mix cool grays in.
One accent: **Braun yellow**. Two signal colors, allowed **only** for sensor
state (lamp, gauge fill, status word) — never for decoration.

| Token | Hex | Role |
|---|---|---|
| `--bg` | `#e8e4db` | page enamel |
| `--panel` | `#f5f2eb` | raised panel |
| `--panel-hi` | `#fcfbf7` | inputs / inner surfaces |
| `--sunken` | `#ddd7ca` | inset tracks |
| `--ink` | `#23211d` | text + dark display panel |
| `--ink-2` | `#4b4740` | secondary text |
| `--muted` | `#837c6f` | micro labels, captions |
| `--hair` | `#cfc8ba` | input outline only — nowhere else |
| `--accent` | `#edb100` | THE accent: Save key, warning lamp, focus, chart endpoint |
| `--accent-deep` | `#8a6400` | accent when used as small text |
| `--on-accent` | `#1c1a16` | text on accent fills |
| `--fresh` | `#4c7a4d` | signal: FRESH lamp/word |
| `--spoiled` | `#b23a25` | signal: SPOILED lamp/word |
| status tints | `#e6ece1` `#f7ecd2` `#f1ded7` | verdict panel background shift |

**Banned:** purple, indigo, blue, gradients of any kind, neon glow,
rainbow accent pickers, cool grays.

## 2. Typography

- **Space Grotesk** — display: readout number, status word, section heads, numerals.
- **Urbanist** — UI/body copy.
- **Alternative pairing — "Editorial"** (opt-in, *Settings → Appearance*):
  **Merriweather** display + **PT Sans** UI. Same palette and scale; only the two
  font tokens switch (`html[data-font=editorial]`). Web fonts load from Google;
  offline they fall back to the system stack.
- **Banned as primary:** Inter, Roboto, Poppins, system sans-first stacks.
- Scale: readout `clamp(64px,14vw,104px)` / status 26px / section head 12px
  uppercase `0.09em` tracked / body 14.5px / micro 11px.
- Numerals: `font-variant-numeric: tabular-nums` everywhere numbers change.

## 3. Layout

- 6-column bento, explicit asymmetric placement. **Never** three equal cards
  in a row, never a centered hero with stacked blocks.
- Surface variation carries hierarchy: dark display block, tinted verdict
  block, plain panels, and two **borderless** blocks (trend, device) that sit
  on page whitespace.
- Borders: none on blocks. `--hair` only on inputs. No shadows on cards.
- Shape: panels 14px, display 16px, controls 10px, lamps full round.
- Spacing scale: 4 · 8 · 12 · 16 · 20 · 28 · 40.

## 4. Graphics

- One **custom SVG illustration** (the sealed test chamber), line style,
  ink strokes + a single accent LED. No icon libraries, no sparkles,
  no emoji (zero is the target).

## 5. Copy

- Every claim carries a real number, unit, name, or time: threshold counts,
  ms cadence, IP addresses, firmware version, port 115200.
- No filler (“seamless”, “powerful”, “stay up to date”), no vague promises.
- No redundant micro-labels — if context is obvious, the label is deleted
  (“newest first”, “overall status”, “manage your calibration” are gone).
- One primary job per screen: read the sensor. Secondary items (Bluetooth,
  demo) live one level down, not in the header.

## 6. Status language

Dynamic copy is built from the actual reading, e.g.
“1,702 counts — 102 past the 1,600 warning line.” Never a static sentence
that could be true for any value.

---

## 7. Dark mode (added 2026-10, same rulebook)

Dark is **not** a second design — it is the same warm appliance at night.
Rules that must not break: one gray temperature (hue ≈ 40°, never cool blue
grays), one accent (`#edb100`), signal colors only for sensor state. The
graphite readout **stays dark in both themes** (surface `#23211d` light /
`#0e0d0b` dark) — it is the instrument, not a themed card.

Selected with `data-theme` on `<html>`; the inline `<head>` script applies the
stored choice before first paint. Options: Light · Dark · System.

| Token | Light | Dark | Role |
|---|---|---|---|
| `--bg` | `#e8e4db` | `#1b1815` | page enamel |
| `--panel` | `#f5f2eb` | `#24211c` | raised panel |
| `--panel-hi` | `#fcfbf7` | `#2e2b24` | inputs / inner surfaces |
| `--sunken` | `#ddd7ca` | `#3a362f` | inset tracks, segmented control |
| `--ink` | `#23211d` | `#f2eee5` | text |
| `--ink-2` | `#4b4740` | `#c9c2b3` | secondary text |
| `--muted` | `#837c6f` | `#918a7d` | micro labels, captions |
| `--hair` | `#cfc8ba` | `#464036` | input outline only |
| `--display-bg` | `#23211d` | `#0e0d0b` | graphite readout surface |
| `--tint-fresh` | `#e6ece1` | `#232a20` | verdict background |
| `--tint-warn` | `#f7ecd2` | `#302916` | verdict background |
| `--tint-bad` | `#f1ded7` | `#33221d` | verdict background |
| `--fresh` | `#4c7a4d` | `#79a878` | signal |
| `--spoiled` | `#b23a25` | `#d86a4f` | signal |
| `--accent` | `#edb100` | `#edb100` | unchanged |
| `--accent-deep` | `#8a6400` | `#f0c04a` | accent as small text (lifted for contrast) |

The sparkline reads the live theme and redraws (grid / line / area / endpoint
halo all switch). Still banned: gradients, neon glow, cool grays.
