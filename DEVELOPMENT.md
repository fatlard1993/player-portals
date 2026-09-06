# Player Portals - Development Guide

For what the mod is and how it plays, see [README.md](README.md).

## Installation

Install server-side alongside its declared dependencies (see `fabric.mod.json`); connecting clients
need only Pandorical. Version targets live in `gradle.properties` (Minecraft, loader, Fabric API)
and `fabric.mod.json` (Java).

## Key Files

| File | Responsibility |
|------|---------------|
| `Main.java` | Entry point; the item and its creative tab |
| `PortalStrikerItem.java` | Lighting a frame, holding the first end, tying the pair |
| `PortalAnchor.java` | Naming a portal in a way that survives being relit |
| `PortalRegistry.java` | Which portals lead to which, and who is half way through a pair |
| `PortalLinks.java` | Where a portal actually goes, when somebody has said |
| `PortalColors.java` | What colour a portal is, and getting it onto the glass |
| `PortalSigns.java` | The name a striker was given, hanging in the portal it made |
| `PortalMarking.java` | Changing a pair's colour or its name, after the fact |
| `mixin/NetherPortalBlockMixin.java` | Answering the one question a portal asks |

## Art

`generate_icon.py` and `generate_textures.py` cut the mod's icon and item sprite out of the vanilla
jar. Both are deterministic; re-run either after a Minecraft version bump.
