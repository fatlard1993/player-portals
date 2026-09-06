package justfatlard.player_portals;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import justfatlard.pandorical.api.PandoricalApi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;

/**
 * What colour a portal is, and getting that colour onto the glass.
 *
 * <p>A nether portal is purple everywhere in the world, which is fine while there is only one
 * question to ask about it. Once a portal goes to one particular other portal, the useful question
 * is which one, and the answer has to be visible from across a room without opening anything.
 *
 * <p>Struck and unpaired reads as near black: it is a portal that does not go anywhere yet, and
 * looking dead is the honest way to say so. Tying it lights it up.
 */
public final class PortalColors {
	private PortalColors() {}

	/** Struck, waiting for its other half. Nearly black, and plainly not one of the sixteen. */
	public static final int UNPAIRED = 0xFF23222A;

	/**
	 * The dye palette, so a colour that turned up at random is always one somebody could have
	 * chosen. A random colour off the whole 24-bit wheel would be a colour you can never match on
	 * the portal next to it.
	 */
	private static final List<DyeColor> PALETTE = List.of(DyeColor.values());

	private static Map<Item, DyeColor> dyes;

	public static int random(RandomSource random) {
		return opaque(PALETTE.get(random.nextInt(PALETTE.size())));
	}

	/** The colour this dye paints, or null if the item is not a dye. */
	public static Integer ofDye(Item item) {
		if (dyes == null) dyes = buildDyeTable();

		DyeColor colour = dyes.get(item);
		return colour == null ? null : opaque(colour);
	}

	private static int opaque(DyeColor colour) {
		return 0xFF000000 | colour.getTextureDiffuseColor();
	}

	/**
	 * Dye item to colour, built by name.
	 *
	 * <p>Read out of the item registry rather than off the item, because {@code DyeItem} exposes
	 * no accessor for the colour it carries on this version: the field is there and there is no
	 * way in. Names are the remaining handle, and they are the one thing about a dye that has
	 * never moved.
	 */
	private static Map<Item, DyeColor> buildDyeTable() {
		Map<Item, DyeColor> table = new HashMap<>();

		for (DyeColor colour : PALETTE) {
			Item item = BuiltInRegistries.ITEM.getValue(
				Identifier.withDefaultNamespace(colour.getSerializedName() + "_dye"));
			if (item != null) table.put(item, colour);
		}

		if (table.size() != PALETTE.size()) {
			Main.LOGGER.warn("[{}] Only {} of {} dyes resolved by name; the rest will not colour a portal",
				Main.MOD_ID, table.size(), PALETTE.size());
		}
		return table;
	}

	/**
	 * Tell one player the colours of every portal already loaded around them.
	 *
	 * <p>Loaded ones only. Reading a portal's sheet means reading blocks, and reading blocks in
	 * an unloaded chunk loads it: stating every portal in the world on join would drag a chunk
	 * off disk per portal, for portals nobody is anywhere near. The rest are stated by
	 * {@link #onChunkLoad} as they arrive, which is the same moment the player could first see
	 * one anyway.
	 */
	public static void stateAll(ServerPlayer player) {
		ServerLevel level = player.level();
		Map<BlockPos, Integer> paint = new HashMap<>();

		PortalRegistry.get(level.getServer()).everyColour().forEach((anchor, argb) -> {
			if (!anchor.dimension().equals(level.dimension())) return;
			if (!level.isLoaded(anchor.pos())) return;
			paint.putAll(sheet(level, anchor, argb));
		});

		if (!paint.isEmpty()) PandoricalApi.blockTints().paint(player, paint);
	}

	/** Chunks that arrived this tick, painted at the end of it. */
	private static final Map<ServerLevel, java.util.Set<net.minecraft.world.level.ChunkPos>> ARRIVED = new HashMap<>();

	/** How far a portal's sheet can reach from its anchor: the widest sheet the anchor walks. */
	private static final int SHEET_REACH = 23;

	/**
	 * A chunk arriving is the moment its portals become visible, so it is the moment to colour
	 * them. Noted here, painted at the end of the tick.
	 *
	 * <p>Not painted here. This runs inside the chunk's own load, and a portal that straddles a
	 * chunk border has half its sheet in the chunk next door: reading that half from in here asks
	 * the chunk system for a chunk while standing inside the chunk system, and the server waits
	 * on itself until the watchdog kills it. It did, with three people on.
	 */
	public static void onChunkLoad(ServerLevel level, net.minecraft.world.level.chunk.LevelChunk chunk) {
		ARRIVED.computeIfAbsent(level, l -> new java.util.HashSet<>()).add(chunk.getPos());
	}

	/**
	 * Paint the portals in and around every chunk that arrived this tick, and keep their signs,
	 * from outside chunk loading.
	 */
	public static void paintArrived(MinecraftServer server) {
		if (ARRIVED.isEmpty()) return;

		for (var arrived : ARRIVED.entrySet()) {
			ServerLevel level = arrived.getKey();
			for (net.minecraft.world.level.ChunkPos chunk : arrived.getValue()) {
				PortalSigns.upkeep(level, chunk);
			}
			if (level.players().isEmpty()) continue;

			Map<BlockPos, Integer> paint = new HashMap<>();
			PortalRegistry.get(server).everyColour().forEach((anchor, argb) -> {
				if (!anchor.dimension().equals(level.dimension())) return;
				// Any anchor whose sheet could cross into an arrived chunk, not only the ones
				// anchored inside it: the far half of a straddling portal is painted when its
				// chunk arrives, whichever half the anchor sits in.
				for (net.minecraft.world.level.ChunkPos chunk : arrived.getValue()) {
					if (anchor.pos().getX() < chunk.getMinBlockX() - SHEET_REACH
							|| anchor.pos().getX() > chunk.getMaxBlockX() + SHEET_REACH
							|| anchor.pos().getZ() < chunk.getMinBlockZ() - SHEET_REACH
							|| anchor.pos().getZ() > chunk.getMaxBlockZ() + SHEET_REACH) continue;
					paint.putAll(sheet(level, anchor, argb));
					break;
				}
			});

			if (paint.isEmpty()) continue;
			for (ServerPlayer player : level.players()) {
				PandoricalApi.blockTints().paint(player, paint);
			}
		}
		ARRIVED.clear();
	}

	/** Tell everybody in that portal's world about a colour that just changed. */
	public static void broadcast(MinecraftServer server, PortalAnchor anchor, int argb) {
		ServerLevel level = server.getLevel(anchor.dimension());
		if (level == null) return;

		Map<BlockPos, Integer> paint = sheet(level, anchor, argb);
		if (paint.isEmpty()) return;

		for (ServerPlayer player : level.players()) {
			PandoricalApi.blockTints().paint(player, paint);
		}
	}

	/**
	 * Every block of the portal's sheet, all the same colour.
	 *
	 * <p>The whole sheet and not just the anchor, because the tint is per block and a portal is
	 * several: colouring the corner alone would give you one purple portal with one coloured
	 * pixel in it.
	 */
	private static Map<BlockPos, Integer> sheet(ServerLevel level, PortalAnchor anchor, int argb) {
		Map<BlockPos, Integer> paint = new HashMap<>();

		for (BlockPos pos : PortalAnchor.sheetOf(level, anchor.pos())) {
			paint.put(pos, argb);
		}
		return paint;
	}
}
