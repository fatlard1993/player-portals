package justfatlard.player_portals.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import justfatlard.player_portals.PortalLinks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.portal.TeleportTransition;

/**
 * Answers the one question a portal asks, when somebody has answered it already.
 *
 * <p>Here rather than in a portal block of this mod's own, because a portal of our own would be a
 * different block: not lit by flint and steel, not repaired by vanilla, not understood by any mod
 * that has ever looked at a nether portal. What a struck frame is, is a nether portal that was
 * told where to go, and this is the sentence that tells it.
 */
@Mixin(NetherPortalBlock.class)
public class NetherPortalBlockMixin {

	@Inject(method = "getPortalDestination", at = @At("HEAD"), cancellable = true)
	private void playerPortals$followTheLink(ServerLevel level, Entity entity, BlockPos pos,
			CallbackInfoReturnable<TeleportTransition> cir) {
		TeleportTransition linked = PortalLinks.destinationFrom(level, entity, pos);

		if (linked != null) cir.setReturnValue(linked);
		// A struck portal with no partner, yet or any more, goes nowhere: never vanilla's nether.
		else if (PortalLinks.leadsNowhere(level, pos)) cir.setReturnValue(null);
	}
}
