# Changelog

All notable changes to this project are documented here.

## v1.0.4

### Added
- **Save-scum protection, part 2: saving locks for 5 waves after a load.**
  Deleting the save on load wasn't enough -- a player could still load, then
  immediately re-save at essentially the same spot and scum against that
  near-identical checkpoint. After loading, "Save Game" is now greyed out in
  the Settings menu (showing "unlocks in N waves") until 5 waves past the
  wave you resumed at, and trying it explains why instead of saving. Starting
  a brand-new run never inherits the lock.
- **"Load Save" now asks for confirmation** (Yes/No, defaulting to No) before
  loading, since loading consumes the save. The prompt shows what's in it
  (wave, class, LVL for each player) and warns about the 5-wave save lockout;
  peeking at the save to show that summary does not consume it.

### Changed
- Main menu "Continue" renamed to **"Load Save"**.
- **Reserve Power renamed to Preserved Power**, with the shop description
  now reading "Grants a minor increase to stats the higher your EXP level".
  Mechanics are unchanged. Note: saves made on v1.0.3 or earlier can't be
  read by this version (the upgrade's internal name changed), so "Load
  Save" will treat them as unreadable -- start a fresh run and save again.
- The quit-to-menu confirmation now warns: "Progress this run is not saved
  automatically. Don't forget to save!"
- Version bumped to v1.0.4.

### Added
- **Save files.** One save slot, written to `laststand_save.dat` next to
  wherever the game is run from. "Save Game" is a new third option in the
  ESC/Settings menu (shows a brief "Game saved!" confirmation); "Continue"
  is a new main-menu option, grayed out until a save exists. A save
  captures both players' class, level/EXP, weapon/armor tier, every
  upgrade level, every enchant on every gear slot, medic kits, shield
  unlock, both wallets, the arena theme, the pause setting, and wave
  progress (current wave, waves completed, boss-kill count). It deliberately
  does **not** capture in-progress combat state -- exact position, current
  HP, or live enemies/projectiles -- loading always resumes at the start of
  the saved wave at full health, the same as any normal wave transition.
  Verified with a full save → (simulated relaunch) → load round-trip test
  covering level, upgrades, enchants, both wallets, and wave state.
- **The save is a one-time checkpoint, not a free undo button.** Loading a
  save deletes it immediately on success, so a bad enchant roll, a risky
  fight, or any other mistake can't be walked back by just reloading the
  same save over and over ("save-scumming"). Saving again afterward creates
  a fresh checkpoint from wherever the player is now, but the old one is
  gone the moment it's used. Verified directly: loading the same save twice
  in a row succeeds the first time and fails the second, as intended.

### Fixed
- **Getting hit now kicks you out of the shop and Settings menu too, not just
  enchanting.** The "kick the player out the instant they take damage while
  pausing is OFF" behavior only ever covered the enchanting player; the shop
  and the Settings/pause menu (both shared, screen-covering modals) had no
  equivalent guard, so a player could keep browsing/reading while an enemy
  was actively hitting them off-screen. A hit on either player now closes
  the shop or Settings menu for both; enchanting still only kicks the one
  player actually enchanting, as before.

### Changed
- Reserve Power's shop description simplified to "Grants a minor increase to
  HP & damage the higher your XP level" (the exact milestone numbers are
  still in the README and the in-match How to Play guide).

### Fixed -- critical
- **Crash: `IllegalArgumentException: Color parameter outside of expected
  range: Alpha` in `DamagePopup.draw()`, reproducible on literally any hit
  once "Pause during shop/enchant/settings" had been turned ON and any menu
  had been opened at least once.** Root cause: `DamagePopup`'s spawn
  timestamp was still stamped with raw `System.currentTimeMillis()` (missed
  in the original paused-clock pass), while everything else -- including the
  `now` used to fade/expire it -- had moved to the new paused-aware virtual
  clock. Once any pause occurred, the virtual clock permanently fell behind
  real time, so every new popup's "spawn time" was in the *future* relative
  to the clock used to fade it, producing a negative elapsed fraction and an
  out-of-range alpha on the very next frame. Fixed by stamping popups with
  the same virtual clock as everything else. `Player.useMedicKit()`'s heal-
  flash timestamp had the identical bug (non-crashing, but would have stuck
  the flash on forever) and is fixed the same way.
- **The enchant wheel's spin animation would never finish (or wouldn't even
  appear to spin) whenever "Pause during shop/enchant/settings" was ON.**
  This was a self-referential deadlock: opening the enchant menu is one of
  the things that engages the pause (it freezes the virtual clock so the
  rest of the world holds still), but the spin animation's own 1-second
  timer was *also* being measured against that same virtual clock -- so the
  very act of opening the menu froze the clock the spin needed to finish.
  The spin timer now runs on real wall-clock time instead, since it's a
  short, self-contained UI animation for the menu the player is actively
  looking at and has no reason to be paused by its own menu being open.
