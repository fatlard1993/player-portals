package justfatlard.player_portals;

import justfatlard.pandorical.api.ItemRegistration;
import justfatlard.pandorical.api.PandoricalApi;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class Main implements ModInitializer {
	public static final String MOD_ID = "player-portals-justfatlard";

	public static final Identifier PORTAL_STRIKER_ID = Identifier.fromNamespaceAndPath(MOD_ID, "portal_striker");

	public static final ResourceKey<Item> PORTAL_STRIKER_KEY = ResourceKey.create(Registries.ITEM, PORTAL_STRIKER_ID);

	public static final PortalStrikerItem PORTAL_STRIKER = new PortalStrikerItem(
		// One use, and the second strike spends it. Stacking is what would make that read wrong:
		// a stack of eight would look like eight portals, and the first end is held per player,
		// so eight of them are one plan whatever the count says.
		new Item.Properties().stacksTo(1).setId(PORTAL_STRIKER_KEY)
	);

	public static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		if (PandoricalApi.isAvailable()) {
			PandoricalApi.content().registerItem(MOD_ID + ":portal_striker", new ItemRegistration()
				.maxStackSize(1)
				.model(MOD_ID + ":item/portal_striker"));
			PandoricalApi.content().registerModAssets(MOD_ID);

			// A nether portal's model carries no tint index, so nothing can colour it as vanilla
			// ships it. These are vanilla's own two models with one added, served above vanilla's
			// copy by the synced pack, and they are the reason the colour has anywhere to land.
			for (String model : new String[] {"nether_portal_ns", "nether_portal_ew"}) {
				syncModelOverride("minecraft/models/block/" + model + ".json");
			}
			PandoricalApi.blockTints().positional("minecraft:nether_portal");
		}

		Registry.register(BuiltInRegistries.ITEM, PORTAL_STRIKER_ID, PORTAL_STRIKER);

		ResourceKey<CreativeModeTab> tabKey = ResourceKey.create(
			Registries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath(MOD_ID, "portal_striker"));
		CreativeModeTab portalStrikerGroup = FabricCreativeModeTab.builder()
			.title(Component.literal("Player Portals"))
			.icon(() -> new ItemStack(PORTAL_STRIKER))
			.displayItems((context, entries) -> {
				entries.accept(new ItemStack(PORTAL_STRIKER));
			})
			.build();
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, tabKey, portalStrikerGroup);

		PortalMarking.register();

		// Nothing about a colour survives a reconnect, so every one is stated again on the way in,
		// and again per chunk as the far ones come into range.
		PandoricalApi.onPlayerReady(PortalColors::stateAll);
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents.CHUNK_LOAD.register(
			(level, chunk, newlyGenerated) -> {
				PortalColors.onChunkLoad(level, chunk);
				PortalSigns.upkeep(level, chunk);
			});

		System.out.println("[" + MOD_ID + "] Loaded (server-side with Pandorical)");
	}

	/** Ship one of our {@code minecraft}-namespace overrides, which registerModAssets does not scan for. */
	private static void syncModelOverride(String path) {
		try (java.io.InputStream in = Main.class.getClassLoader().getResourceAsStream("assets/" + path)) {
			if (in == null) {
				LOGGER.error("[{}] Missing bundled asset {}; portals will not take colour", MOD_ID, path);
				return;
			}
			PandoricalApi.content().registerAsset(path, in.readAllBytes());
		} catch (java.io.IOException e) {
			LOGGER.error("[{}] Could not read bundled asset {}: {}", MOD_ID, path, e.getMessage());
		}
	}
}
