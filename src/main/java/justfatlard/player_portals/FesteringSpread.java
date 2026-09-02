package justfatlard.player_portals;

import java.lang.reflect.Field;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Which palette the spread currently running belongs to.
 *
 * <p>A scrap of state held for the length of one portal's turn, because the method that needs the
 * answer is static and is handed nothing that could carry it. Set when a spread starts, cleared
 * when it ends.
 *
 * <p>Thread-confined by where it is used: corruption runs on the server thread, one portal at a
 * time, inside a loop that finishes with one before starting the next.
 */
public final class FesteringSpread {
	private FesteringSpread() {}

	private static FesteringPalette current;
	private static Field centreField;

	/** Called as a portal's spread begins, with festering-portal's own portal record. */
	public static void begin(ServerLevel level, Object portal) {
		current = null;

		BlockPos centre = centreOf(portal);
		if (centre == null) return;

		// Null already covers everything that should be left alone: not one of ours, a nether-bound
		// portal that wants festering-portal's own answer, and two ends too alike to leak.
		current = FesteringPalette.forCorruptionAt(level, centre);
	}

	public static void end() {
		current = null;
	}

	/** The palette to answer with, or null to leave it to festering-portal. */
	public static FesteringPalette current() {
		return current;
	}

	/** Its centre, read off the record by name because the record's type is not ours to import. */
	private static BlockPos centreOf(Object portal) {
		if (portal == null) return null;

		try {
			if (centreField == null) {
				centreField = portal.getClass().getField("center");
			}
			return (BlockPos) centreField.get(portal);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}
}