- Both fixes were verified with direct reproductions of the exact reported
  sequence (enable pausing → open a menu → hit an enemy → render
  repeatedly; and enable pausing → open the enchant menu → spin) rather than
  just inspection.
- Added defensive isolation around melee/arrow hit resolution and the top
  level of `tick()`/`paintComponent()`: any future exception in one of these
  spots now logs to the console and the game continues, instead of a hard
  crash. (This was added while still chasing this exact bug down and is
  kept as a permanent safety net.)

## v1.0.3

### Fixed
- **Cooldowns (shield, attack) and wave spawn timing no longer keep
  progressing during a pause.** Introduced a paused-aware virtual clock
  (`GamePanel.virtualNow`) that only advances when nothing is paused; every
  timer that should respect a pause now reads from it instead of the wall
  clock.
- **Players are now fully immobilized while the shop or Settings menu is
  open**, even with pausing OFF. Enchanting was already correctly gated
  per-player; the shop and Settings menu (shared, screen-covering modals)
  were missing the same guard.

### Changed
- **LVL hard-capped at 500** (the bar sits full past that point).
- **Reserve Power reworked again** -- the previous "every 4 LVL" version
  scaled unbounded. Now two separate, milestone-based, capped tracks: +2 Max
  HP at each of 50 LVL milestones (5,15,...,495; caps at +100/level) and +1
  damage at each of 50 LVL milestones (10,20,...,500; caps at +50/level),
  both scaling further by how many levels of the perk are bought, for an
  absolute ceiling of +2,000 Max HP / +1,000 damage.
- **Dark Sorcery buffed**: 1-3 bonus dark orbs per level → 1-4.
- **Armored nerfed**: +2 Defense/level → +1 Defense/level (the
  +1-per-boss-defeated stacking is unchanged).
- **Enemy damage from boss kills reworked** from a flat +4/boss into a
  triangular-ish growing sequence (+2, +4, +7, +11, +16, +22, +29, +37,
  +46...), hard-capped at a flat +50 once wave 51 is reached.
- Arena resized to 50×45 tiles (was 50×36).

## v1.0.1 – v1.0.2

### Added
- **ESC now opens a Settings menu instead of instantly abandoning the match.**
  The old behavior (ESC = immediate `state = MAIN_MENU`, no confirmation) was
  too easy to trigger by accident. ESC now opens a modal with two options:
  **How to Play** and **Back to Main Menu** (gated behind a Yes/No
  confirmation, defaulting to "No"). Whether opening it pauses the
  simulation is governed by the same "pause during shop/enchant" setting as
  the shop and enchanting menus (see the bug fix below) -- so it behaves
  consistently with everything else, not as a special case. A "Press ESC to
  check Settings" hint now shows top-right during normal play.
- **How to Play**: a scrollable, 3-tab in-match reference (Player Control,
  Shop, Enchanting), navigable with 1/2/3, A/D, or Left/Right for tabs and
  W/S or Up/Down to scroll. Player Control lists every keybind for both
  players including the enchant-wheel spin keys. Shop and Enchanting
  sections are generated live from the actual `UpgradeType`/`EnchantType`
  game data (not hand-duplicated text), so the numbers shown can't drift out
  of sync with the real balance -- including all 9 enchantments' I/II/III
  amounts. Long lines word-wrap to the panel width instead of clipping.
- **"Innate Prowess" reworked into "Reserve Power".** Instead of a flat
  damage-per-level bonus, it's now fully dynamic: **+2 Max HP and +1 damage
  for every 4 LVL currently held, per level purchased.** It rises live as
  you gain LVL (leveling up) and falls live as you spend it (enchant spins),
  recomputed on every level-affecting action -- gaining EXP, spending LVL,
  or buying more levels of the perk itself.
- Enemy damage no longer grows a flat +2 per wave (regardless of what's
  actually happening in the run) -- it now grows **+4 per boss defeated**,
  game-wide, tracked in `WaveManager.bossDamageBonus` and applied to every
  enemy type spawned from that point on. Enemy HP-per-wave and the
  speed-per-5-waves growth are unchanged.

### Changed
- **Enchant spin costs nerfed again**: 20/55/115/185 LVL -> **10/20/45/85**
  LVL. Odds per tier are unchanged, same as the previous nerf pass.

### Fixed
- **Shop no longer force-pauses the game regardless of the pause setting.**
  `tickPlaying()` had the shop unconditionally `return`ing every frame it
  was open, while enchanting correctly checked "pause during
  shop/enchant/settings" first -- so turning that setting OFF stopped
  enchanting from pausing but the shop kept pausing anyway. Both (and the
  new Settings/How to Play menu) now consistently honor the same setting.

