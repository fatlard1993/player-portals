package justfatlard.player_portals;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import java.util.Set;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.portal.PortalShape;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/** Where a portal actually goes, when somebody has said where it goes. */
public final class PortalLinks {
	private PortalLinks() {}

	/**
	 * Whether this is a struck portal with nowhere to go, which is a portal that must not go
	 * anywhere.
	 *
	 * <p>Asked after {@link #destinationFrom} has answered null. An ordinary portal answering
	 * null is left to vanilla; a struck one answering null is waiting for its other half, or
	 * has lost it, and the one thing it must not do is fall back to the nether. Somebody built
	 * it to go somewhere in particular, and "nowhere yet" is closer to that than "the nether".
	 */
	public static boolean leadsNowhere(ServerLevel level, BlockPos pos) {
		PortalAnchor here = PortalAnchor.of(level, pos);
		if (here == null) return false;

		return PortalRegistry.get(level.getServer()).isStruck(here);
	}

	/**
	 * The far end of this portal, or null when there is none to give.
	 *
	 * <p>Null covers an ordinary portal, a link whose far end has been mined out, a dimension
	 * that is not loaded. What null means is decided by {@link #leadsNowhere}: vanilla's way
	 * for a portal we never touched, no way at all for one we did.
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
		// instead: a link with nothing at the other end is untied here and now, and this end
		// goes dark rather than swallowing whoever steps in.
		if (!destination.getBlockState(there.pos()).is(Blocks.NETHER_PORTAL)) {
			// Signs first: untie forgets which entities were showing the name, and an id nobody
			// holds any more is an entity nobody can discard.
			PortalSigns.takeDown(level.getServer(), registry, here);
			registry.untie(here);
			PortalColors.broadcast(level.getServer(), here, PortalColors.UNPAIRED);
			return null;
		}

		TeleportTransition outside = infrontOf(destination, there, entity);
		if (outside != null) return outside;

		// Both faces of the far portal are walled up, so it is vanilla's arrival after all: inside
		// the frame, momentum and facing carried, and the way out is whichever way it always was.
		Vec3 arrival = PortalShape.findCollisionFreePosition(
			Vec3.atBottomCenterOf(there.pos()), destination, entity,
			entity.getDimensions(entity.getPose()));
		return new TeleportTransition(destination, arrival, Vec3.ZERO,
			entity.getYRot(), entity.getXRot(),
			Relative.union(Relative.DELTA, Relative.ROTATION),
			TeleportTransition.PLAY_PORTAL_SOUND);
	}

	/**
	 * Arriving in front of the far portal rather than inside it.
	 *
	 * <p>Vanilla puts you down inside the destination frame, and everything bad about arriving
	 * follows from that. The screen swirl keeps climbing for as long as you stand in any portal,
	 * so it is at full strength as you try to find the way out; the frame is a portal too, so the
	 * ten-tick cooldown lapses and a fresh crossing starts counting while you dither; and the
	 * momentum vanilla carries is aimed at whatever this frame happens to face. So the far end
	 * sets you down one block out from its sheet, on whichever face you were looking toward,
	 * turned to face away from it with your speed along that line. The swirl starts to fade the
	 * moment you land, and there is nothing to walk out of.
	 *
	 * @return the transition, or null when neither face has room to stand
	 */
	private static TeleportTransition infrontOf(ServerLevel destination, PortalAnchor there, Entity entity) {
		BlockState portal = destination.getBlockState(there.pos());
		if (!portal.hasProperty(NetherPortalBlock.AXIS)) return null;
		Direction.Axis axis = portal.getValue(NetherPortalBlock.AXIS);

		// The middle of the bottom row, so a wide portal is left from its centre rather than its corner.
		Set<BlockPos> sheet = PortalAnchor.sheetOf(destination, there.pos());
		if (sheet.isEmpty()) return null;
		int floor = sheet.stream().mapToInt(BlockPos::getY).min().orElse(there.pos().getY());
		double x = 0, z = 0;
		int count = 0;
		for (BlockPos block : sheet) {
			if (block.getY() != floor) continue;
			x += block.getX() + 0.5;
			z += block.getZ() + 0.5;
			count++;
		}
		Vec3 foot = new Vec3(x / count, floor, z / count);

		Direction[] faces = axis == Direction.Axis.X
			? new Direction[] {Direction.SOUTH, Direction.NORTH}
			: new Direction[] {Direction.EAST, Direction.WEST};
		Vec3 look = entity.getLookAngle();
		if (look.x * faces[1].getStepX() + look.z * faces[1].getStepZ()
				> look.x * faces[0].getStepX() + look.z * faces[0].getStepZ()) {
			faces = new Direction[] {faces[1], faces[0]};
		}

		EntityDimensions size = entity.getDimensions(entity.getPose());
		for (Direction face : faces) {
			Vec3 spot = foot.add(face.getStepX(), 0, face.getStepZ());
			if (!destination.noCollision(entity, size.makeBoundingBox(spot))) continue;
			if (destination.getBlockState(BlockPos.containing(spot)).is(Blocks.NETHER_PORTAL)) continue;

			double speed = entity.getDeltaMovement().horizontalDistance();
			Vec3 onward = new Vec3(face.getStepX() * speed, 0, face.getStepZ() * speed);
			return new TeleportTransition(destination, spot, onward,
				face.toYRot(), entity.getXRot(), Set.of(),
				TeleportTransition.PLAY_PORTAL_SOUND);
		}
		return null;
	}
}
