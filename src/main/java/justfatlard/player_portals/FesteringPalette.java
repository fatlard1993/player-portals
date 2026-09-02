package justfatlard.player_portals;

import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What a festering player portal spreads, which is wherever it goes.
 *
 * <p>festering-portal turns the ground around a crying-obsidian portal into nether, which is right
 * for a portal that goes to the nether because that is the only place a portal went. A player
 * portal goes where you tied it, so what leaks through the frame should be the place on the other
 * side of it - another dimension, or just a very different bit of this one.
 *
 * <p><b>Only where the two ends disagree.</b> A portal from one plain to another has nothing to
 * spread: the ground at both ends is already the same ground, and corrupting it into itself is an
 * effect nobody would ever see. Sand into snow is the case worth having.
 *
 * <p>Derived from climate and biome tags rather than a list of biome names, so a modded desert is
 * a desert here on the day it is installed. The two that are too strange to derive are named.
 */
public final class FesteringPalette {
	/** Deferred to festering-portal's own answer, which is the one it was written to give. */
	private static final List<BlockState> DEFER = List.of();

	/** The nether is festering-portal's home ground; it does that better than this could. */
	private static final FesteringPalette NETHER = new FesteringPalette("nether", DEFER, false);

	private static final FesteringPalette END = new FesteringPalette("end", List.of(
		Blocks.END_STONE.defaultBlockState(), Blocks.END_STONE.defaultBlockState(),
		Blocks.END_STONE.defaultBlockState(), Blocks.END_STONE_BRICKS.defaultBlockState(),
		Blocks.PURPUR_BLOCK.defaultBlockState()), true);

	private static final FesteringPalette SANDY = new FesteringPalette("sandy", List.of(
		Blocks.SAND.defaultBlockState(), Blocks.SAND.defaultBlockState(),
		Blocks.SANDSTONE.defaultBlockState(), Blocks.SMOOTH_SANDSTONE.defaultBlockState()), true);

	private static final FesteringPalette FROZEN = new FesteringPalette("frozen", List.of(
		Blocks.SNOW_BLOCK.defaultBlockState(), Blocks.SNOW_BLOCK.defaultBlockState(),
		Blocks.PACKED_ICE.defaultBlockState(), Blocks.ICE.defaultBlockState()), true);

	// Dyed terracotta is one block with a colour on it now, so the plain sort and red sand carry
	// this on their own rather than reaching into a block state to tint it.
	private static final FesteringPalette BADLANDS = new FesteringPalette("badlands", List.of(
		Blocks.RED_SAND.defaultBlockState(), Blocks.RED_SAND.defaultBlockState(),
		Blocks.TERRACOTTA.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState()), true);

	private static final FesteringPalette LUSH = new FesteringPalette("lush", List.of(
		Blocks.MOSS_BLOCK.defaultBlockState(), Blocks.MOSS_BLOCK.defaultBlockState(),
		Blocks.MUD.defaultBlockState(), Blocks.ROOTED_DIRT.defaultBlockState()), false);

	private static final FesteringPalette CONIFER = new FesteringPalette("conifer", List.of(
		Blocks.PODZOL.defaultBlockState(), Blocks.PODZOL.defaultBlockState(),
		Blocks.COARSE_DIRT.defaultBlockState(), Blocks.MOSS_BLOCK.defaultBlockState()), false);

	private static final FesteringPalette SAVANNA = new FesteringPalette("savanna", List.of(
		Blocks.COARSE_DIRT.defaultBlockState(), Blocks.COARSE_DIRT.defaultBlockState(),
		Blocks.DIRT.defaultBlockState(), Blocks.ROOTED_DIRT.defaultBlockState()), true);

	private static final FesteringPalette DROWNED = new FesteringPalette("drowned", List.of(
		Blocks.SAND.defaultBlockState(), Blocks.GRAVEL.defaultBlockState(),
		Blocks.PRISMARINE.defaultBlockState()), true);

	private static final FesteringPalette SCULK = new FesteringPalette("sculk", List.of(
		Blocks.SCULK.defaultBlockState(), Blocks.SCULK.defaultBlockState(),
		Blocks.DEEPSLATE.defaultBlockState(), Blocks.SCULK_CATALYST.defaultBlockState()), true);

	private static final FesteringPalette FUNGAL = new FesteringPalette("fungal", List.of(
		Blocks.MYCELIUM.defaultBlockState(), Blocks.MYCELIUM.defaultBlockState(),
		Blocks.BROWN_MUSHROOM_BLOCK.defaultBlockState()), false);

