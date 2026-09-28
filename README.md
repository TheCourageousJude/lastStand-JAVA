# Last Stand — v1.0.4

See [CHANGELOG.md](CHANGELOG.md) for what changed in this release.

A local 2-player, wave-survival game built in pure Java (Swing/Java2D — **no
external libraries**, so there's nothing to download or configure;
`javac`/`java` from any JDK 17+ is enough).

## Run it

```
cd lastStand
javac -d out $(find src -name "*.java")
java -cp out laststand.Main
```

(On Windows PowerShell, replace `$(find src -name "*.java")` with a loop, or
just open the folder in IntelliJ/VS Code and hit Run on `Main.java`.)

## Controls

| Action                       | Player 1        | Player 2         |
|-------------------------------|-----------------|------------------|
| Move                          | W A S D         | Arrow keys       |
| Attack / use selected item    | SPACE (tap — holding does **not** auto-repeat) | ENTER (same) |
| Select hotbar slot 1 (weapon) | 1               | 0                |
| Select hotbar slot 2 (medic kit) | 2            | 9                |
| Select hotbar slot 3 (shield) | 3               | 8                |
| Select hotbar slot 4 (secondary — Ranger's manual dagger) | 4 | 7 |
| Open shop, or enchant if near the Enchanting Center | E | P |
| Enchant wheel spins (Default / 30 / 90 / 270 / 450 tier, cheapest to priciest) | 1 2 3 4 5 | 7 8 9 0 - |
| Menu confirm                  | SPACE or ENTER (either works, either player) |
| Menu navigate                 | W/S or Up/Down (either works, either player) |
| Open/close the Settings menu (was instant quit-to-menu — now opens How to Play / Back to Main Menu, the latter behind a Yes/No confirm) | ESC | ESC |

**Inventory hotbar**: pressing a number selects that slot (shown highlighted in
the HUD); your next attack-key press *uses* whatever's selected, then
automatically snaps back to slot 1 (weapon) so you don't have to remember to
switch back before you can attack again. Slot 1 is just your normal attack.
Slot 4 only does anything for the Ranger (a manual melee swing with the dagger,
even with arrows in reserve) — it's blank/unused for the Tank.

**In the shop and while enchanting**: whether either one pauses the rest of
the game (enemies, wave timer, cooldowns — all of it, via a paused-aware
virtual clock so nothing silently keeps ticking in the background) is
controlled by the "Pause game during shop/enchant/settings" option (off by
default). Either way, the player(s) looking at the menu are always fully
immobilized while it's open, even with pausing OFF — only enchanting locks
just the one player enchanting; the shop and the Settings menu are shared,
screen-covering modals, so they lock both players at once while enemies (if
unpaused) keep coming. P1 navigates the shop with W/S and buys with SPACE;
P2 navigates with Up/Down and buys with ENTER — each player can only spend
their own wallet, on their own side of the panel.

Main menu: W/S to move the highlight, SPACE to confirm. One item toggles
Player 2 on/off. Character select: A/D (P1) or Left/Right (P2) to switch
between Tank and Ranger, then lock in.

**Saving.** ESC → **Save Game** writes one save slot (`laststand_save.dat`,
next to wherever the game runs from); main menu → **Load Save** restores it
(after a Yes/No confirmation showing what's in it). A save holds both
players' class, LVL/EXP, weapon/armor tier, upgrades, enchants, medic kits,
shield unlock, wallets, arena theme, the pause setting, and wave progress --
but *not* position, current HP, or live enemies: loading always resumes at
the start of the saved wave at full health. To discourage save-scumming
(reloading to undo a bad enchant roll or a risky fight), a save is a
**one-time checkpoint** -- loading deletes it -- and **Save Game is greyed out
for 5 waves after a load**. Saves from older versions may be unreadable.

**In the shop's upgrade row**: 1-4 (P1) / 0,9,8,7 (P2) directly buy that slot's
offered perk for 1 yellow orb (no separate confirm needed — the shop's already
paused). The 4th key on each side rerolls the 3 offered perks -- costs 20 LVL (so a player at LVL 25 drops to LVL 5), and requires being at LVL 20+ to use at all. Buying any perk also auto-rerolls the other two, so the next purchase is always a fresh random pick.

**In the shop's armor row**: a single "Upgrade Armor: [Tier]" line in the same
W/S-navigated list as everything else — one purchase upgrades Helmet, Chest,
and Legs all at once (no per-piece purchases). Z/X/C and `,`/`.`/`/` are
deliberately unbound for now, reserved for the upcoming enchanting system.

## Armor

One shared tier for all 3 pieces (Helmet, Chestplate, Leggings) per player,
climbing the ladder: Leather → Copper → Iron → Gold → Diamond. Each piece
contributes 1/2/3/5/8 Defense at those tiers; since all 3 always match, total
armor Defense is 3/6/9/15/24. Prices: 10/20/30/40 dark orbs for Leather
through Gold, 5 silver orbs for Diamond — which is Tank-exclusive. One
purchase (`Player.tryUpgradeArmor()`) upgrades the whole set at once.

The Tank starts the whole set at Leather for free; the Ranger starts with
none at all and buys up from scratch. Armor Defense adds directly into the
same Attack-Defense formula as the Armored perk (`Player.armorDefense()`,
folded into `takeDamage()`). The buff bar at the bottom of the screen shows 3
small armor-piece icons next to the 9 upgrade icons, tinted by the player's
current tier, so a purchase is visible immediately without opening the shop.

## Upgrade system

9 perks, each leveling independently 0-20, bought with yellow orbs (1 per
level) from a row of 3 random options at the bottom of each player's shop
column. All levels reset to 0 on a new game. Every purchase re-randomizes all
3 offers (so stacking the same perk needs a lucky reroll each time); manually
rerolling costs 20 LVL and is gated behind reaching LVL 20 in the first place
(`Player.REROLL_LEVEL_REQUIREMENT`) -- the shop shows "requires LVL 20" in
place of the reroll line until then.

| Perk | Effect |
|---|---|
| Speedy | +10% move speed / level |
| Armored | +1 Defense / level, **plus +1 Defense per boss defeated** (only for players who own at least 1 level of Armored; uncapped, stacks forever) |
| Recovery | Tank: +2 HP recovered per enemy slain, per level. Ranger: +1 arrow recovered per enemy slain, per level |
| Critical | +3% chance to double a hit / level -- same mechanic for both classes |
| Preserved Power | +2 Max HP per 5-ish LVL held (milestones at 5,15,25,...,495) and +1 damage per 10 LVL held (milestones at 10,20,...,500), per level -- rises/falls live as LVL is gained or spent. Capped at +2,000 Max HP / +1,000 damage (level 20, LVL 500) |
| Dark Sorcery | +1-4 bonus dark orbs per wave cleared, scaling to +20-80 at level 20 |
| Silver Bank | +1-2 bonus silver orbs per wave cleared, scaling to +20-40 at level 20 |
| Grow | +2 max HP per wave cleared, per level (uncapped, stacks forever, heals the gain too) |
| Reforged | +1 flat damage per wave cleared, per level (uncapped, stacks forever, applies to every attack) |

**Attack - Defense combat formula**: whenever a player takes a hit,
`finalDamage = max(1, Attack - Defense)`. `Player.totalDefense()` is now the
single source of truth for Defense (Armored's levels + boss stacks + armor
gear + the Protection enchant's percentage) — the HUD's `DEF` readout used to
only show the armor-gear portion, which made it look like upgrades and
enchants weren't doing anything; it now shows the real total. Defense is
checked in `Player.takeDamage()`, the single entry point for anything
hitting a player (melee contact and enemy projectiles alike). HP Boost no
longer needs special handling there — its bonus lives directly inside
`maxHealth` (see the Enchanting section below), so a hit just comes off
real HP like normal.

Dark Sorcery/Silver Bank/Grow/Reforged bonuses are all personal (only the
buyer's own wallet/stats), on top of the flat +10 dark orbs both players
always get per wave.

Melee Power, Toughen, and Dodge were reworked into Recovery, Grow, and
Reforged respectively -- Melee Power's old flat damage bonus is gone (that
role now belongs to weapon tiers and Preserved Power), Toughen's one-time %
HP grant became Grow's recurring per-wave HP gain, and Dodge's evasion chance
became Reforged's recurring per-wave flat damage bonus. Critical was
originally a distance-scaled bonus for the Ranger, but it barely moved the
needle in testing, so it's the same 3%/level double-damage chance as the
Tank's version, for both classes.

## What's implemented

- Main menu → character select (Tank/Ranger, duplicates allowed) → play loop → game over
- Cross-shaped arena (now 50×45 tiles): flat single-color obstacle tiles (no
  bevel/border — that was reworked away for looking too "blocky"), ~5% of
  which get a small grass-tuft (Forest) or cactus (Desert) decoration doodle,
  rolled once at generation so it doesn't flicker as the camera pans. A
  portal at the end of each of the 4 arms, a center **sentry** block + dashed
  **trade zone** ring. Forest/Desert themes, toggle with T from Options
- EXP bar under each player's HP bar, capped at 50 ("50/50") per level -- a
  full bar rolls over into +1 LVL rather than just capping there, up to a
  hard cap of LVL 500 (the bar just sits full past that point). LVL gates
  the upgrade reroll (needs LVL 20) and is spent on enchant wheel spins
- Bottom-of-screen buff bar (P1 bottom-left, P2 bottom-right) showing all 9
  upgrade levels at a glance, same quantity-badge style as the inventory hotbar
- Beginner wave curriculum for waves 1-15 (fixed enemy pools per your spec),
  fully random pool from wave 16 on. Each **portal** is locked to one enemy type
  for the whole wave (fixes the old "one portal spawning 2+ types" bug) — with
  multiple portals open you'll see different types from different portals, e.g.
  wave 24's 3 portals each spawning a different type
- All 8 enemy types with a simple pixel face each, wave-scaled health (+10/wave)
  and damage (flat +2/wave for every type), +10% speed every 5 waves
- Boss rounds every 5th wave: 5× health, 0.5× speed, 2.5× damage, knockback-immune
- Weight-based knockback for players and enemies; knockback now routes through
  the same collision system as normal movement so nothing gets shoved into a wall
- **Tank**: sword beside the character (flips with facing), green when ready /
  red while on cooldown, swings on attack. Damage scales with weapon tier
- **Ranger**: bow beside the character (same color/cooldown behavior), fires
  ruby-red projectiles while it has arrows; automatically switches to a
  dagger melee swing (33% of current effective bow damage) when out of
  arrows -- scales down with the bow instead of falling further behind it as
  the bow gets upgraded. No more passive ammo regen — arrows are bought from
  the shop
- Passive healing: Tank +3 HP/sec, Ranger +1 HP/sec
- **Per-player economy**: separate wallets for P1/P2. Dark orbs (+10 to *both*
  players per wave cleared), silver orbs (33% chance, credited to whoever got
  the kill), yellow orbs (boss kills, or traded from 100 dark / 20 silver)
- **Shop** (press E): two independent columns, one per player. Weapon tier
  upgrades (Wooden → Flint → Iron → Golden → Diamond, up to +100% damage),
  arrows (10 for 1 dark orb, Ranger only), Medic Kit (+30 HP, 1 silver orb),
  Shield (unlock for 5 silver orbs, then 20s cooldown between activations --
  nullifies all damage and knockback while active, contact or projectile),
  and a full-set Armor upgrade (see the Armor section above). Enchanting and
  the Potion of Experience are still stubbed as "COMING SOON"
- Floating damage numbers appear over a hit enemy on every player attack --
  normal hits fade in 0.5s; a Critical proc is bigger, flashes yellow/orange,
  and fades over 1.5s instead, Terraria-style
- Game over: resets wave to 1 and **both wallets to zero**; a clickable
  "BACK TO MENU" button as well as SPACE/ENTER

## Assumptions I made (all easy to retune, and called out in comments)

- **P2's armor keys weren't being tracked separately from P1's.** Z/X/C and
  ,/./ used to be universal (applying to whichever player was active), which
  is why a P2 enchant could go uncounted if the wrong category ended up
  selected. They're now strictly gated by player number, and entering the
  menu always resets to your one weapon slot as a reliable starting point.
- **Spin costs are now whole LVLs**, not EXP — the same five numbers
  (1/30/90/270/450) just changed units, per "subtract by LEVEL... not 1/50 of
  LVL 8." That does make the pricier spins a serious investment (270+ levels
  earned), which reads as intentional given the "gambling" framing.
- **Spin animation** is 1 second, fast-then-slow, and purely visual — the
  roll itself resolves the instant you press the key (so results can't be
  re-rolled by mashing buttons mid-spin), the wheel just doesn't show it
  until the animation finishes.
- **E's shop-vs-enchant behavior checks every player's position**, not just
  whoever physically pressed it (there's only one E key to share) — the
  first player found standing inside the dashed boundary gets the enchant
  menu; otherwise it's the shop.
- **The 30 XP spin's odds** weren't given numerically (only described
  narratively as "T1 removed, replaced by T2 chance, then T2 into T3...
  until T5 exists"), so I read it as "shift the DEFAULT odds up one tier" —
  `{0, 75, 20, 4, 1}` for T1-T5. The 90/270/450 XP rows are your exact
  numbers. All five spin options live in `shop/EnchantSpinOption.java` if
  you want to hand me different numbers.
- **DEFAULT's odds** (the "1 XP" always-available spin) reuse the original
  75/20/4/1/0-ish numbers from your very first enchanting message
  (74.9/20/4/1/0.1, keeping Diamond a hair above literal zero so it's
  reachable in principle).
- **A spin fully replaces that gear's enchants**, rather than adding to what
  was there before — matches the "no reveal ahead of time, pure gamble" feel
  you described, though it does mean a bad roll can downgrade something you
  already had.
- **Weapon damage is now an explicit table per tier**, not a shared
  multiplier — Sword: 50 (Wooden) / 60 / 75 / 95 / 110 (Diamond). Bow: 90
  (Wooden) / 105 / 130 / 165 / 220 (Diamond), exactly as specified. Both
  classes are 100 max HP.
- **Ranger's dagger damage** is now 33% of the Ranger's current *effective*
  bow damage (`arrowDamage()`, so it includes tier and Preserved Power), plus
  Melee Power, capped at 80 — replacing the old flat 23. Flagged as a
  placeholder ahead of a possible throwable-darts rework, per your note.
- **Shield duration** — 5 seconds of full damage/knockback immunity per
  activation (`Player.SHIELD_DURATION_MS`), not specified in the doc; the
  20s cooldown between activations is exactly as you specified.
- **Weapon upgrade path** — only the *next* tier is purchasable at a time
  (can't skip straight to Diamond), since the tiers read as a ladder.
- **Enemies-per-wave formula** — still `3 + waveNumber`, not specified exactly
  in the doc.
- **EXP formula** — flat 30 EXP per kill at wave 1, +5 for every wave progressed
  since (so wave 10 kills are worth 75 EXP). Resets to 30 whenever the wave
  counter resets (game over), since it's computed live off the current wave.
- **Inventory hotbar slot 4** — for the Ranger, made it trigger a manual dagger
  swing on demand (melee even with arrows left), since the sketch showed a
  distinct 4th icon only on the Ranger's side. It's unused/blank for the Tank,
  matching the sketch's blank 4th slot there.

## Enchanting

A structure at the exact center of the arena — the **Enchanting Center** —
marks the enchant area, with a dashed boundary (now colored to match the
center block) showing how close you need to be. It's purely visual and fully
passable now (an earlier pass made it solid, but that let mob AI — which has
no real pathfinding — get permanently stuck against it, and made the center
annoyingly hard to reach at times).

**E is now one context-sensitive button, for either player.** Outside the
dashed boundary, E opens/closes the regular shop, same as always. Inside it,
E opens/closes the enchant menu instead for whichever player triggered it,
freezing just that one player in place — the rest of the game (waves, the
other player) keeps running by default, so it's genuinely risky to enchant
mid-fight. There's an Options toggle ("Pause game during shop/enchanting",
press P from Options) to make both shop and enchanting fully pause everything
instead, if you'd rather it be safe.

Only one player can be enchanting at a time. V/M (weapon) are shared since
there's only ever one relevant weapon per class, but the armor-piece keys are
kept strictly separate per player, same as the inventory hotbar convention:

| Action | P1 | P2 |
|---|---|---|
| Select Sword | V (Tank only) | V (Tank only) |
| Select Bow | M (Ranger only) | M (Ranger only) |
| Select Helmet | Z | , comma |
| Select Chestplate | X | . period |
| Select Leggings | C | / slash |
| SPIN (default, 1 LVL, capped at 10 uses/wave — see below) | 1 | 7 |
| SPIN at 10 / 20 / 45 / 85 LVL | 2 / 3 / 4 / 5 | 8 / 9 / 0 / - |
| Exit (or open the shop outside the boundary) | E | P |

The default 1-LVL spin is capped at 10 uses per wave per player (refills to
10 every time a wave clears) since it otherwise has no real cost gate to
limit spamming it. The other four prices were nerfed twice since first
shipping (30/90/270/450 → 20/55/115/185 → the current 10/20/45/85) to make
the pricier, better-odds tiers actually reachable.

Entering the menu always starts on your one main weapon slot (Sword for
Tank, Bow for Ranger) — pressing an armor key a **second time** (while that
same piece is already selected) toggles back to it too, so there's always an
obvious way back without hunting for V/M.

**It's a gamble, not a menu.** Each of the 5 gear categories (Helmet,
Chestplate, Leggings, Sword, Bow) has its own pool of 3 enchant types and its
own *independent* enchant state — a helmet and a pair of leggings can have
completely different Regen levels, even though all 3 armor pieces still
share one unified `ArmorTier` for their raw Defense stat. The pools match
your spec exactly (Regen/Protection/HP Boost for every armor piece; Quick
Charge/Arrow Damage/Arrow Speed for bow; Swing Speed/Sword Damage/Lucky
Strike for sword), each with I/II/III amplifiers.

**Protection and HP Boost were reworked to be percentage-based** rather
than flat numbers. Protection now grants +10%/+25%/+50% of your *total*
Defense (Armored levels + boss stacks + armor gear, combined) rather than a
flat +2/+5/+10 — summed across every armor piece that has it socketed, then
applied once to the total. **HP Boost (v1.0.0 fix) now resizes your Max HP
bar directly instead of a hidden absorb-pool** — it adds +20%/+45%/+90% of
your *real* (un-boosted) Max HP as bonus Max HP, summed across every armor
piece that has it socketed. Your current HP is left exactly where it was —
58/100 rolling a 10% boost becomes 58/110, not 68/110 — so the bar visibly
grows and natural regen/medic kits just fill the new headroom over time.
Re-rolling a bigger or smaller amount (or rolling the enchant away entirely
by replacing that piece with something else) recomputes the bonus from
scratch off your real Max HP every time, rather than stacking; if your
current HP was above the new (smaller) max, it's clamped down to match —
e.g. full at 125/125 with a piece removed drops straight to 100/100.

There's no preview and no picking between offered options ("no enchantment
reveals") — you pick a price (1/30/90/270/450, each with its own fixed odds
for landing Tier 1-5, per your numbers) and spin. **The cost is paid in whole
LVLs now, not a fraction of the EXP bar** — spinning at the "1" price costs
1 full LVL, not 1 out of 50 EXP within your current level. The wheel spins
for a full second (fast, easing to a stop) before the result is revealed;
the roll itself is locked in the moment you press the key, so what you see
during the spin is pure animation, not suspense over an undetermined outcome
Whatever Tier you land on, the result is a random combination of that
category's 3 enchant types whose amplifiers sum to exactly that tier — e.g. a
Tier 3 result might be one type at III, one at I + one at II, or all three at
I — matching your bow walkthrough exactly (Tier 1 = 3 possible outcomes,
Tier 2 = 6, Tier 3 = 10, and so on). The result **replaces** that gear's
enchants outright.

Every enchant effect is live in combat: Sword/Arrow Damage add straight to
your hit, Swing Speed/Quick Charge shrink your attack cooldown, Protection
adds to Defense, Regen adds to passive healing, HP Boost directly grows your
Max HP (see above), Lucky Strike stacks its own crit chance onto the
Critical perk (sword swings only), and Arrow Speed boosts both projectile
velocity and the knockback it deals.

## Not yet built (explicitly marked "coming soon" per your notes)

Potion of Experience. Armor, the upgrade-perks shop, and enchanting are now
all implemented (see the sections above).

## Project layout

```
src/laststand/
  Main.java
  core/    GameWindow, GamePanel (loop + state machine), InputHandler, Constants
  entity/  Entity, Player, PlayerClass, Enemy, EnemyType, Projectile, WeightClass, AttackType
  shop/    Wallet, WeaponTier, ArmorTier, Currency, ShopItem, ShopUI, UpgradeType,
           EnchantCategory, EnchantSpinOption, EnchantType, EnchantUI
  world/   Arena, Portal, TileType, ArenaTheme
  wave/    WaveManager
  ui/      HUD
  fx/      DamagePopup
```
