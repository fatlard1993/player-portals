package justfatlard.player_portals;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/**
 * One end of a portal, named in a way that survives everything a player will do to it.
 *
 * <p>Not the block that was struck: that block is one of several and the player may well break
 * and relight it. The anchor is the lowest, most north-westerly block of the portal's own sheet
 * of purple, found by walking the sheet, so every block in a portal answers to the same name and
 * still does after it has been put out and lit again.
 *
 * @param dimension which world it is in, because the two ends need not share one
 * @param pos       the corner block the sheet is named by
 */
public record PortalAnchor(ResourceKey<Level> dimension, BlockPos pos) {
	public static final Codec<PortalAnchor> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(PortalAnchor::dimension),
		BlockPos.CODEC.fieldOf("pos").forGetter(PortalAnchor::pos)
	).apply(instance, PortalAnchor::new));

	/** A portal is at most 23x23; anything past that is not a portal and not worth walking. */
	private static final int MAX_SHEET = 23 * 23;

	/**
	 * The anchor for the portal sheet containing this block, or null if there is no portal here.
	 *
	 * <p>Flood filled rather than measured, so it does not care what shape the frame is or which
	 * axis it lies on, and so a sheet found from any of its blocks reduces to the same corner.
	 */
	public static PortalAnchor of(ServerLevel level, BlockPos inside) {
		Set<BlockPos> sheet = sheetOf(level, inside);
		if (sheet.isEmpty()) return null;

		BlockPos corner = null;
		for (BlockPos pos : sheet) {
			if (corner == null || compare(pos, corner) < 0) corner = pos;
		}
		return new PortalAnchor(level.dimension(), corner);
	}

	/**
	 * Every block of the portal sheet containing this one, or empty if there is no portal here.
	 *
	 * <p>Public because colouring a portal means colouring all of it: a tint is per block, and a
	 * portal is several of them pretending to be one thing.
	 */
	public static Set<BlockPos> sheetOf(ServerLevel level, BlockPos inside) {
		if (!level.getBlockState(inside).is(Blocks.NETHER_PORTAL)) return Set.of();

		Set<BlockPos> seen = new HashSet<>();
		Deque<BlockPos> pending = new ArrayDeque<>();

		seen.add(inside.immutable());
		pending.add(inside.immutable());

		while (!pending.isEmpty() && seen.size() <= MAX_SHEET) {
			BlockPos at = pending.poll();

			for (net.minecraft.core.Direction face : net.minecraft.core.Direction.values()) {
				BlockPos next = at.relative(face).immutable();
				if (seen.contains(next)) continue;
				if (!level.getBlockState(next).is(Blocks.NETHER_PORTAL)) continue;

				seen.add(next);
				pending.add(next);
			}
		}
		return seen;
	}

	/** Lowest first, then north, then west: any total order will do, as long as it is always the same one. */
	private static int compare(BlockPos a, BlockPos b) {
		if (a.getY() != b.getY()) return Integer.compare(a.getY(), b.getY());
		if (a.getZ() != b.getZ()) return Integer.compare(a.getZ(), b.getZ());
		return Integer.compare(a.getX(), b.getX());
	}
}
