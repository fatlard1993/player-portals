package justfatlard.player_portals;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.PortalShape;

/**
 * The striker: lights a frame, remembers it, and on the second frame ties the two together and is
 * spent.
 *
 * <p>Which end you are on is not written on the item, it is held against the player. A striker
 * that carried its first end would be a striker you could hand to somebody else half used, or
 * lose in a hopper with a location inside it, and it would need a data component of its own on
 * the wire for a vanilla client to fail to understand. What is actually true is that a person
 * walked from one place to another holding a plan, so the plan is filed under the person.
 *
 * <p>An already-lit portal can be struck too, and that is the common case for the second end:
 * you built the far one last week. Nothing here needs the frame to be dark, only to be a portal
 * by the time we look.
 */
public class PortalStrikerItem extends Item {
	public PortalStrikerItem(Properties settings) {
		super(settings);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		Player player = context.getPlayer();
		if (player == null) return InteractionResult.PASS;
		if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.SUCCESS;

		// The space the frame encloses, reached from the obsidian you clicked: the same place
		// flint and steel would have put its fire.
		BlockPos inside = context.getClickedPos().relative(context.getClickedFace());

		// Sneaking picks a portal to wire things to rather than lighting one. Only an existing
		// portal: the gesture means "this one", and there is nothing to mean it about until the
		// frame is lit.
		if (player.isSecondaryUseActive()) {
			return sync(serverLevel, player, inside);
		}

		PortalAnchor anchor = strike(serverLevel, inside);
		if (anchor == null) {
			player.sendSystemMessage(Component.translatable("player-portals-justfatlard.striker.no_frame"));
			return InteractionResult.FAIL;
		}

		serverLevel.playSound(null, inside, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS,
			1.0F, serverLevel.getRandom().nextFloat() * 0.4F + 0.8F);

		PortalRegistry registry = PortalRegistry.get(serverLevel.getServer());

		// A synced hub answers first: while one is set, every frame struck is a way to it, and
		// nothing here is making pairs.
		PortalAnchor hub = registry.syncedFor(player.getUUID());
		if (hub != null) {
			return spoke(serverLevel, player, registry, anchor, hub, inside, context.getItemInHand());
		}

		PortalAnchor held = registry.pendingFor(player.getUUID());

		if (held == null) {
			registry.hold(player.getUUID(), anchor);
			PortalColors.broadcast(serverLevel.getServer(), anchor, PortalColors.UNPAIRED);
			player.sendSystemMessage(Component.translatable("player-portals-justfatlard.striker.first_end"));
			return InteractionResult.SUCCESS;
		}

		if (held.equals(anchor)) {
			// Struck the same portal twice. Left held rather than cleared, because the mistake
			// is obvious and losing the first end over it would mean walking back.
			player.sendSystemMessage(Component.translatable("player-portals-justfatlard.striker.same_portal"));
			return InteractionResult.SUCCESS;
		}

		// A colour off the dye palette, so the one chance hands you gave you is always a colour
		// you could have chosen yourself, and always one the portal beside it can be told from.
		int argb = PortalColors.random(serverLevel.getRandom());

		// Whatever the striker was called on the anvil. Naming one is optional and most will not
		// be; a pair with no name simply has no sign over it.
		ItemStack striking = context.getItemInHand();
		Component named = striking.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
		String name = named == null ? null : named.getString();

		registry.tie(held, anchor, argb, name);
		registry.release(player.getUUID());

		PortalColors.broadcast(serverLevel.getServer(), held, argb);
		PortalColors.broadcast(serverLevel.getServer(), anchor, argb);
		PortalSigns.raise(serverLevel.getServer(), registry, held, name);
		PortalSigns.raise(serverLevel.getServer(), registry, anchor, name);

		// Crying obsidian in the frame means this end leaks, and what it leaks is the other end.
		// Only now, at the tie: before it there is no other end to leak.
		fester(serverLevel, held, anchor);
		fester(serverLevel, anchor, held);

		striking.consume(1, player);

		serverLevel.playSound(null, inside, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 0.4F, 1.6F);
		player.sendSystemMessage(Component.translatable("player-portals-justfatlard.striker.linked"));

		return InteractionResult.SUCCESS;
	}

