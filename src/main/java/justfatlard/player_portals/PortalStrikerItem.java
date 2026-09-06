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
 * The striker: lights a frame and becomes a {@link LinkedStrikerItem} holding it; that one,
 * struck on a second frame, ties the two together and is spent.
 *
 * <p>The first end rides on the item. It used to be held against the player instead, and a
 * striker that had struck one end looked exactly like one that had not, which is the one thing
 * a half-used striker has to say. Vanilla's custom data component carries the end, so nothing
 * new crosses the wire.
 *
 * <p>An already-lit portal can be struck too, and that is the common case for the second end:
 * you built the far one last week. Nothing here needs the frame to be dark, only to be a portal
 * by the time we look. Either way, a portal a striker has touched is a player portal from then
 * on: it leads where it is told, and until it is told, nowhere.
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

		ItemStack striking = context.getItemInHand();
		PortalAnchor held = LinkedStrikerItem.endOf(striking);

		if (held == null) {
			// The first end. The portal is ours from here and dark until it leads somewhere,
			// and the striker in the hand becomes the one that is carrying it.
			registry.strike(anchor);
			PortalColors.broadcast(serverLevel.getServer(), anchor, PortalColors.UNPAIRED);
			becomeLinked(player, context.getHand(), striking, LinkedStrikerItem.holding(anchor, striking));
			player.sendSystemMessage(Component.translatable("player-portals-justfatlard.striker.first_end"));
			return InteractionResult.SUCCESS;
		}

		if (held.equals(anchor)) {
			// Struck the same portal twice. The striker keeps its end, because the mistake is
			// obvious and losing the first end over it would mean walking back.
			player.sendSystemMessage(Component.translatable("player-portals-justfatlard.striker.same_portal"));
			return InteractionResult.SUCCESS;
		}

		ServerLevel heldLevel = serverLevel.getServer().getLevel(held.dimension());
		if (heldLevel == null
				|| !heldLevel.getBlockState(held.pos()).is(net.minecraft.world.level.block.Blocks.NETHER_PORTAL)) {
			// The end it was carrying has been mined out since. Back to a plain striker, holding
			// this portal as its first end instead: the walk here was not for nothing.
			registry.strike(anchor);
			PortalColors.broadcast(serverLevel.getServer(), anchor, PortalColors.UNPAIRED);
			becomeLinked(player, context.getHand(), striking,
				LinkedStrikerItem.holding(anchor, LinkedStrikerItem.unlinked(striking)));
			player.sendSystemMessage(Component.translatable("player-portals-justfatlard.striker.end_gone"));
			return InteractionResult.SUCCESS;
		}

		// A colour off the dye palette, so the one chance hands you gave you is always a colour
		// you could have chosen yourself, and always one the portal beside it can be told from.
		int argb = PortalColors.random(serverLevel.getRandom());

		// Whatever the striker was called on the anvil. Naming one is optional and most will not
		// be; a pair with no name simply has no sign over it.
		Component named = striking.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
		String name = named == null ? null : named.getString();

		registry.tie(held, anchor, argb, name);

		PortalColors.broadcast(serverLevel.getServer(), held, argb);
		PortalColors.broadcast(serverLevel.getServer(), anchor, argb);
		PortalSigns.raise(serverLevel.getServer(), registry, held, name);
		PortalSigns.raise(serverLevel.getServer(), registry, anchor, name);

		// Crying obsidian in the frame means this end leaks, and what it leaks is the other end.
		// Only now, at the tie: before it there is no other end to leak.
		fester(serverLevel, held, anchor);
		fester(serverLevel, anchor, held);

		// Spent outright, creative or not. consume() lets a creative player keep it, which is
		// right for flint and wrong here: a linked striker holds one particular end, and one
		// that survives the tie is still pointing at a portal that is now tied to something.
		striking.shrink(1);

		serverLevel.playSound(null, inside, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 0.4F, 1.6F);
		player.sendSystemMessage(Component.translatable("player-portals-justfatlard.striker.linked"));

		return InteractionResult.SUCCESS;
	}

	/**
	 * One striker out of the hand becomes the linked one; the rest of the stack stays.
	 *
	 * <p>Swapping the whole hand for the linked striker looked right with one striker in it
	 * and threw the others away with more: a stack of five struck once was one linked striker.
	 */
	private static void becomeLinked(Player player, net.minecraft.world.InteractionHand hand,
			ItemStack striking, ItemStack linked) {
		if (striking.getCount() <= 1) {
			player.setItemInHand(hand, linked);
			return;
		}
		striking.shrink(1);
		if (!player.addItem(linked)) player.drop(linked, false, net.minecraft.util.Prediction.PREDICTED);
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
