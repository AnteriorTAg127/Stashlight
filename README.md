# Stashlight

**Stop searching for items like a maniac and find your forgotten loot instantly.**

**Stashlight** is a Fabric mod that indexes every container you open, letting you instantly search for items across your world — even inside **Shulker Boxes**, **Bundles**, nested inventories, and **across dimensions**.

Works as a **client-side only** mod for single-player and client-mode use, and optionally as a **dual-side** mod: when installed on a dedicated server, it can proactively scan and push container data to clients without requiring them to open each container first.

## Features

- **World-wide instant search**
  Locate items in any indexed container across all dimensions.

- **Nested container indexing**
  Caches nested inventories, enabling searches inside shulker boxes and bundles.

- **Enhanced Previews**
  Full compatibility with **ShulkerBoxTooltip** for visual item previews directly in the search menu.

- **Broad container support**
  Works with Chests, Barrels, Shulkers, Hoppers, Droppers, Dispensers, and most modded block entities with inventories\*.

- **Enchantments search & filtering**
  Switch to enchantment search mode to filter by enchantment type, combine multiple enchantments with AND/OR logic, and narrow results by enchantment level range. Sort results by enchantment count.

- **Multi-layer visual highlighting**
  Click a result to highlight the container with:
  - **Block outlines** — X-ray box around the container block
  - **GUI slot highlights** — highlights the specific slots inside an open container
  - **Nested box highlights** — highlights shulker boxes/bundles inside a container
  - **World marker beams** — beacon-style beams visible through walls

  Shift+click to highlight multiple results at once, or use the ✦ button to highlight all matching results.

- **Server-side scanning (optional)**
  When installed on a server, Stashlight can scan loaded containers around each player and push updates proactively — no need to open containers manually. Includes incremental sync (only changed/removed containers are sent) and automatic dimension-switch push.

- **Cross-dimension tracking**
  Tracks containers across Overworld, Nether, End, and modded dimensions\*.

- **Client auto-indexing**
  Optional client-side auto-scanner that indexes nearby containers in loaded chunks at a configurable interval — useful in single-player or when the server doesn't run Stashlight.

- **Configurable highlight settings**
  Toggle each highlight layer independently, customize colors, adjust highlight duration (1–60s), and enable/disable the pulsing effect.

- **Crafting (v1.5)**
  The search screen gains a "Craft" page listing unlocked recipes from the vanilla recipe book (craftable highlighted, missing materials grayed), with recipe search and a "craftable only" filter. Click a recipe to see the 3x3/2x2 preview and a live consumption table (need / in backpack / in reach / to take); enter a quantity and hit **Take & Craft** — missing materials are auto-taken from reachable chests (modded and vanilla servers alike), then placed and batch-crafted through the vanilla recipe-book mechanism, stopping exactly at the target. A reachable crafting table enables all 3x3 recipes; without one, the 2x2 inventory grid is used. The top-left "Nearby Stock" panel shows reachable chest items; clicking one selects the recipe that uses it as an ingredient.

- **Bilingual support (EN / 中文)**
  Full English and Simplified Chinese localization.

<br/>

<sub>\* _Modded containers and dimensions are untested but expected to work_</sub>

## Usage

Press **NUMPAD 5** to open the search menu (keybind can be changed in Minecraft's controls settings).

### Search Modes

- **Item mode** — Search by item name. Results show all containers holding the item, with slot-level location info.
- **Enchant mode** — Search by enchantment. Select one or more enchantments, set level ranges, and combine with AND/OR logic. Use the `@ench:` prefix in the search box for quick enchantment queries.

### Data Sources

When Stashlight is installed on the server, you can switch between:
- **Local** — Only containers you've personally opened
- **Server** — Only server-scanned containers
- **Merged** — Both combined

Use the ↻ refresh button to request a new server scan. **Shift+Click** the refresh button to force a full scan (bypasses incremental sync).

### Highlighting

Click any search result to highlight the container in the world. The ⚙ button opens the highlight settings screen where you can toggle each layer, set colors, and adjust duration.

## Server Configuration

When installed on a server, Stashlight generates `config/stashlight-server.json` with the following sections:

| Section | Key Settings |
|---------|-------------|
| `scan` | `maxRadius`, `radiusUnit` (blocks/chunks), `maxContainersPerRequest`, `containersPerTick`, `maxDepth`, `sameDimensionOnly` |
| `rateLimit` | `minRequestIntervalTicks`, `bucketCapacity`, `refillTicks` |
| `push` | `enabled` (join push + incremental sync), `radius` |
| `permission` | `requireOp`, `permissionNode`, `respectClaims` |
| `limits` | `maxResponseBytes` |

Use `/stashlight reload` (permission level 2) to hot-reload the config without restarting the server.

## Requirements

- Minecraft **1.21.10**
- [**Fabric API**](https://modrinth.com/mod/fabric-api)
- [**oωo (owo-lib)**](https://modrinth.com/mod/owo-lib)
- Java 21+

## License

This project is licensed under the [**GNU General Public License v3.0**](https://github.com/Strange-Quark-007/Stashlight?tab=GPL-3.0-1-ov-file#readme).
