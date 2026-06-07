# CLAUDE.md — D&D Initiative Mod

Context for Claude Code. This file records the design decisions made while planning
this project so work can continue from here. Read this before suggesting changes.

## What this is

A mod for running **Dungeons & Dragons-style sessions inside Minecraft**, DM-guided.
The framing that drives every decision: this is **not** a feature added to normal
survival play. It's a dedicated game mode where **Minecraft is the engine** (rendering,
physics, multiplayer, animation) and the **DM plus this mod are the rules engine**. A
lot of the work is therefore *suppressing* Minecraft's autonomous systems so the DM
referees instead.

Scale assumption: a single private session server, **3-5 players and up to ~15 enemies**
(~20 entities max during combat). This small scale makes some otherwise-infeasible
things (full roster snapshots) cheap and reliable. Optimize for correctness and clarity,
not performance.

## Target stack

- **Java Edition only.** NeoForge mods cannot run on Bedrock (different engine; Bedrock
  add-ons can't express any of this). Every participant — DM and players — must be on
  Minecraft Java Edition.
- **Loader:** NeoForge.
- **Minecraft version:** **26.1** → **NeoForge 26.1.0.19-beta**, **Java 25**. This line is
  unobfuscated (NeoForge uses official Mojang mappings) and Mojang ships Java 25 to end
  users in 26.1, so the mod targets Java 25 (`java.toolchain.languageVersion = 25`).
  - *Alternative considered and rejected:* 1.21.11 (the last obfuscated version) →
    NeoForge 21.11, Java 21. It had a larger body of version-matched references, and its
    only real downside (forced migration) doesn't apply to a server we can pin forever.
    We chose 26.1 anyway for official mappings and to start on the current line rather
    than migrate later. Note 26.1 NeoForge is still **beta**, so expect some API churn.
- **Build plugin:** ModDevGradle (`net.neoforged.moddev`; simpler than NeoGradle — we don't
  need multi-version).
- **Package / group id:** `io.github.dillanprince.minecraftdnd`.
- **Mod id:** `minecraftdnd` (see `gradle.properties`). NOTE: this doc's prose elsewhere
  predates the rename and still refers to the project/classes as "dndinitiative" in a few
  places — the actual `mod_id` is `minecraftdnd`.

## Client/server split

- The initiative/combat logic is server-authoritative.
- The spellbook GUI is a **client-side custom Screen**, so **every player must install the
  mod** (not just the server). This is the other reason it's Java-only.

## Initiative engine (the spine)

Server-side singleton `InitiativeManager` holding:
- `active` flag
- ordered participant list (UUIDs), sorted by initiative roll
- current turn index
- per-player action budget: action / bonus action / reaction availability
- per-turn roster snapshots (see Rewind)
- freeze anchors (see Action gating)

Commands (require permission level 2 / op), under `/initiative` (alias `/init`):
- `start` — roll d20 per non-spectator participant, sort descending, broadcast order
- `next` — advance the turn (and **commit** the prior turn; see Rewind)
- `end` — leave initiative, release everyone
- `status` — show order and whose turn it is

## Action gating

MVP rule: **full freeze** off-turn. While initiative is active, any player who is not the
current-turn player is fully restricted.

Implementation on NeoForge: cancelable events via `event.setCanceled(true)`:
- attacking entities, breaking blocks, placing/using blocks, using items, interacting
  with entities — gate through the relevant `PlayerInteractEvent` variants,
  `BlockEvent.BreakEvent`, `AttackEntityEvent`, etc. (verify exact class names against the
  installed NeoForge API).
- **Movement** has no clean cancel event: use a per-tick snap-back. Record an anchor
  position; each server tick, if an off-turn player has moved beyond a small tolerance,
  teleport them back. The current player's anchor follows them so they roam freely and are
  pinned where they stop when their turn ends.

Note: once players are in permanent Adventure mode (below), block break/place gating is
largely moot — players can't do it anyway — so the gate mostly handles attacks, item use,
and movement.

### Action economy (phase 2, after full-freeze works)

Spells/actions are typed `ACTION` / `BONUS_ACTION` / `REACTION`. Gate by type + budget:
- ACTION → allowed only on your turn, if action unspent
- BONUS_ACTION → allowed only on your turn, if bonus unspent
- REACTION → allowed if reaction available, **regardless of whose turn it is**
Reset all three at the **start of a player's own turn**. The reaction is spent during
*others'* turns, so its lifecycle differs from action/bonus.

## Reactions — human in the loop (chosen approach)

No automatic trigger detection or adjudication in v1. The DM is present and arbitrates.
Players **remember and declare** their own reactions (e.g. an opportunity attack when an
enemy leaves melee range). The mod's job is the mechanical part: enforce that only players
with a reaction available can act off-turn, and route the action correctly.

### Pending-action machinery (also powers DM approval)

Key constraint: **never block the server tick** waiting for human input (it freezes the
whole server). So gated/triggering actions are **deferred**, not executed inline:
1. The action suspends as a `PendingAction` with a stored continuation (the lambda that
   would finish it). It does **not** run yet.
2. A window opens — prompt the DM (and/or eligible reactors) with a **timeout**.
3. A per-tick manager watches the window; on response or timeout it resolves.
4. Apply any reaction effects (may cancel/modify the pending action), then run the
   continuation — or drop it if denied/countered.

Reactions can nest (a counterspell is itself a cast), so pending actions form a **stack**
that unwinds in reverse.

**Build order:** get suspend/resume working for **spell casts first** (they already flow
through the server-side cast pipeline), with DM-approved windows, end to end. Only then
expand to more trigger/action types.

## Rewind — deferral over undo

General "undo the last action" is **not** feasible in Minecraft (no transactions; an
action's side effects — death, loot, XP, knockback, redstone, AI aggro — propagate beyond
what can be cheaply reversed, and clients have already seen it). So:

- **Withhold, don't undo.** Gated actions stay pending and only execute on approval;
  denial just drops them. Nothing happened, so there's nothing to reverse. This is the
  primary mechanism.
- **Start-of-turn roster snapshot** handles the coarse "redo the whole turn" case. At ~20
  entities, snapshot the entire combat roster cheaply and restore all of them. Snapshot
  per entity: position+rotation, HP, status effects, **spent resources (spell slots /
  cooldowns)**, and for players inventory+equipment.
- **Commit on turn-advance.** A turn's effects are provisional until `/initiative next`.
- Two tools cover the space: deny one declared action → deferral; redo a whole turn →
  snapshot restore.

### Downed, not dead (identity preservation)

Intercept the killing blow during initiative: cancel the death, set the entity to a
"downed" state (frozen, AI disabled), and only truly remove it on turn-commit. The
**primary reason** is that initiative order, snapshots, and pending actions all key on
entity **UUIDs** — a real death + respawn gives a new UUID and breaks every reference. A
downed-but-present entity makes a rewind a field-restore, not a respawn.

### Residual

A rewound action already flashed its animation/sound on clients before the server pulled
state back, so a rewind looks like a brief glitch. Acceptable in a DM-run game ("ignore
that, I rewound it").

## Loot & XP — suppressed entirely

Because this is a private session server with no vanilla coexistence to protect, suppress
all drops globally (no rostered-enemy predicate needed):
- `gamerule doMobLoot false` — kills mob item drops and XP in one switch (keep an
  experience-drop hook as a backstop if any XP leaks).
- The DM distributes items manually via `/give` + narration; custom items reuse the
  data-component work from the spellbook.
- Suppressing XP also removes player levels as a moving part the snapshot must track.
- Optional later polish: a DM-authored "loot note" stored as a **data attachment** on an
  enemy, surfaced when players ask what it's carrying. Not core.

## Dedicated-game-mode setup (suppress the autonomous referee)

Much of this is gamerules, not code:
- `doMobLoot` off, `doMobSpawning` off, `naturalRegeneration` off, `doDaylightCycle` off,
  `doWeatherCycle` off, `keepInventory` on.
- Players permanently in **Adventure mode** — the world is DM-authored scenery; players
  can't freely mine/build. This collapses block break/place gating and means the rewind
  system never has to log/restore block changes.
- Principle: suppress systems that change *outcomes* autonomously (spawning, regen, hunger
  damage, automatic death/loot, time); keep systems that are just *interaction* (doors,
  movement, boats, eating as flavor).

## DM tooling — a primary feature, not a convenience

Once vanilla stops refereeing, the DM does: spawn enemies, set HP, control time/weather,
distribute items, advance turns, approve declared actions/reactions, trigger rewinds.
Design this as a **coherent command set** (and eventually a DM dashboard screen), treated
as a first-class part of the mod alongside the initiative engine — not commands accreting
ad hoc.

## Spells & spellbook

- Custom spellbook **item** + client-side custom **Screen**. Reading/browsing spells is
  always allowed (pure client rendering — even off-turn, so players can plan). Only the
  **cast packet** arriving at the server is gated and validated. Casting is
  server-authoritative; never trust the client to self-report a cast.
- Spell type enum: `ACTION` / `BONUS_ACTION` / `REACTION` (drives the budget gate above).
- Spell effects (server-side): apply status effects, spawn projectiles/area entities,
  raycast for targets, deal damage, trigger particles.
- Storage: spells bound to a specific book → custom **data component** (1.21 replaced item
  NBT). Spells known per-player → **data attachments** (modern capability replacement).

## Health display

D&D HP outgrows the 10-heart display fast. Drive HP via the **max-health attribute** and
show it via a custom readout (bossbar / scoreboard / action-bar number), not hearts. Plan
for this early; it's painful to retrofit once combat math assumes hearts.

## Immediate next steps

1. Confirm the blank generated mod **builds** and `runClient` launches.
2. First real code: `InitiativeManager` state + `/initiative start|next|end|status` — the
   spine everything hangs off.
3. Lay the gamerule + Adventure-mode groundwork at the same time (cheap, foundational).
4. Then the full-freeze action gates.
5. Later: action economy → spellbook + spells → pending-action/DM-approval machinery →
   roster snapshot + downed state → DM dashboard.
