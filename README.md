# Player Portals

A Fabric mod that adds one item: a striker that ties two obsidian frames to each other instead of to the map.

## What This Mod Does

A nether portal does not go somewhere. It goes to whatever the game finds nearest a divided-by-eight
coordinate, which is why a portal you built on purpose comes out somewhere you did not, and why
linking two of them reliably is a job with a spreadsheet in it.

A **Portal Striker**, flint and steel with lapis where the flint was, answers the question directly. Strike one frame, walk to another,
strike that. The two now lead to each other, and the striker is spent.

## Using One

1. Build two portal frames, wherever you want them.
2. Strike the first. It lights, near black, and the striker in your hand becomes a **Linked
   Striker** holding that end. Unnamed, it carries the portal's coordinates in its name; named
   on an anvil, it keeps the name, and hovering it says where it points either way.
3. Strike the second with the linked striker. It lights, the pair is tied, and the striker is gone.

Either end takes you to the other. **Any mix of dimensions**: overworld to nether, nether to end,
end to overworld, or both ends in one world - the two are tied to each other, and where they happen
to be is not part of the arrangement.

**You come out in front of the far portal, not inside it**, on whichever face you were looking
toward, turned to face away from it and still moving. Vanilla sets you down inside the frame, which
is why the swirl is at full strength while you look for the way out and why standing still for a
moment sends you straight back. Here the swirl starts to fade as you land and there is nothing to
walk out of. Only when both faces of the far portal are walled up do you arrive inside, vanilla's
way.

An already-lit portal can be struck too, and usually is: the far end is often one you built last
week. Nothing needs the frame to be dark, only to be a portal by the time the striker looks.

**A struck portal goes nowhere until it is tied.** It is a player portal from the first strike:
step into one that is still waiting for its other half and nothing happens. It does not fall back
to the nether, because somebody built it to go somewhere in particular and "nowhere yet" is closer
to that than "the nether".

## Names

Name a striker on an anvil before you use it and the pair it makes wears that name, hanging in the
top-middle of both portals. Leave it unnamed and there is no sign, which is most of them.

Both ends, again: the striker carried one name and what it named was the pair.

**Renaming needs no second striker.** Name a name tag on an anvil and right-click either frame with
it, the same gesture a dye uses. Both signs change together and the tag is spent. An unnamed name
tag does nothing here, exactly as it does nothing to a cow.

The sign is a vanilla text display, so it is there for **everybody**, Pandorical or not. A portal
you cannot read is a portal you have to walk into to identify, and that is exactly the trip the
name saves.

Mine a named portal out and its sign comes down with it, checked as the chunk loads rather than
left hanging over an empty frame.

## Colour

A struck portal waiting for its other half is **nearly black**: it does not go anywhere yet, and
looking dead is the honest way to say so.

Tying the pair lights both ends in **one colour, picked at random** from the sixteen dyes - so the
colour you were handed is always one you could have chosen, and always one the portal beside it can
be told apart from. **Right-click either frame with a dye** to choose a different one; both ends
change together, because the colour names the pair rather than the door.

Dye and name tags both want a **finished pair**. A struck end still waiting for its other half is
near black on purpose and takes its colour and name the moment it is tied, so anything chosen for
it beforehand would be overwritten by the strike that finished it.

Untying a pair takes the colour with it. A portal that leads nowhere has nothing to be the colour
of.

This needs Pandorical on the client. A vanilla nether portal's model carries no tint index, so
nothing can colour it as the game ships it; the mod syncs vanilla's own two portal models with one
added, pointed at a copy of the portal texture drained to grey, and paints through Pandorical's
per-position block tint. Grey, because a tint multiplies: over the purple original a white tint
left a purple portal and a yellow one made brown, so half the palette read as no dye at all. Over
grey the tint is the whole of the colour. Portals nobody struck get vanilla's purple back through
the tint's fallback. A client without Pandorical sees ordinary purple portals that work exactly
the same.

## Festering, Where Festering Portal Is Installed

Build a player portal out of **crying obsidian** and it leaks - but what leaks through is the place
on the other side of it, not the nether.

