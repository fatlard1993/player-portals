package justfatlard.player_portals.gametest;

import justfatlard.pandorical.portal.ImmersivePortals;
import justfatlard.player_portals.PortalAnchor;
import justfatlard.player_portals.PortalRegistry;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/**
 * Two struck portals tied across a quarter turn: one facing north and south, the other east and
 * west. Walking through looking straight on comes out of the face Pandorical's immersive portals
 * show through the sheet, which is vanilla's quarter turn of the way you were looking - not
 * whichever face the last fraction of a degree happened to favour. And the near one shows its far
 * side, which it could not while it was only kept out of pairing.
 */
public final class CrossFacing implements FabricClientGameTest {

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			TestServerConnection connection = world.getConnection();
			connection.waitForChunksRender();
			server.runCommand("gamemode creative @a");

			BlockPos spawn = server.computeOnServer(s -> connection.getServerPlayer().blockPosition());
			BlockPos near = spawn.offset(0, 20, 6);
			BlockPos far = spawn.offset(30, 20, 6);
			server.runOnServer(s -> {
				ServerLevel level = s.overworld();
				build(level, near, Direction.Axis.X);
				build(level, far, Direction.Axis.Z);
				PortalRegistry.get(s).tie(PortalAnchor.of(level, near), PortalAnchor.of(level, far), 0xFF7A0FFF, null);
				ImmersivePortals.choose(s, true);
			});

			// Facing north comes out east of the far sheet, and facing south comes out west
			double east = cross(context, server, connection, near, 180.0F) - (far.getX() + 0.5);
			check(east > 0.5, "looking north through a north-south portal came out " + east + " east of an east-west one, not on its east face");
			double west = cross(context, server, connection, near, 0.0F) - (far.getX() + 0.5);
			check(west < -0.5, "looking south came out " + west + " east of the far portal, not on its west face");

			server.runOnServer(s -> connection.getServerPlayer().teleportTo(s.overworld(),
				near.getX() + 0.5, near.getY(), near.getZ() - 4.5, Set.of(), 0.0F, 0.0F, true));
			for (int waited = 0; context.computeOnClient(client -> farBlocksShowing()) == 0; waited += 5) {
				check(waited < 200, "the struck portal showed nothing past it");
				context.waitTicks(5);
			}
		}
	}

	/** Step into the near sheet facing {@code yaw} and wait to land; how far east the landing is. */
	private static double cross(ClientGameTestContext context, TestServerContext server, TestServerConnection connection,
								BlockPos near, float yaw) {
		context.waitTicks(30);
		Vec3 before = server.computeOnServer(s -> {
			ServerPlayer player = connection.getServerPlayer();
			player.setPortalCooldown(0);
			player.teleportTo(s.overworld(), near.getX() + 1.0, near.getY(), near.getZ() + 0.5, Set.of(), yaw, 0.0F, true);
			return player.position();
		});
		server.waitFor(s -> connection.getServerPlayer().position().distanceTo(before) > 10, 200);
		context.waitTicks(2);
		return server.computeOnServer(s -> connection.getServerPlayer().getX());
	}

	/** A 2 wide, 3 tall sheet with its lower corner at {@code corner}, framed, on a floor with room either side. */
	private static void build(ServerLevel level, BlockPos corner, Direction.Axis axis) {
		Direction along = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
		Direction across = along.getClockWise();
		for (int a = -1; a <= 2; a++) {
			for (int up = -1; up <= 3; up++) {
				BlockPos at = corner.relative(along, a).above(up);
				boolean frame = a == -1 || a == 2 || up == -1 || up == 3;
				level.setBlock(at, frame ? Blocks.OBSIDIAN.defaultBlockState()
					: Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, axis), 18);
			}
			for (int side = -3; side <= 3; side++) {
				if (side != 0) level.setBlock(corner.relative(along, a).relative(across, side).below(), Blocks.STONE.defaultBlockState(), 18);
			}
		}
	}

	/**
	 * How many far blocks Pandorical is drawing. Its client classes are in the game this test runs
	 * in but not on the classpath this mod compiles against, so it is asked by name.
	 */
	private static int farBlocksShowing() {
		try {
			return (int) Class.forName("justfatlard.pandorical.client.portal.PortalViews").getMethod("showing").invoke(null);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("Pandorical's portal views are not where this test looks for them", e);
		}
	}

	private static void check(boolean holds, String otherwise) {
		if (!holds) throw new AssertionError(otherwise);
	}
}
