package justfatlard.player_portals.integration;

import java.lang.reflect.Method;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Signing a player portal up for festering, where festering-portal is installed.
 *
 * <p>It does not sign itself up. That mod watches fire being placed inside a frame, which is how
 * every portal in the game is lit except these: a striker builds the portal blocks itself and
 * never lights anything, so a player portal built entirely of crying obsidian sits there doing
 * nothing while an ordinary one beside it corrupts half a forest.
 *
 * <p>Reached by name rather than imported, the way every optional integration in this suite is, so
 * player-portals neither compiles nor runs against it.
 */
public final class FesteringPortals {
	private FesteringPortals() {}

	private static final String PORTAL = "com.festeringportal.FesteringPortal";
	private static final String SCANNER = "com.festeringportal.util.PortalScanner";

	private static boolean looked = false;
	private static Method created;
	private static Method count;
	private static Method centre;

	public static boolean isPresent() {
		return resolve();
	}

	/** How many stones of the frame are crying. Zero means an ordinary portal. */
	public static int cryingObsidianIn(ServerLevel level, BlockPos portalBlock) {
		if (!resolve()) return 0;

		try {
			return (int) count.invoke(null, level, portalBlock);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return failed();
		}
	}

	/** The spot festering-portal will file this portal under, which is what its spread is keyed to. */
	public static BlockPos centreOf(ServerLevel level, BlockPos portalBlock) {
		if (!resolve()) return null;

		try {
			return (BlockPos) centre.invoke(null, level, portalBlock);
		} catch (ReflectiveOperationException | RuntimeException e) {
			failed();
			return null;
		}
	}

	/** Hand the portal over to festering-portal as if it had been lit the ordinary way. */
	public static void begin(ServerLevel level, BlockPos portalBlock, int cryingObsidian) {
		if (!resolve() || cryingObsidian <= 0) return;

		try {
			created.invoke(null, level, portalBlock, cryingObsidian);
		} catch (ReflectiveOperationException | RuntimeException e) {
			failed();
		}
	}

	private static int failed() {
		looked = true;
		created = null;
		return 0;
	}

	private static synchronized boolean resolve() {
		if (looked) return created != null;
		looked = true;

		if (!FabricLoader.getInstance().isModLoaded("festeringportal")) return false;

		try {
			Class<?> portal = Class.forName(PORTAL);
			Class<?> scanner = Class.forName(SCANNER);

			created = portal.getMethod("onFesteringPortalCreated",
				ServerLevel.class, BlockPos.class, int.class);
			count = scanner.getMethod("countCryingObsidianInFrame", ServerLevel.class, BlockPos.class);
			centre = scanner.getMethod("calculatePortalCenter", ServerLevel.class, BlockPos.class);
			return true;
		} catch (ReflectiveOperationException e) {
			justfatlard.player_portals.Main.LOGGER.warn(
				"[{}] festering-portal is installed but does not look the way this expects ({});"
					+ " crying obsidian in a player portal will not fester",
				justfatlard.player_portals.Main.MOD_ID, e.toString());
			created = null;
			return false;
		}
	}
}