[Festering Portal](https://github.com/fatlard1993/festering-portal) turns the ground around a
crying-obsidian portal into nether, which is exactly right for a portal that goes to the nether,
because until now that is the only place a portal went.

A portal to the End corrupts the ground around it into End instead: pale, barren, purpur through
it. One to the nether does what it always did. And a portal that never leaves the overworld leaks
the **far end's own country**: tie a desert to an ice sheet and the sand creeps toward snow while
the snow creeps toward sand.

**Only where the two ends disagree.** A portal from one plain to another has nothing to spread -
the ground at both ends is already the same ground, and corrupting it into itself is an effect
nobody would ever see.

The far end's character is read from its biome tags and what falls out of its sky, not from a list
of biome names, so a modded desert is a desert here the day it is installed. Tags are asked first
and that ordering matters: badlands, savanna, jungle and taiga all report the same weather as
somewhere they look nothing like.

Two things had to be added for that, and they are different problems:

- **A player portal never registered as a festering one at all.** Festering Portal watches fire
  being placed inside a frame, which is how every portal in the game is lit except these - a
  striker builds the portal blocks itself and lights nothing. So a portal of solid crying obsidian
  sat there doing nothing while an ordinary one beside it corrupted half a forest.
- **What a block turns into is decided without knowing whose corruption is asking.** That is fine
  when every portal spreads the same thing and useless the moment they do not, so the portal being
  processed is noted as its spread begins and read back inside the transformation.

Optional throughout. Without Festering Portal installed none of it loads, and crying obsidian in a
frame is just an expensive frame.

## Hubs

Two portals tied to each other is the common case and stays the default. For everything else -
a room with six doors in it, a mine with a way out on every level - **sneak and strike an existing
portal** to wire yourself to it. Every frame you strike after that becomes a way *to* it.

Sneak-strike the same portal again to unwire, and go back to making pairs.

**One way.** A spoke leads to the hub; the hub carries on leading wherever it already led. That is
the whole difference between this and a pair, and it is what lets six portals share one
destination without six of them fighting over which way it faces.

Spokes take the hub's colour and name, because what a spoke is for is arriving at the hub.

The wiring is remembered against **you**, not the striker, so a hub with six ways in is one
sneak-strike and six strikes rather than six of each. It also cannot be otherwise: the obvious
home for it is a data component on the item, and components ride the registry sync, which every
mod in this suite avoids so that a client that has never heard of it can still play here.

## Details Worth Knowing

- **The first end rides on the item.** A linked striker can be handed to somebody else, dropped in
  a chest, or carried through the portal it holds; whoever strikes the second frame with it ties
  the pair. It used to be held against the player instead, and a striker that had struck one end
  looked exactly like one that had not. If the end it holds is mined out before it is used, the
  next strike makes it a plain striker again, holding that portal as its first end.
- **Striking the same portal twice** says so and changes nothing, rather than spending the striker
  on a link from a place to itself.
- **Striking a portal that already leads somewhere** re-aims it. The alternative is a portal nobody
  can repurpose without finding and breaking its far end first, which for an end in another
  dimension is a trip.
- **Break either end and the link is gone.** Noticed on the way through rather than watched for: a
  portal whose partner has been mined out goes dark and leads nowhere until it is struck again.
- **Everything untouched is untouched.** An ordinary nether portal is still an ordinary nether
  portal, including every one that existed before this was installed. Only a portal a striker has
  touched is one of these.
- **An ordinary portal never comes out of one of these.** Vanilla looks for the nearest portal on
  the far side to arrive through, and a struck portal is a nether portal to it, so walking through
  an ordinary portal near one used to send you out of somebody's linked portal. Struck portals are
  left out of that search; with only those in range, vanilla builds a new portal as it would
  anywhere.
- **Ordinary portals go back the way they came.** A world of player portals is a world of portals
  near each other, so the mod turns on Pandorical's paired nether portals by default: each
  ordinary portal remembers the one its first traveller came out of, both ways round, and a new
  portal never comes out of one already paired with another: it gets a partner of its own, built
  on the far side if none is free. Struck portals are kept out of the pairing. An op can turn it
  off on Pandorical's own settings page ("Nether portals go back the way they came").
- **It is a real nether portal.** Not a block of this mod's own: lit by fire, repaired by vanilla,
  understood by every mod that has ever looked at one. A struck frame is a nether portal that was
  told where to go.

## Crafting

An iron ingot and a lapis lazuli, shapeless: flint and steel's recipe with lapis in place of the
flint.

## Pandorical

Pandorical is required on the server; the mod will not load without it. Player Portals registers
its item models through Pandorical's content sync.

**The Pandorical mod must be installed client-side** to see the striker rendered with its texture.
Without it the mod still works, but a connecting client sees an untextured item.

## Development

Installing, the map of the source and the art pipeline are in [DEVELOPMENT.md](DEVELOPMENT.md).

## License

MIT, see [LICENSE](LICENSE).