	/** Ordinary ground. Two temperate places have nothing to spread to each other. */
	private static final FesteringPalette TEMPERATE = new FesteringPalette("temperate", DEFER, false);

	/** The two whose character is not in their climate or their tags. */
	private static final Map<String, FesteringPalette> BY_NAME = Map.of(
		"deep_dark", SCULK,
		"mushroom_fields", FUNGAL);

	private final String name;
	private final List<BlockState> ground;
	/** Whether what grows here stops growing: a desert and an ice sheet keep no undergrowth. */
	private final boolean barren;

	private FesteringPalette(String name, List<BlockState> ground, boolean barren) {
		this.name = name;
		this.ground = ground;
		this.barren = barren;
	}

	/**
	 * The palette to answer with at this corruption site, or null to leave it to festering-portal.
	 *
	 * <p>Null covers three different situations that all want the same silence: this is not one of
	 * ours, the far end is the nether, or the two ends are the same sort of place.
	 */
	public static FesteringPalette forCorruptionAt(ServerLevel level, BlockPos centre) {
		PortalAnchor here = nearbyAnchor(level, centre);
		if (here == null) return null;

		PortalAnchor there = PortalRegistry.get(level.getServer()).partnerOf(here);
		if (there == null) return null;

		FesteringPalette far = of(level.getServer(), there);
		if (far == null || far.ground.isEmpty()) return null;
		if (far.equals(of(level.getServer(), here))) return null;

		return far;
	}

	/** Whether these two ends are different enough to leak into each other at all. */
	public static boolean mismatched(net.minecraft.server.MinecraftServer server,
			PortalAnchor a, PortalAnchor b) {
		FesteringPalette first = of(server, a);
		FesteringPalette second = of(server, b);

		return first != null && second != null && !first.equals(second);
	}

	/** What sort of place this end is. */
	private static FesteringPalette of(net.minecraft.server.MinecraftServer server, PortalAnchor end) {
		if (end.dimension().equals(Level.NETHER)) return NETHER;
		if (end.dimension().equals(Level.END)) return END;

		ServerLevel level = server.getLevel(end.dimension());
		if (level == null) return null;

		return ofBiome(level, end.pos());
	}

	private static FesteringPalette ofBiome(ServerLevel level, BlockPos pos) {
		Holder<Biome> biome = level.getBiome(pos);

		Identifier id = biome.unwrapKey().map(key -> key.identifier()).orElse(null);
		if (id != null) {
			FesteringPalette named = BY_NAME.get(id.getPath());
			if (named != null) return named;
		}

		// Tags before climate: a badlands and a desert share a climate and look nothing alike.
		if (biome.is(BiomeTags.IS_BADLANDS)) return BADLANDS;
		if (biome.is(BiomeTags.IS_JUNGLE)) return LUSH;
		if (biome.is(BiomeTags.IS_SAVANNA)) return SAVANNA;
		if (biome.is(BiomeTags.IS_TAIGA)) return CONIFER;
		if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN)) return DROWNED;

		// Then what falls out of the sky, which sorts the rest and covers biomes nobody tagged.
		return switch (biome.value().getPrecipitationAt(pos, level.getSeaLevel())) {
			case NONE -> SANDY;
			case SNOW -> FROZEN;
			case RAIN -> TEMPERATE;
		};
	}

	/**
	 * A player portal near this corruption centre.
	 *
	 * <p>Searched rather than recorded, because festering-portal works out its own centre from the
	 * frame and a second copy of that arithmetic on this side is a second thing to keep in step.
	 */
	private static PortalAnchor nearbyAnchor(ServerLevel level, BlockPos centre) {
		for (PortalAnchor anchor : PortalRegistry.get(level.getServer()).everyColour().keySet()) {
			if (!anchor.dimension().equals(level.dimension())) continue;
			if (anchor.pos().distSqr(centre) <= 6 * 6) return anchor;
		}
		return null;
	}

	/** What this block becomes. Only asked for blocks festering-portal had already decided to change. */
	public BlockState transform(BlockState input, RandomSource random) {
		if (this.ground.isEmpty()) return null;

		if (this.barren && (!input.isSolidRender() || input.getBlock() == Blocks.WATER)) {
			return Blocks.AIR.defaultBlockState();
		}
		return this.ground.get(random.nextInt(this.ground.size()));
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof FesteringPalette palette && this.name.equals(palette.name);
	}

	@Override
	public int hashCode() {
		return this.name.hashCode();
	}
}
