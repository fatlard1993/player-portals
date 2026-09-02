package justfatlard.player_portals;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.portal.PortalShape;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/** Where a portal actually goes, when somebody has said where it goes. */
public final class PortalLinks {
	private PortalLinks() {}

	/**
	 * The far end of this portal, or null to leave it to vanilla.
	 *
	 * <p>Null is the important answer here and it is the answer for everything: an ordinary
	 * portal, a link whose far end has been mined out, a dimension that is not loaded. A portal
	 * this mod cannot place has to behave exactly like one it was never involved with, because
	 * the alternative is a nether portal that stops working.
	 */
	public static TeleportTransition destinationFrom(ServerLevel level, Entity entity, BlockPos pos) {
		PortalAnchor here = PortalAnchor.of(level, pos);
		if (here == null) return null;

		PortalRegistry registry = PortalRegistry.get(level.getServer());
		PortalAnchor there = registry.partnerOf(here);
		if (there == null) return null;

		ServerLevel destination = level.getServer().getLevel(there.dimension());
		if (destination == null) return null;

		// The far end can be mined, and nothing tells us when it is. Checked on the way through
		// instead: a link with nothing at the other end is untied here and now, so the portal
		// goes back to being an ordinary one rather than swallowing whoever steps in.
		if (!destination.getBlockState(there.pos()).is(Blocks.NETHER_PORTAL)) {
			// Signs first: untie forgets which entities were showing the name, and an id nobody
			// holds any more is an entity nobody can discard.
			PortalSigns.takeDown(level.getServer(), registry, here);
			registry.untie(here);
			return null;
		}

		Vec3 arrival = PortalShape.findCollisionFreePosition(
			Vec3.atBottomCenterOf(there.pos()), destination, entity,
			entity.getDimensions(entity.getPose()));

		// Momentum and facing carried over, the way vanilla carries them: walking through a
		// portal and coming out stopped and turned around is the tell that something homemade
		// happened, even when the destination is right.
		return new TeleportTransition(destination, arrival, Vec3.ZERO,
			entity.getYRot(), entity.getXRot(),
			Relative.union(Relative.DELTA, Relative.ROTATION),
			TeleportTransition.PLAY_PORTAL_SOUND);
	}
}
