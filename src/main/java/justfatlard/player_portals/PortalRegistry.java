package justfatlard.player_portals;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Which portals lead to which, and who is half way through making a pair.
 *
 * <p>Kept once against the overworld rather than per level, because a pair is allowed to span two
 * of them and a record filed in one world would be invisible from the other end of its own link.
 *
 * <p>Pairs are stored both ways round. A portal is asked what it leads to far more often than a
 * pair is made, so the second entry buys a map lookup at every step through a portal for the cost
 * of one more line in a file.
 */
public final class PortalRegistry extends SavedData {
	private static final String STORAGE_KEY = "player_portals";

	private record Pair(PortalAnchor from, PortalAnchor to) {
		static final Codec<Pair> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			PortalAnchor.CODEC.fieldOf("from").forGetter(Pair::from),
			PortalAnchor.CODEC.fieldOf("to").forGetter(Pair::to)
		).apply(instance, Pair::new));
	}

	private record Pending(UUID player, PortalAnchor anchor) {
		static final Codec<Pending> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			UUIDUtil.CODEC.fieldOf("player").forGetter(Pending::player),
			PortalAnchor.CODEC.fieldOf("anchor").forGetter(Pending::anchor)
		).apply(instance, Pending::new));
	}

	private record Painted(PortalAnchor anchor, int argb) {
		static final Codec<Painted> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			PortalAnchor.CODEC.fieldOf("anchor").forGetter(Painted::anchor),
			Codec.INT.fieldOf("argb").forGetter(Painted::argb)
		).apply(instance, Painted::new));
	}

	/** A portal's name, and the sign currently showing it. The sign is absent until one is put up. */
	private record Named(PortalAnchor anchor, String name, java.util.Optional<UUID> sign) {
		static final Codec<Named> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			PortalAnchor.CODEC.fieldOf("anchor").forGetter(Named::anchor),
			Codec.STRING.fieldOf("name").forGetter(Named::name),
			UUIDUtil.CODEC.optionalFieldOf("sign").forGetter(Named::sign)
		).apply(instance, Named::new));
	}

	private record Stored(List<Pair> pairs, List<Pending> pending, List<Painted> colours,
			List<Named> names, List<Pending> synced) {
		static final Codec<Stored> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Pair.CODEC.listOf().fieldOf("pairs").forGetter(Stored::pairs),
			Pending.CODEC.listOf().fieldOf("pending").forGetter(Stored::pending),
			// Optional with an empty default, so a world saved before portals had colours still
			// loads: a pair tied yesterday must not become unreadable for gaining a colour.
			Painted.CODEC.listOf().optionalFieldOf("colours", List.of()).forGetter(Stored::colours),
			Named.CODEC.listOf().optionalFieldOf("names", List.of()).forGetter(Stored::names),
			// Optional with an empty default, so a world saved before hubs existed still loads.
			Pending.CODEC.listOf().optionalFieldOf("synced", List.of()).forGetter(Stored::synced)
		).apply(instance, Stored::new));
	}

	public static final Codec<PortalRegistry> CODEC =
		Stored.CODEC.xmap(PortalRegistry::fromStored, PortalRegistry::toStored);

	private static final SavedDataType<PortalRegistry> TYPE = new SavedDataType<>(
		Identifier.parse(STORAGE_KEY), PortalRegistry::new, CODEC, DataFixTypes.LEVEL);

	private final Map<PortalAnchor, PortalAnchor> pairs = new HashMap<>();
	private final Map<UUID, PortalAnchor> pending = new HashMap<>();
	private final Map<UUID, PortalAnchor> synced = new HashMap<>();
	private final Map<PortalAnchor, Integer> colours = new HashMap<>();
	private final Map<PortalAnchor, String> names = new HashMap<>();
	private final Map<PortalAnchor, UUID> signs = new HashMap<>();

	public static PortalRegistry get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	/** Where this portal leads, or null if it is an ordinary one. */
	public PortalAnchor partnerOf(PortalAnchor portal) {
		return this.pairs.get(portal);
	}

	/** The end this player struck and has not yet matched, or null. */
	public PortalAnchor pendingFor(UUID player) {
		return this.pending.get(player);
	}

	public void hold(UUID player, PortalAnchor anchor) {
		this.pending.put(player, anchor);
		// Struck and going nowhere yet, and it should look like it.
		this.colours.put(anchor, PortalColors.UNPAIRED);
		this.setDirty();
	}

	public Map<PortalAnchor, Integer> everyColour() {
		return Map.copyOf(this.colours);
	}

	public Integer colourOf(PortalAnchor portal) {
		return this.colours.get(portal);
	}

	/** Both ends together: the colour names the pair, not the door. */
	public void repaint(PortalAnchor portal, int argb) {
		this.colours.put(portal, argb);

		PortalAnchor partner = this.pairs.get(portal);
		if (partner != null) this.colours.put(partner, argb);

		this.setDirty();
	}

	/**
	 * The portal this player is wiring things to, or null if they are making ordinary pairs.
	 *
	 * <p>Held against the player rather than written on the striker. A data component would be
	 * the obvious home for it and is the one thing that cannot go there: components ride the
	 * registry sync, and every mod in this suite is meant to work for a client that has never
	 * heard of it. It reads better this way regardless - the sync outlives the striker that set
	 * it, so a hub with six spokes is one shift-click and six strikes, not six of each.
	 */
	public PortalAnchor syncedFor(UUID player) {
		return this.synced.get(player);
	}

	public void syncTo(UUID player, PortalAnchor hub) {
		this.synced.put(player, hub);
		// A hub and a half-made pair are opposite intentions; taking one drops the other.
		this.pending.remove(player);
		this.setDirty();
	}

	public void unsync(UUID player) {
		if (this.synced.remove(player) != null) this.setDirty();
	}

	public void release(UUID player) {
		if (this.pending.remove(player) != null) this.setDirty();
	}

	/**
	 * Tie two ends together, dropping whatever either was tied to before.
	 *
	 * <p>Retying rather than refusing, because the alternative is a portal nobody can repurpose
	 * without finding and breaking its far end first, which for an end in another dimension is a
	 * trip. A striker aimed at a portal that already leads somewhere is a player saying they want
	 * it to lead here instead.
	 */
	/**
	 * One portal leads to another, and that one carries on leading wherever it did.
	 *
	 * <p>This is the half of {@link #tie} that makes a hub: several portals can point at one, and
	 * pointing at it does not turn it round to face any of them. The spoke takes the hub's colour
	 * and name, because what a spoke is for is arriving at the hub.
	 */
	public void point(PortalAnchor from, PortalAnchor to) {
		untie(from);
		this.pairs.put(from, to);

		Integer colour = this.colours.get(to);
		if (colour != null) this.colours.put(from, colour);

		String name = this.names.get(to);
		if (name != null) this.names.put(from, name);

		this.setDirty();
	}

	public void tie(PortalAnchor a, PortalAnchor b, int argb, String name) {
		untie(a);
		untie(b);
		this.pairs.put(a, b);
		this.pairs.put(b, a);
		this.colours.put(a, argb);
		this.colours.put(b, argb);

		// Both ends, because the striker carried one name and what it named was the pair.
		if (name != null && !name.isBlank()) {
			this.names.put(a, name);
			this.names.put(b, name);
		}
		this.setDirty();
	}

	public Map<PortalAnchor, String> everyName() {
		return Map.copyOf(this.names);
	}

	public String nameOf(PortalAnchor portal) {
		return this.names.get(portal);
	}

	/** Both ends together, the way the colour goes: a name belongs to the pair. */
	public void rename(PortalAnchor portal, String name) {
		this.names.put(portal, name);

		PortalAnchor partner = this.pairs.get(portal);
		if (partner != null) this.names.put(partner, name);

		this.setDirty();
	}

	public UUID signOf(PortalAnchor portal) {
		return this.signs.get(portal);
	}

	public void rememberSign(PortalAnchor portal, UUID sign) {
		if (sign == null) {
			this.signs.remove(portal);
		} else {
			this.signs.put(portal, sign);
		}
		this.setDirty();
	}

	/** Forget this end and whatever it led to, leaving both as ordinary portals. */
	public void untie(PortalAnchor portal) {
		PortalAnchor partner = this.pairs.remove(portal);
		if (partner == null) return;

		// Only if it was facing back. A hub leads somewhere of its own, and cutting one of the
		// six spokes pointing at it must not take the hub's own way out with it.
		if (!portal.equals(this.pairs.get(partner))) {
			this.colours.remove(portal);
			this.names.remove(portal);
			this.signs.remove(portal);
			this.setDirty();
			return;
		}

		this.pairs.remove(partner);
		// The colour goes with the link. A portal that leads nowhere has nothing to be the
		// colour of, and leaving it painted would promise a far end that is not there.
		this.colours.remove(portal);
		this.colours.remove(partner);
		this.names.remove(portal);
		this.names.remove(partner);
		// The sign entities themselves are the caller's to discard: this holds ids, not worlds.
		this.signs.remove(portal);
		this.signs.remove(partner);
		this.setDirty();
	}

	private static PortalRegistry fromStored(Stored stored) {
		PortalRegistry registry = new PortalRegistry();
		for (Pair pair : stored.pairs()) registry.pairs.put(pair.from(), pair.to());
		for (Pending held : stored.pending()) registry.pending.put(held.player(), held.anchor());
		for (Painted painted : stored.colours()) registry.colours.put(painted.anchor(), painted.argb());
		for (Pending held : stored.synced()) registry.synced.put(held.player(), held.anchor());
		for (Named named : stored.names()) {
			registry.names.put(named.anchor(), named.name());
			named.sign().ifPresent(sign -> registry.signs.put(named.anchor(), sign));
		}
		return registry;
	}

	private static Stored toStored(PortalRegistry registry) {
		// One line per direction, both written: rebuilding the reverse on load would work too,
		// but a file you can read straight through is worth more than the line it saves.
		List<Pair> pairs = registry.pairs.entrySet().stream()
			.map(entry -> new Pair(entry.getKey(), entry.getValue()))
			.toList();
		List<Pending> pending = registry.pending.entrySet().stream()
			.map(entry -> new Pending(entry.getKey(), entry.getValue()))
			.toList();
		List<Painted> colours = registry.colours.entrySet().stream()
			.map(entry -> new Painted(entry.getKey(), entry.getValue()))
			.toList();
		List<Named> names = registry.names.entrySet().stream()
			.map(entry -> new Named(entry.getKey(), entry.getValue(),
				java.util.Optional.ofNullable(registry.signs.get(entry.getKey()))))
			.toList();
		List<Pending> synced = registry.synced.entrySet().stream()
			.map(entry -> new Pending(entry.getKey(), entry.getValue()))
			.toList();
		return new Stored(pairs, pending, colours, names, synced);
	}
}
