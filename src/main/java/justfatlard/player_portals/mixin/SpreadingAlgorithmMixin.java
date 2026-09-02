package justfatlard.player_portals.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import justfatlard.player_portals.FesteringSpread;
import net.minecraft.server.level.ServerLevel;

/**
 * Says which portal is spreading, for the length of one spread.
 *
 * <p>festering-portal decides what a block turns into through a static method that is handed the
 * block and nothing else - no position, no portal, no way to ask whose corruption this is. That is
 * fine when every portal spreads the same thing and useless the moment they do not.
 *
 * <p>So the answer is set here instead, where the portal being processed is in hand, and read back
 * out inside the transformation. It lives for exactly one call and is cleared on the way out.
 *
 * <p>The portal and state arguments are {@code @Coerce Object} because their types belong to a mod
 * this one does not compile against. Without the annotation the descriptors do not match and the
 * injection is refused - quietly, if the config lets it be, which is why this config does not.
 */
@Mixin(targets = "com.festeringportal.corruption.SpreadingAlgorithm", remap = false)
public class SpreadingAlgorithmMixin {

	@Inject(method = "spreadFromPortal", at = @At("HEAD"), remap = false)
	private static void playerPortals$rememberWhichPortal(ServerLevel level,
			@Coerce Object portal, @Coerce Object state, long tick,
			CallbackInfoReturnable<Boolean> cir) {
		FesteringSpread.begin(level, portal);
	}

	@Inject(method = "spreadFromPortal", at = @At("RETURN"), remap = false)
	private static void playerPortals$forgetWhichPortal(ServerLevel level,
			@Coerce Object portal, @Coerce Object state, long tick,
			CallbackInfoReturnable<Boolean> cir) {
		FesteringSpread.end();
	}
}
