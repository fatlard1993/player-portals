package justfatlard.player_portals;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/**
 * A striker that has struck its first end and is carrying it to the second.
 *
 * <p>Its own item rather than a striker with a note on it, so it can be told apart in the hand
 * and in a chest: the one thing a half-used striker has to say is that it is half used. The end
 * it holds rides in vanilla's own custom data component, which every client already understands,
 * so nothing new crosses the wire for a client that has never heard of this mod.
 *
 * <p>Handed to somebody else it still works, and that is the point of it being an item: the plan
 * is in the thing you are holding, and you can give the thing away.
 */
public class LinkedStrikerItem extends PortalStrikerItem {
	private static final String END = Main.MOD_ID + ":end";

	public LinkedStrikerItem(Properties settings) {
		super(settings);
	}

	/** A linked striker holding this end, carrying over whatever the plain one was named. */
	public static ItemStack holding(PortalAnchor end, ItemStack from) {
		ItemStack linked = new ItemStack(Main.LINKED_STRIKER);
		CustomData.update(DataComponents.CUSTOM_DATA, linked, tag -> {
			tag.putString(END + "/dimension", end.dimension().identifier().toString());
			tag.putInt(END + "/x", end.pos().getX());
			tag.putInt(END + "/y", end.pos().getY());
			tag.putInt(END + "/z", end.pos().getZ());
		});
		Component name = from.get(DataComponents.CUSTOM_NAME);
		if (name != null) {
			linked.set(DataComponents.CUSTOM_NAME, name);
		} else {
			// Where it points, in the name, so two of them in a chest can be told apart at a
			// glance. The item name and not a custom name: a custom name would be an anvil name,
			// and an anvil name is what the pair gets called when it is tied.
			linked.set(DataComponents.ITEM_NAME, Component.translatable(
				"item.player-portals-justfatlard.linked_striker.at",
				end.pos().getX(), end.pos().getY(), end.pos().getZ()));
		}
		return linked;
	}

	/** The end this striker is carrying, or null if it is carrying nothing readable. */
	public static PortalAnchor endOf(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) return null;
		var tag = data.copyTag();
		String dimension = tag.getString(END + "/dimension").orElse(null);
		if (dimension == null) return null;
		Identifier id = Identifier.tryParse(dimension);
		if (id == null) return null;
		return new PortalAnchor(
			ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, id),
			new BlockPos(tag.getInt(END + "/x").orElse(0), tag.getInt(END + "/y").orElse(0),
				tag.getInt(END + "/z").orElse(0)));
	}

	/** The plain striker this one was, name and all, for when the end it held is gone. */
	public static ItemStack unlinked(ItemStack linked) {
		ItemStack plain = new ItemStack(Main.PORTAL_STRIKER);
		Component name = linked.get(DataComponents.CUSTOM_NAME);
		if (name != null) plain.set(DataComponents.CUSTOM_NAME, name);
		return plain;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context,
			net.minecraft.world.item.component.TooltipDisplay display,
			java.util.function.Consumer<Component> lines, net.minecraft.world.item.TooltipFlag flag) {
		PortalAnchor end = endOf(stack);
		if (end == null) return;
		lines.accept(Component.translatable("player-portals-justfatlard.linked_striker.holding",
			end.pos().getX(), end.pos().getY(), end.pos().getZ(),
			end.dimension().identifier().getPath()));
	}
}
