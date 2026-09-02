package justfatlard.player_portals;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Changing a pair's colour or its name, after the fact.
 *
 * <p>Both are the same gesture on purpose: hold the thing, click the frame. A dye repaints, a
 * named name tag relabels, and neither needs a second striker - the striker's job was to decide
 * where the portal goes, and that is the one thing here that cannot be taken back.
 *
 * <p>Aimed at the frame rather than the sheet, because a nether portal cannot be right-clicked:
 * it has no collision and the ray goes straight through it. The obsidian is what your cursor can
 * find, and it is where you struck it in the first place.
 *
 * <p><b>Tied pairs only.</b> A struck end still waiting for its other half is deliberately near
 * black and gets its colour and name the moment it is tied, so anything chosen for it beforehand
 * would be overwritten a minute later by the thing that finished it.
 */
public final class PortalMarking {
	private PortalMarking() {}

	public static void register() {
		UseBlockCallback.EVENT.register(PortalMarking::onUseBlock);
	}

	private static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand,
			BlockHitResult hit) {
		if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.PASS;
		if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;

		ItemStack stack = player.getItemInHand(hand);
		Integer dyed = PortalColors.ofDye(stack.getItem());
		boolean tagging = stack.is(Items.NAME_TAG);
		if (dyed == null && !tagging) return InteractionResult.PASS;

		BlockPos inside = hit.getBlockPos().relative(hit.getDirection());
		PortalAnchor portal = PortalAnchor.of(serverLevel, inside);
		if (portal == null) return InteractionResult.PASS;

		PortalRegistry registry = PortalRegistry.get(serverLevel.getServer());
		// A pair, not an ordinary nether portal and not a half-made one. Both are somebody
		// else's business: one was never ours, and the other is not finished.
		if (registry.partnerOf(portal) == null) return InteractionResult.PASS;

		return dyed != null
			? dye(serverLevel, serverPlayer, registry, portal, inside, stack, dyed)
			: rename(serverLevel, serverPlayer, registry, portal, inside, stack);
	}

	private static InteractionResult dye(ServerLevel level, ServerPlayer player, PortalRegistry registry,
			PortalAnchor portal, BlockPos inside, ItemStack stack, int argb) {
		Integer current = registry.colourOf(portal);
		if (current != null && current == argb) return InteractionResult.PASS;

		registry.repaint(portal, argb);
		stack.consume(1, player);

		PortalColors.broadcast(level.getServer(), portal, argb);
		PortalAnchor partner = registry.partnerOf(portal);
		if (partner != null) PortalColors.broadcast(level.getServer(), partner, argb);

		level.playSound(null, inside, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
		player.sendSystemMessage(Component.translatable("player-portals-justfatlard.striker.dyed"));

		return InteractionResult.SUCCESS;
	}

	private static InteractionResult rename(ServerLevel level, ServerPlayer player, PortalRegistry registry,
			PortalAnchor portal, BlockPos inside, ItemStack stack) {
		// A blank name tag names nothing, exactly as it names no cow. Passed rather than refused,
		// so the tag is still a tag and can be put on the anvil it needed in the first place.
		Component named = stack.get(DataComponents.CUSTOM_NAME);
		if (named == null) return InteractionResult.PASS;

		String name = named.getString();
		if (name.isBlank() || name.equals(registry.nameOf(portal))) return InteractionResult.PASS;

		registry.rename(portal, name);
		stack.consume(1, player);

		PortalSigns.raise(level.getServer(), registry, portal, name);
		PortalAnchor partner = registry.partnerOf(portal);
		if (partner != null) PortalSigns.raise(level.getServer(), registry, partner, name);

		level.playSound(null, inside, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1.0F, 1.2F);
		player.sendSystemMessage(Component.translatable("player-portals-justfatlard.striker.renamed"));

		return InteractionResult.SUCCESS;
	}
}