	/**
	 * Wire this striker's owner to the portal they are sneaking at.
	 *
	 * <p>Nothing is spent. Choosing where the spokes go is not itself a portal, and charging a
	 * striker for the choice would mean a hub costs one more than the ways into it.
	 */
	private static InteractionResult sync(ServerLevel level, Player player, BlockPos inside) {
		PortalAnchor hub = PortalAnchor.of(level, inside);
		if (hub == null) {
			player.sendSystemMessage(
				Component.translatable("player-portals-justfatlard.striker.nothing_to_sync"));
			return InteractionResult.FAIL;
		}

		PortalRegistry registry = PortalRegistry.get(level.getServer());

		// Sneaking at the portal you are already wired to unwires it, so there is a way back to
		// making ordinary pairs that does not involve remembering a second gesture.
		if (hub.equals(registry.syncedFor(player.getUUID()))) {
			registry.unsync(player.getUUID());
			player.sendSystemMessage(
				Component.translatable("player-portals-justfatlard.striker.unsynced"));
			return InteractionResult.SUCCESS;
		}

		registry.syncTo(player.getUUID(), hub);
		level.playSound(null, inside, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.7F, 1.4F);

		String name = registry.nameOf(hub);
		player.sendSystemMessage(name == null
			? Component.translatable("player-portals-justfatlard.striker.synced")
			: Component.translatable("player-portals-justfatlard.striker.synced_named", name));

		return InteractionResult.SUCCESS;
	}

	/** One more way in to a hub, pointing at it without turning it round. */
	private static InteractionResult spoke(ServerLevel level, Player player, PortalRegistry registry,
			PortalAnchor anchor, PortalAnchor hub, BlockPos inside, ItemStack striking) {
		if (anchor.equals(hub)) {
			player.sendSystemMessage(
				Component.translatable("player-portals-justfatlard.striker.same_portal"));
			return InteractionResult.SUCCESS;
		}

		registry.point(anchor, hub);

		// A spoke has a far end like any other portal, so crying obsidian in its frame leaks the
		// hub's country the same way a tied pair's does.
		fester(level, anchor, hub);

		Integer colour = registry.colourOf(anchor);
		if (colour != null) PortalColors.broadcast(level.getServer(), anchor, colour);
		PortalSigns.raise(level.getServer(), registry, anchor, registry.nameOf(anchor));

		striking.consume(1, player);

		level.playSound(null, inside, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 0.4F, 1.6F);
		player.sendSystemMessage(Component.translatable("player-portals-justfatlard.striker.spoke"));

		return InteractionResult.SUCCESS;
	}

	/**
	 * Hand this end to festering-portal, if it is installed and there is anything to leak.
	 *
	 * <p>Three things have to be true. The frame has to be crying, or nothing leaks at all. The
	 * end has to be in the overworld, which is the only place festering-portal spreads anything -
	 * a nether-side portal registered here would sit in its records forever and never tick. And
	 * the two ends have to be different sorts of place: a portal from one plain to another has
	 * nothing to spread, and corrupting ground into itself is an effect nobody would ever see.
	 */
	private static void fester(ServerLevel level, PortalAnchor end, PortalAnchor other) {
		if (!end.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) return;
		if (!FesteringPalette.mismatched(level.getServer(), end, other)) return;

		ServerLevel where = level.getServer().getLevel(end.dimension());
		if (where == null) return;

		int crying = justfatlard.player_portals.integration.FesteringPortals
			.cryingObsidianIn(where, end.pos());
		justfatlard.player_portals.integration.FesteringPortals.begin(where, end.pos(), crying);
	}

	/**
	 * The portal at this spot, lighting the frame first if it is dark.
	 *
	 * @return its anchor, or null if there is no frame here to be a portal
	 */
	private static PortalAnchor strike(ServerLevel level, BlockPos inside) {
		PortalAnchor lit = PortalAnchor.of(level, inside);
		if (lit != null) return lit;

		// X first and Z as the fallback, which is what findEmptyPortalShape does with the axis
		// it is handed: the same call vanilla's fire makes when it lands inside a frame.
		Optional<PortalShape> shape = PortalShape.findEmptyPortalShape(level, inside, Direction.Axis.X);
		if (shape.isEmpty()) return null;

		shape.get().createPortalBlocks(level);
		return PortalAnchor.of(level, inside);
	}
}
