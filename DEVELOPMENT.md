# Player Portals - Development Guide

For what the mod is and how it plays, see [README.md](README.md).

## Installation

Install server-side alongside its declared dependencies (see `fabric.mod.json`); connecting clients
need only Pandorical. Version targets live in `gradle.properties` (Minecraft, loader, Fabric API)
and `fabric.mod.json` (Java).

## Key Files

| File | Responsibility |
|------|---------------|
| `Main.java` | Entry point; the two items, their creative tab, and the Pandorical wiring (content sync, portal tint, paired nether portals) |
| `PortalStrikerItem.java` | Lighting a frame, tying the pair, wiring hubs and their spokes |
| `LinkedStrikerItem.java` | A striker carrying its first end, held in its own custom data |
| `PortalAnchor.java` | Naming a portal in a way that survives being relit |
| `PortalRegistry.java` | Which portals lead to which, every portal a striker has touched, and who is wired to a hub |
| `PortalLinks.java` | Where a portal actually goes, when somebody has said |
| `PortalColors.java` | What colour a portal is, and getting it onto the glass |
| `PortalSigns.java` | The name a striker was given, hanging in the portal it made |
| `PortalMarking.java` | Changing a pair's colour or its name, after the fact |
| `FesteringPalette.java`, `FesteringSpread.java` | What a crying-obsidian player portal leaks, read off its far end |
| `integration/FesteringPortals.java` | Signing a struck portal up with Festering Portal, by reflection |
| `mixin/NetherPortalBlockMixin.java` | Answering the one question a portal asks |
| `mixin/PortalForcerMixin.java` | Leaving struck portals out of vanilla's search for somewhere to arrive |
| `mixin/SpreadingAlgorithmMixin.java`, `mixin/BlockTransformationsMixin.java` | Festering Portal's spread answering with the far end's palette (the festering mixin config) |

## Art

`generate_icon.py` and `generate_textures.py` cut the mod's icon, both striker sprites and the
grey portal texture the tint paints over out of the vanilla jar. Both are deterministic; re-run either after a Minecraft version bump.
