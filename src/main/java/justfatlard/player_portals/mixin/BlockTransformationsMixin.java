package justfatlard.player_portals.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import justfatlard.player_portals.FesteringPalette;
import justfatlard.player_portals.FesteringSpread;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Answers with the far end's stone instead of the nether's.
 *
 * <p>Only while a player portal is the one spreading, and only when that portal leads somewhere
 * with a palette of its own. Everything else - every ordinary festering portal, and every player
 * portal that happens to lead to the nether - falls through to festering-portal's own answer,
 * which is the one it was written to give.
 */
@Mixin(targets = "com.festeringportal.corruption.BlockTransformations", remap = false)
public class BlockTransformationsMixin {

	@Inject(method = "getTransformation", at = @At("HEAD"), cancellable = true, remap = false)
	private static void playerPortals$useTheFarEnd(BlockState input, RandomSource random,
			CallbackInfoReturnable<BlockState> cir) {
		FesteringPalette palette = FesteringSpread.current();
		if (palette == null) return;

		BlockState instead = palette.transform(input, random);
		if (instead != null) cir.setReturnValue(instead);
	}
}
