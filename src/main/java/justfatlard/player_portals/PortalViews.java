package justfatlard.player_portals;

import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.api.PortalApi;
import net.minecraft.core.GlobalPos;

/**
 * Struck portals are out of the pairing, so with immersive portals on they would show nothing past
 * them; this says where each leads instead.
 */
final class PortalViews {
	private PortalViews() {}

	static void register() {
		PandoricalApi.portals().lookThrough((viewer, level, pos) -> {
			PortalAnchor anchor = PortalAnchor.of(level, pos);
			if (anchor == null) return null;
			PortalAnchor there = PortalRegistry.get(level.getServer()).partnerOf(anchor);
			return there == null ? null : PortalApi.FarEnd.of(GlobalPos.of(there.dimension(), there.pos()));
		});
	}
}
