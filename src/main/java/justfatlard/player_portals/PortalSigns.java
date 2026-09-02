package justfatlard.player_portals;

import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.phys.Vec3;

/**
 * The name a striker was given, hanging in the portal it made.
 *
 * <p>A colour tells two portals apart; a name says which is which. Both ends wear it, because the
 * striker carried one name and what it named was the pair.
 *
 * <p>Drawn with a text display rather than through Pandorical, so it is there for everybody: a
 * portal you cannot read is a portal you have to walk into to identify, and that is the trip the
 * name exists to save.
 */
public final class PortalSigns {
	private PortalSigns() {}

	/** Far enough to read across a room, short enough not to clutter a skyline. */
	private static final float VIEW_RANGE = 0.6F;

	/**
	 * Put this portal's name up, or take it down when there is no name.
	 *
	 * @return the sign's id, or null when there is nothing to show
	 */
	public static UUID show(ServerLevel level, PortalAnchor portal, String name, UUID existing) {
		remove(level, existing);
		if (name == null || name.isBlank()) return null;

		Vec3 spot = topMiddle(level, portal);
		if (spot == null) return null;

		Display.TextDisplay sign = new Display.TextDisplay(EntityTypes.TEXT_DISPLAY, level);
		sign.setPos(spot.x, spot.y, spot.z);
		sign.setText(Component.literal(name));
		sign.setBillboardConstraints(Display.BillboardConstraints.CENTER);
		sign.setViewRange(VIEW_RANGE);

		level.addFreshEntity(sign);
		return sign.getUUID();
	}

	public static void remove(ServerLevel level, UUID sign) {
		if (sign == null) return;

		Entity existing = level.getEntity(sign);
		if (existing != null) existing.discard();
	}

	/**
	 * Keep the signs in one chunk honest as it loads.
	 *
	 * <p>Two things go wrong on their own and neither announces itself. A sign can go missing -
	 * discarded by a command, cleared by something else tidying entities - and a portal can be
	 * mined out from under its sign, which leaves a name hanging in the air over nothing. Chunk
	 * load is the one moment both are cheap to check, because the blocks are in memory anyway.
	 */
	public static void upkeep(ServerLevel level, net.minecraft.world.level.chunk.LevelChunk chunk) {
		PortalRegistry registry = PortalRegistry.get(level.getServer());

		for (var entry : registry.everyName().entrySet()) {
			PortalAnchor portal = entry.getKey();
			if (!portal.dimension().equals(level.dimension())) continue;
			if (net.minecraft.core.SectionPos.blockToSectionCoord(portal.pos().getX()) != chunk.getPos().x()) continue;
			if (net.minecraft.core.SectionPos.blockToSectionCoord(portal.pos().getZ()) != chunk.getPos().z()) continue;

			UUID sign = registry.signOf(portal);

			if (PortalAnchor.sheetOf(level, portal.pos()).isEmpty()) {
				// The portal is gone. Take the name down and let the link go with it, rather than
				// leaving a label over an empty frame promising a trip nobody can take.
				takeDown(level.getServer(), registry, portal);
				registry.untie(portal);
				continue;
			}

			if (sign == null || level.getEntity(sign) == null) {
				registry.rememberSign(portal, show(level, portal, entry.getValue(), sign));
			}
		}
	}

	/**
	 * Hang a name over one end, and remember which entity is doing it.
	 *
	 * <p>The end may be in a world nobody is standing in, which is fine: an unloaded level is
	 * still a level, and spawning into it loads exactly the chunk the portal is in.
	 */
	public static void raise(net.minecraft.server.MinecraftServer server, PortalRegistry registry,
			PortalAnchor portal, String name) {
		ServerLevel where = server.getLevel(portal.dimension());
		if (where == null) return;

		registry.rememberSign(portal, show(where, portal, name, registry.signOf(portal)));
	}

	/** Discard the signs on both ends of a pair, for a link that is about to stop existing. */
	public static void takeDown(net.minecraft.server.MinecraftServer server, PortalRegistry registry,
			PortalAnchor portal) {
		for (PortalAnchor end : new PortalAnchor[] {portal, registry.partnerOf(portal)}) {
			if (end == null) continue;

			ServerLevel where = server.getLevel(end.dimension());
			if (where != null) remove(where, registry.signOf(end));
		}
	}

	/**
	 * The middle of the portal's top row.
	 *
	 * <p>Read off the sheet rather than the anchor, because the anchor is the bottom corner: a
	 * name hung there would sit in the doorway at knee height.
	 */
	private static Vec3 topMiddle(ServerLevel level, PortalAnchor portal) {
		Set<BlockPos> sheet = PortalAnchor.sheetOf(level, portal.pos());
		if (sheet.isEmpty()) return null;

		int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
		int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
		int maxY = Integer.MIN_VALUE;

		for (BlockPos pos : sheet) {
			minX = Math.min(minX, pos.getX());
			maxX = Math.max(maxX, pos.getX());
			minZ = Math.min(minZ, pos.getZ());
			maxZ = Math.max(maxZ, pos.getZ());
			maxY = Math.max(maxY, pos.getY());
		}

		return new Vec3((minX + maxX + 1) / 2.0, maxY + 0.5, (minZ + maxZ + 1) / 2.0);
	}
}