### Added
- **Obstacle terrain reworked to be less blocky**, per the reference mockup: the old beveled
  block (flat fill + brighter/darker edge shading, reading as a bordered frame) is gone. Obstacle
  tiles are now a flat single color -- yellow-green for Forest, light tan-orange for Desert
  (colors pixel-sampled from the mockup) -- and ~5% of obstacle tiles get a small decoration
  doodle drawn on top: a grass tuft (Forest) or a tiny cactus (Desert), each with a bit of
  per-tile jitter so a patch of decorated tiles doesn't look copy-pasted. Decorations are rolled
  once at arena generation (not re-rolled per frame), so they stay put as the camera pans, and
  they're purely visual -- collision still only checks `TileType.OBSTACLE`, unaffected by whether
  a tile happens to be decorated.
- Player bullets are now dark blue (was yellowish-orange briefly, before that
  the original ruby red shared with enemy bullets); enemy bullets are
  unchanged, so player vs. enemy shots are now easy to tell apart at a glance
  in duo play.
- **Per-player interact keys.** E (P1) and P (P2) each independently open/close
  the shop or their own enchanting menu, instead of a single shared E that
  didn't distinguish who pressed it. The shared shop can still be closed by
  either key; enchanting can only be closed by the key of whoever is actually
  in that menu, so P2 can no longer accidentally kick P1 out (and vice versa).
- **Player-specific spin keybinds.** P1's enchant-wheel spins still use
  1,2,3,4,5 (ascending price, left to right); P2's now use 7,8,9,0,- — the
  mirrored keys on the other side of the number row, matching the existing
  P2 hotbar convention (0,9,8,7) and no longer colliding with P1's own 1-4
  hotbar-slot keys when P2 is the one enchanting mid-match.
- **The 1-LVL "Default" spin is capped at 10 uses per wave**, refilling to
  10 every time a wave is cleared. It has no real cost gate otherwise (1 LVL
  is trivial to earn), so this keeps it from being spammed indefinitely
  between waves. The remaining count is shown live in the enchanting menu
  ("X/10 left this wave", turning red at 0), and the SPIN button itself
  dims to gray and stops responding once attempts run out for that wave.
- **The enchant reveal now waits for the spin animation to finish.** Landing
  a roll used to update the "Currently equipped" summary the instant the
  wheel started spinning, spoiling the result a full second early. The
  category being spun now shows its pre-roll snapshot ("(revealing...)" if
  it was empty) for the whole animation, then reveals the real result the
  moment the wheel stops — same beat as the "Last spin: Tier X" line.

### Changed
- **Priced spin costs nerfed significantly** to make the higher tiers
  actually reachable: 30 LVL -> 20 LVL, 90 LVL -> 55 LVL, 270 LVL -> 115 LVL,
  450 LVL -> 185 LVL. Odds per tier are unchanged — only the price dropped.

### Fixed
- **Armored's boss-kill Defense stack no longer applies to players who
  haven't bought the upgrade.** It was previously granted to every player
  on every boss kill unconditionally, so a "default" build with zero
  Armored levels was still quietly gaining Defense from bosses. It now only
  accrues for players with at least 1 level of Armored purchased, matching
  the perk's actual description ("+1 Defense per boss defeated" as part of
  Armored, not a free stat for everyone).
- **HP Boost enchant no longer hides its effect.** Previously it added a
  temporary "absorb shield" (`Player.tempHP`) that sat behind the visible
  HP bar and quietly soaked up damage before real health did — so the bar
  never actually reflected the bonus, and a fresh or bigger roll looked
  like it did nothing. HP Boost now resizes Max HP directly: applying or
  re-rolling it adds its percentage of your *real* (un-boosted) Max HP as
  bonus Max HP, current HP is left exactly where it was, and the HUD bar
  updates immediately (e.g. `58/100` + a 10% boost -> `58/110`). Removing
  the enchant (replacing that armor piece with something else) clamps
  current HP back down if it was above the new, smaller max (e.g. a full
  `125/125` drops to `100/100` once the boost is gone). See the
  "Enchanting" section of the README for the full behavior.
- Corrected the enchant price-box label that read "`N XP`" — spins are
  priced in whole **LVL**, not XP, matching every other label in that menu.
- Fixed a couple of stale doc comments that no longer matched their code
  (`EnemyType`'s per-wave damage/speed-growth numbers, `ShopUI.drawOrb`'s
  description).

### Notes
- This is the first version-tagged release; nothing else changed
  functionally in this pass. See the README for the full feature set and
  design-decision history leading up to this point.
