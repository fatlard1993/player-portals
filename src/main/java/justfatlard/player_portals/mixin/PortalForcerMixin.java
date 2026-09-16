package justfatlard.player_portals.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import justfatlard.player_portals.PortalLinks;
import justfatlard.player_portals.PortalRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.portal.PortalForcer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Leaves our portals out of vanilla's search for an ordinary portal to come out of. See
 * {@link PortalLinks#struckPortalAt}.
 *
 * <p>Around the one {@code min} at the end of the search rather than in the filters before it:
 * those are lambdas, whose names are the compiler's to change, and this is the call that picks.
 * With none of ours in the dimension, nothing is asked and the search runs as it always did. With
 * only ours in range, nothing is found, and vanilla builds a new portal the way it does for any
 * trip with nowhere to arrive.
 */
@Mixin(PortalForcer.class)
public class PortalForcerMixin {
	@Shadow
	@Final
	private ServerLevel level;

	@WrapOperation(method = "findClosestPortalPosition", at = @At(value = "INVOKE",
		target = "Ljava/util/stream/Stream;min(Ljava/util/Comparator;)Ljava/util/Optional;"))
	private Optional<BlockPos> playerPortals$notThroughOurs(Stream<BlockPos> candidates,
			Comparator<? super BlockPos> nearest, Operation<Optional<BlockPos>> original) {
		PortalRegistry registry = PortalRegistry.get(level.getServer());
		if (!registry.anyStruckIn(level.dimension())) return original.call(candidates, nearest);

		Map<BlockPos, Boolean> seen = new HashMap<>();
		return original.call(candidates.filter(pos -> !PortalLinks.struckPortalAt(level, pos, registry, seen)), nearest);
	}
}
