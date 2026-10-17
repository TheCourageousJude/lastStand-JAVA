# Changelog

All notable changes to this project are documented here.

## v1.1.5

A big gameplay + presentation update.

### Added
- **A grid-snap setting in the Music Editor (G or the SNAP button).** Notes used to snap to their own length (at most a
  beat), so a note of 1/4 or longer could only start exactly on a beat. The grid is now separate: 1/16 (the default),
  1/8, 1/4, or AUTO for the old behaviour. The arrow keys nudge by the same grid.
- **Extra bars in a tune file are no longer dropped quietly.** If the file has bar lines past its `bars=` number, all of
  them are used and the editor / Sound Test say so (before, the bars after `bars=` were silently ignored).
- **Chords follow the notes (Music Editor, on by default; F or the CHORDS FOLLOW pill switches it).** When all notes are
  selected and moved, the chords move with them: a pitch move transposes every chord by the same number of semitones, a move
  by whole bars slides the chords along by that many bars (an emptied bar becomes "none"), and a part of the tune moved on
  its own leaves the chords alone. One Ctrl+Z undoes notes and chords together.
- **Multi-select in the Music Editor.** Ctrl+A selects every note, Shift+click adds or removes one, and Shift+drag on an
  empty spot draws a selection box. Dragging any selected note, or the arrow keys (Shift+Up/Down = an octave,
  Shift+Left/Right = a bar), moves them all together: spacing is kept, and the group stops when one note reaches an
  edge. Delete removes them all, Esc deselects (then leaves), T refuses politely, and one Ctrl+Z undoes a whole group
  move. **Ctrl+Up / Ctrl+Down** moves every chord a semitone (chords and notes stay independent).
- **A bar can have no chord** (right-click its chord cell in the Music Editor; `-` in the tune file, also read as
  `none` / `n`): no bass and no arpeggio for that bar, drums unaffected. Clicking the cell again brings the chord back.
- **The tune file is forgiving about pasted bars, and says so when something's wrong.** Quotes, trailing commas, `//`
  comments, curly quotes and lines without `barN=` are accepted; a missing `bars=` line uses as many bars as were given.
  Previously a file with any of these was silently replaced by the starter tune (the editor and the Sound Test now show
  the reason in red), and a missing `bpm=` / `chords=` now says what was assumed.
- **Ties in the Music Editor and the tune file.** Press **T** on a note to tie it to the note touching it on its left:
  it carries that note on (same pitch) with no new start, which is how a note carries over a bar line. In
  `laststand_workshop_track.txt` a tie is written `T 1.5` (hold the note before it for 1.5 more beats), next to `R 2.5`
  for a rest. A tie after a rest or gap is refused with a plain message; deleting the note before a tie turns it back
  into an ordinary note. Nothing else changes: all 23 sounds are bit-for-bit identical.
- **In-game SFX Editor for every sound effect.** On the Sound Test, select any sound and press **E** (or click EDIT SOUND).
  Each effect is a stack of tones (square / triangle / saw / noise / air); edit their wave, timing, pitch slide, volume,
  fade, vibrato and pulse width, add / duplicate / delete layers, and toggle the 8-bit crunch. A waveform strip shows what
  will play, Space plays it unsaved, AUTO-PLAY replays after every change. **Save** (Ctrl+S) writes `laststand_sfx.txt` and
  the **whole game** then plays the edited sound; Discard, RESET TO ORIGINAL, Ctrl+Z and a leave-with-unsaved prompt are there
  too. Every built-in effect converts to an editable patch and renders bit-for-bit like the original recipe. New classes
  `fx/SfxPatch` (tone list + its file) and `ui/SfxEditorUI`; new `ui/SoundEditor` interface shared with the Music Editor;
  new `Sfx` hooks `builtinPatch`, `renderPatch`, `previewPatch`, `reloadSfx`, `hasSfxEdit`.
- **In-game Music Editor for the hidden "??? 2" track.** On the Sound Test, select "??? 2" and press **E** (or click
  EDIT TRACK) for a piano roll: click to add notes, drag to move or stretch them, right-click to remove, one chord per
  bar (the bass and arpeggio follow it), plus tempo, number of bars (4-32), and the bars where the drums and the fuller
  melody sound come in. PLAY auditions the tune as you edit; **Save** (Ctrl+S) writes `laststand_workshop_track.txt`,
  **Discard** goes back to the last saved version, Ctrl+Z undoes, and leaving with unsaved edits asks first. "??? 2" plays
  the saved tune; with no saved file it is the original starter tune, bit for bit. New classes `fx/WorkshopTune`
  (the editable tune + its text file) and `ui/MusicEditorUI`; new `Sfx` hooks `previewTune`, `stopPreview`,
  `previewSeconds`, `previewNote`, `reloadWorkshop`. The Sound Test now also passes mouse drags, releases, right-clicks
  and the wheel through to the editor.
- **Background music support.** A looping song can play under a game (quietly: about 30% of an effect's volume, so effects
  stay clear on top). It follows the game: it plays while a game is in progress, including under the shop and pause menus,
  and is silent on the menus and after game over; **F10** turns it on/off and F9 mutes it with everything else. Nothing
  plays by default: the two hidden songs are chosen with a secret key combination (see `chooseMusic` in `GamePanel`), and
  the song that's playing is remembered across menus. The loop is seamless: the version built for looping has its last
  bar flow on into the first instead of the held ending. New `Sfx` pieces: `playLoop`, `startMusic`, `stopMusic`,
  `isMusicPlaying`, `currentMusic`, `loopSamplesFor`.
- **Ctrl+S opens the Sound Test when S is let go** instead of the moment it's pressed (in the Music Editor it still saves
  straight away).
- **Loop setting in the Sound Test.** L (or the LOOP pill beside the title) makes songs repeat seamlessly until X.
- **Secret Sound Test soundboard (Ctrl+S, from any screen).** A MyInstants-style wall of big glossy round buttons, one
  per sound effect (21), labelled and colour-coded by group. Click one to hear it (or use arrows/WASD + SPACE); R plays
  them all in order, X stops. A strip shows the selected effect's waveform with a moving playhead, length, peak level,
  where it's used in-game, synth-vs-WAV source and rate limit. Freezes the game while open and returns you to whatever
  screen you were on. New `Sfx` hooks: `preview` (skips the rate limit), `silence`, `samplesFor`, `isRecordedOverride`,
  `isAudioAvailable`.
- **`air(...)`: smooth, realistic air for the sound synth.** A new kind of line next to `noise(...)`. The chiptune noise
  flips between just two values (+1/-1) and every sound is rounded to 8-bit steps, which is what makes everything sound
  "pixel-y". `air(at, dur, f0, f1, vol)` uses smooth random values through a sliding band-pass filter (f0 -> f1 is the
  centre of the airy band in Hz), and a recipe can set `m.smooth = true` to skip the 8-bit rounding. The Dagger throw now
  uses it (one short, focused "fwip" of air that dulls as the knife flies away); every other sound is unchanged, bit for
  bit. `air(...)` takes an optional `.q(x)` to focus the band: low = broad and fuzzy ("sandy"), ~2 = a whoosh, 5+ whistles.
- **Wave scenarios (wave 6+, hidden in-game).** Non-boss waves from 6 roll one of: *Swarm*
  (30 fragile, fast, weaker-hitting enemies; 3 portals), *Heavy Hitters* (slow, hard-hitting;
  8 with 1 portal, 15 with 2), *Standard* (20 classic enemies), *Power Rangers* (forces 3
  portals; 20 skeletons + 10 blaster golems, split evenly), and from wave 26 *Savages* (20 red
  enemies, 2 portals) and *Ghastlies* (near-invisible, 15/20, 1-2 portals). Each has its own
  spawn pacing (all out in ~3-7.5 s). Odds: Power Rangers 10% on every wave; on waves 26+
  Savages 40% (2-portal waves) and Ghastlies 8%; the rest split evenly. Nothing on screen names
  the scenario (`-Dlaststand.debug=true` logs it).
- **Boss specials**, one rolled per boss wave and named in yellow above the boss: Miniature,
  Armored, Speedy, Strong, Sorcerer (summons 5/7/10 of its own type at 75/50/25% HP) and, from
  wave 25, Savage (25% of rolls, 50% from wave 50). Miniature/Savage speed is a fraction of the
  player's (60%, +1% / +2% per boss defeated).
- **Savage hits** deal the enemy's raw damage plus a % of the victim's Max HP (16%, bosses 25%)
  that ignores Defense; Shield blocks it entirely. Savage skeleton/blaster-golem variants fire
  nightmare-purple bullets 75% faster. Ghastlies hit for raw + 3% Max HP. After a Savage/Ghastly-type
  hit lands, further hits of that kind are ignored for 0.5 s so a bunched pack can't land every strike
  in one frame (per-hit damage unchanged; a full pack now takes ~1.8-3.3 s to kill a stationary
  140-HP player instead of ~0.7-1.1 s).
- **Healing Potion** -- a 3-bar rechargeable flask replacing Medic Kits (25% Max HP, 5 s
  cooldown, +1 charge per cleared wave, 3 silver to buy/refill).
- **Rechargeability** perk (-1 charge needed for your ultimate per level; 40 -> 20 at Lv20).
- **Innate Talent** (reworked Recovery): Tank heals 1%/level of Max HP per kill (20% at Lv20);
  Ranger has 3%/level chance a shot doesn't use an arrow (60% at Lv20).
- **Ultimates.** The box beside the hotbar's 4th slot is now the ultimate: 40 charge (1 per enemy slain,
  -1 per Rechargeability level), shown as a blurred icon with an upward-filling bar that turns crisp and
  electrified when full. Tap the weapon key 3 times within 0.8 s to fire it; kills made by an ultimate don't
  recharge it. **Ranger: Giant Arrow** -- its own big sprite, auto-aimed at the nearest enemy, moderate speed,
  pierces everything, no knockback, passes through walls, 150% damage. **Tank: Thorns** -- 10 s red shield
  with 6 pulsing dark-green spikes: 80% damage resistance (whole hit, Savage bonus included), each 0.5 s pulse
  hits touching enemies for 50% of current damage + 10% of their max HP and knocks them back; freezes the
  regular Shield's timers and can't be stacked with it. Charge is saved. (HUD strip is 6 px taller.)
- **HUD:** a silver timer bar above the shield that drains over its 5 s of protection.
- **Smarter enemy AI.** Enemies attack only with a clear line to the target and route around the
  corner blocks (ranged ones reposition until they can see the player) -- no more shooting into
  walls or pinning against a corner. Uses a cached shortest-route map; ~0.1 ms/frame for 80 enemies.
- **Fullscreen** (F11 / Alt+Enter) with display scaling; the HUD and world view stretch to fill
  widescreens, menus stay centered, mouse clicks are mapped through the scale.
- **Floating-island arena.** The camera is dead-centered on the player (never clamped to the map);
  beyond the edge is a sky-blue backdrop with slow blocky clouds. The playable cross is wrapped in
  an irregular, textured rock border (3-6 blocks thick, rounded corners; sandstone in Desert) with
  a jagged rocky underside. The border is solid for movement, shots and pathfinding.
- **Sound effects** -- 21 chiptune-style effects synthesized in code (no audio files), mixed in software and
  rate-limited; F9 mutes. Menu blips, shots, hits, deaths, hurt, potion, shield, shop buy/error, level-up,
  ultimate ready / Giant Arrow / Thorns, wave start / boss warning / wave clear, game over. Any effect can be
  replaced by a WAV in `src/sounds/`. Silent (no crash) when there's no audio device.
- **Executable JAR** (`release/LastStand-v1.1.5.jar`, `java -jar`, built for Java 17+) plus `build.sh` / `build.bat`
  so anyone can rebuild it, and a `.gitignore`.
- **Dying destroys the save file** for that mode (even one made earlier in the same run); the
  game-over screen says so.

### Changed
- **The level-up jingle plays only once per 5 levels a player obtains** (levels 5, 10, 15, ...). It counts levels
  gained, so spending LVL at the enchanting wheel doesn't make it play again, gaining several at once plays it once, and
  each player is counted separately (`LEVEL_UP_SOUND_EVERY` in `GamePanel`).
- **The wave-start sound is delayed 1 s and the boss warning 0.5 s after their wave begins**, so neither is drowned out by
  the previous wave's last death sound (`WAVE_START_SOUND_DELAY_MS` / `BOSS_WARN_SOUND_DELAY_MS`). They run on the game
  clock, so they wait if a menu pauses the game, and are dropped on game over.
- **A much more dramatic boss warning** (2.2 s instead of 0.7 s): a swelling riser, a huge boom with a crash and a
  metallic rumble, then a slow doom motif with a clashing note.
- Window is now 1000x740 (was 1000x700) -- tall enough for the bigger HUD, short enough to leave
  the taskbar/desktop reachable. On shorter screens it shrinks to fit.
- Playable arena is 55x45 tiles (odd sizes make the cross exactly symmetric: N=S, W=E; horizontal
  arms are longer than vertical); the rock border is added around it, so arm lengths and portal
  spacing are exactly those tile counts.
- **Bosses no longer move at half speed** -- regular speed for their type; specials adjust from there.
- **Speedy** nerfed 40%: +6% move speed per level (Lv20 is x2.2 instead of x3.0).
- Savage / Ghastly / Power Rangers odds and speeds retuned (Ghastly: base speed 2.0, scales with waves).

### Fixed
- **Stuck sound loop.** The mixer used to stop writing to the sound card whenever no effect was playing. Some
  systems (Windows' DirectSound especially) keep a small circular buffer and just REPLAY it if nothing refills
  it, so the tail of the last sounds could loop forever -- and muting couldn't stop it, because nothing was
  being sent to clear. The mixer now always writes a block, silence when idle, so any stale audio is overwritten
  within a few milliseconds; the card buffer was also halved (about 46 ms) to keep latency low. Verified against a
  simulated circular-buffer device: the old behaviour loops indefinitely (and survives a mute), the new one is
  silent within ~23 ms of muting, with ~35 ms from trigger to first sound.

### Removed
- **Armored** and **Grow** perks (Rechargeability took one slot; the other is vacant for now).
  They remain as hidden retired enum constants so old saves still load; their levels are dropped.
- Stackable **Medic Kits** and the **Potion of Experience** placeholder.

### Notes
- Saves from v1.1.3 still load (retired perks dropped; old Medic Kit counts are not carried over).
- Version bumped to v1.1.5.

## v1.1.3

### Investigated -- reported "P2 doesn't benefit from damage upgrades"
Traced upgrade purchasing (`tryBuyPerk`), storage (`upgradeLevels`, a per-player `EnumMap`), the
wave-completion loop that applies Grow/Reforged, the damage formulas (`daggerDamage`/
`arrowDamage`/`combatPinDamage`), and the firing code (`fireDagger`/`fireArrow`/`fireCombatPin`).
All of it is correctly per-player -- no static fields, no hardcoded P1 references found in any
of these paths, so this isn't a "P2 is silently reading P1's stats" bug as far as static review
can tell.

One concrete, confirmed mechanical fact that could fully explain Preserved Power specifically:
**it requires LVL >= 20 to contribute anything at all** since last update's rework
(`level / 20` milestones). Below LVL 20, both its HP and damage bonus are exactly 0 -- if P2's
LVL is lower than P1's, this alone explains it with no bug involved. Reforged has no such
threshold, so that doesn't explain Reforged specifically.

### Added
- **"DMG: N" readout** next to the existing "DEF: N" in the buff bar -- there was previously no
  on-screen way to tell whether a damage-type upgrade or enchant (Reforged, Preserved Power's
  damage half, Dagger/Arrow Damage) was doing anything at all, unlike Grow (visible HP growth)
  or Armored (visible DEF). Shows each player's current effective per-hit damage (Dagger for
  Tank, arrow for Ranger) with tier/Preserved Power/enchants/Reforged already baked in --
  directly comparable between P1 and P2 now, instead of a guessing game.
- Version bumped to v1.1.3.

### Fixed (same version -- found via the DMG readout above)
- **Tank damage was hard-capped at 80, even for a Diamond Dagger.** Real bug, not balance:
  Diamond's own base damage is 110 -- already over the cap with zero bonuses added -- so
  Golden/Diamond Tanks were silently capped to WORSE effective damage than a bare Iron dagger
  the instant they upgraded. It also flatly broke Reforged's "uncapped, stacks forever" design:
  once at the cap, Reforged (and everything else) did nothing. The old 80-damage cap is removed
  entirely from both `daggerDamage()` and `combatPinDamage()`, which are now uncapped, matching
  `arrowDamage()` (which was never capped in the first place).

## v1.1.2

### Fixed
- **v1.1.1's save-file split orphaned everyone's existing save.** Switching to two per-mode
  files (`laststand_save_solo.dat` / `laststand_save_coop.dat`) meant the game simply stopped
  looking at the old shared `laststand_save.dat` -- your save wasn't deleted, the game just
  couldn't see it anymore.
  New `SaveManager.migrateLegacySaveIfPresent()` runs once automatically on startup: if the old
  file is still there, it reads it, checks its own `p2Enabled` field to figure out which mode it
  belongs to, and copies it into that mode's new file (without overwriting one that already
  exists there), then removes the old file. If the old file can't be read at all, it's left in
  place untouched rather than silently discarded.
  **If you still have your old `laststand_save.dat` sitting next to the game, this update
  should recover it automatically the next time you launch** -- no action needed on your end.
- Rewrote the README's Saving section, which was still describing the pre-v1.0.8 single-save
  design (one shared save slot including the arena theme and pause setting).
- Version bumped to v1.1.2.

## v1.1.1

### Fixed
- **v1.1.0 didn't compile** -- `GamePanel.java` had 4 leftover calls to `SaveManager`'s old
  no-argument methods (lines 393, 445, 606, 1352, all in the save/load path) that were never
  updated when `SaveManager` was switched over to two separate, mode-flagged save files. All 4
  now pass `p2Enabled` like every other `SaveManager` call site already did.
- This also means the separate-save-file design is now actually complete end to end: solo (P1
  only) and 2-player runs each get their own save file (`laststand_save_solo.dat` /
  `laststand_save_coop.dat`, see `SaveManager`'s class doc), so a solo save can only ever be
  loaded into a solo session and a 2-player save only into a 2-player session -- supersedes
  v1.0.8's single-shared-save approach.
- Version bumped to v1.1.1.

## v1.1.0

### Fixed
- **A maxed (Lv20/20) upgrade could still show up in the shop's 3 offered slots**, uselessly
  occupying one instead of an available perk (visible in your screenshot: Silver Bank at MAX
  still sitting in the reroll list). `Player.rerollPerks()` now excludes anything already at
  `MAX_LEVEL` from the pool it picks from -- a maxed perk gets swapped out for an available one
  the moment it happens, whether from a fresh reroll or the automatic re-roll after any
  purchase. Only falls back to including maxed perks if literally all 9 are maxed.

### Changed -- another balance pass
- **Boss HP scaling: 18% -> 11%** compounding per boss defeated ("definitely very, very tough").
- **Reforged: +3 -> +2** flat damage per wave cleared, per level (nerfed again).
- **Recovery (Tank) reworked**: was +2 flat HP per kill, per level; now restores a flat 3% of
  current total Max HP per kill. Doesn't scale further with additional Recovery levels beyond
  the first -- the request dropped "per level" for this one specifically (unlike every other
  upgrade), so treated as a flat percentage rather than 3%-per-level, which would spiral fast at
  high levels. Flag this if you actually wanted it to scale per level instead. Ranger's Recovery
  (+1 arrow/kill/level) is unchanged.
- **Preserved Power reworked again**: HP and damage bonuses are now unified -- both +1 per
  level at each of 25 milestones, every 20 LVL (20, 40, ... 500), same increment and interval
  for both stats (previously HP was +2 per milestone every 10 LVL, damage was +1 per milestone
  every 10 LVL). New ceiling: +500 Max HP / +500 damage at Preserved Power level 20 and LVL 500
  (was +2,000 / +1,000). Upgrade description text unchanged, per request.
- **Enchant tier background color lightened**: the darkened fill behind a gear glyph was 35%
  brightness, hard to tell the tier color apart from the background -- now 50%.
- Version bumped to v1.1.0.

## v1.0.10

### Fixed -- icon colors were tracking the wrong thing
- **Correction to v1.0.6/v1.0.8's weapon/armor icon coloring.** The box behind a gear glyph and
  the glyph's own color were both being driven by the same thing (WeaponTier/ArmorTier's
  material). They're two independent stats and now use two independent colors:
  - **The box behind the glyph** is now colored by that specific slot's own ENCHANT tier (Tier
    1-5 -- the same Tier the enchant wheel itself rolls, see `EnchantSpinOption.TIER_COLORS`:
    green/orange/silver/gold/cyan), darkened 35%. New `Player.enchantTierFor(EnchantCategory)`
    sums that slot's 3 enchant types' amplifiers (0-9 total) and buckets it down into Tier 1-5;
    a slot with nothing enchanted into it yet shows a plain neutral dark gray box instead.
  - **The glyph itself** is colored by the weapon/armor's MATERIAL (`WeaponTier`/`ArmorTier`
    .displayColor) -- reverted back to actual material tones (Wooden=brown, Flint=dark gray,
    Iron=silver, Golden=gold, Diamond=cyan; same idea for Leather/Copper/Iron/Gold/Diamond
    armor) instead of the green/orange/silver/gold/cyan palette, which was never meant for
    material in the first place -- that palette belongs to the enchant tier (see above).
- Version bumped to v1.0.10.

## v1.0.9

### Changed
- **Dagger range: 1.5 -> 3.0 tiles.**
- **Combat Pin range: 0.75 -> 1.75 tiles**, alongside a damage nerf: 33% -> 20% of the
  Ranger's effective bow damage. (The actual old value was 33%, not the 0.4/40% floated when
  asking for this -- nerfed the real number down to 20% as intended.)
- Version bumped to v1.0.9.

## v1.0.8

### Changed
- **Reforged buffed from +1 to +3 flat damage per wave cleared, per level** (still uncapped,
  stacks forever).
- **Weapon/armor tier icons: glyph keeps the full tier color, box behind it is now a darkened
  (35%) version instead of the same full-brightness color** -- fixes the glyph blending into
  an equally-bright background.
- **Dagger/Combat Pin/Bow hotbar icons redesigned** as small blocky pixel-art glyphs (Dagger:
  4x3, hilt + a 3-row blade + a tip pixel; Combat Pin: 4x3, a thin straight needle -- visibly
  smaller/thinner than the Dagger; Bow: 3x2, a string with the crescent limbs' tips poking out).
  Shared as constants on `Player` (`DAGGER_GLYPH`/`COMBAT_PIN_GLYPH`/`BOW_GLYPH`) so the hotbar
  and the in-world weapon rig can't drift apart.
- **In-world weapon rig now uses the same glyphs**, and swaps from the Bow glyph to the Combat
  Pin glyph the instant a Ranger runs out of arrows -- the held weapon visibly changes, not
  just the ammo count.
- **Dagger/Combat Pin no longer swing OR recoil-kick on attack -- they briefly disappear**
  (~90ms) right when thrown, since the projectile now on screen IS the weapon; it reappears
  after (infinite ammo, so there's always another one).
- **Arena squared to 50x50** (was 50x45) -- the horizontal arms of the cross-shaped path
  reached noticeably farther from the center than the vertical arms; all 4 arms/portals are
  now equidistant from center.
- **Boss kills now grant 5x the normal per-kill EXP formula**, on top of the existing
  yellow-orb/Armored-stack/damage-bonus rewards a boss kill already grants.
- **A downed player (not the whole team) is revived at the start of the next wave, if that
  wave is cleared.** Hooked into the same wave-completion check that already awards orbs --
  `alive` and HP are restored to full; gear, upgrades, and ammo are untouched (nothing is
  wiped by dying). The whole team still has to go down for GAME_OVER, unchanged.

### Fixed -- save state
- **Save/load no longer restores the arena theme or the "pause during shop/enchanting"
  option.** Those are session/display settings, not run progress -- removed the two fields
  from `SaveData` entirely (old save files with them still in the stream deserialize fine;
  Java's default serialization just ignores fields the class no longer declares).
- **Loading now uses the CURRENT session's P1/P2 setup, not the save's own recorded one.**
  Previously `p2Enabled` was forced from the save file, silently overriding whatever the
  player had just chosen on the main menu. Loading a solo save into a 2-player session now
  gives P2 a fresh character instead of being blocked entirely; loading a 2-player save solo
  just leaves the save's P2 data unused.
- Version bumped to v1.0.8.

## v1.0.7

### Changed -- Weapon remake: melee swings are gone
- **Tank's sword swing is now a thrown Dagger (infinite ammo).** Short range (~1.5 tiles,
  dissipates on its own even in the open), medium fire rate (~400ms cooldown, was 650ms), small
  knockback, a bit smaller than an arrow. Same damage formula and enchant hooks as the old sword
  (tier + Preserved Power + Reforged, capped at 80); the enchant category is still internally
  `EnchantCategory.SWORD` so old saves' enchant slots keep resolving, but everywhere the player
  sees it now reads "Dagger" (shop, enchant UI, Character Select, help text). The "Swing Speed"
  enchant is now "Throw Speed"; "Sword Damage" is now "Dagger Damage".
- **Ranger's melee dagger-fallback is now the Combat Pin (infinite ammo), a fired sidearm.**
  Tiny range (~0.75 tiles), very fast fire rate (~100ms cooldown) -- its own cooldown, fully
  independent of the bow's, so running out of arrows turns into genuine rapid fire instead of
  inheriting the bow's slow rate. No knockback at all. A lot smaller than the Dagger. Usable
  automatically once arrows run out, or manually from hotbar slot 3 even with arrows left --
  slot 3 no longer auto-deselects back to the weapon slot after firing, since re-selecting it
  before every ~100ms shot would make it unusable.
- **No more melee hit-resolution at all.** `GamePanel.meleeAttack()` is gone; the Dagger, the
  Combat Pin, and arrows are all resolved as projectiles now, including the two new weapons'
  self-imposed max range (`Projectile.maxRange` / `traveledDistance()` -- new fields, default
  unbounded so arrows and enemy shots are unaffected).
- The idle weapon rig beside the character no longer shows a rotating swing arc for the
  Dagger/Combat Pin -- it's a quick forward recoil flick now, matching "thrown", not "swung".
- Version bumped to v1.0.7.

## v1.0.6

### Added
- **New Game now asks before it can throw away an existing save.** Picking "Play" from the
  main menu with a save on disk opens a confirmation: "Use previous save" (loads it, same
  path as "Load Save") or "Start fresh" (deletes it, then goes to Character Select as normal).
  Defaults to the non-destructive choice. No save on disk -> goes straight to Character
  Select like before, nothing changes for a first-time player.

### Changed
- **Weapon/armor tier icons now show their tier as a colored fill behind the glyph**, not just
  a tinted glyph. New shared 5-tier palette (`WeaponTier`/`ArmorTier`): T1 green, T2 orange,
  T3 silver, T4 gold, T5 cyan. Applies to the hotbar's weapon slot (sword or bow) and dagger
  slot (shares the weapon tier, since dagger damage derives from it), and to the buff bar's
  3 armor-piece icons. `ArmorTier.NONE` (not yet purchased) stays neutral gray, outside the
  T1-T5 palette.
- Version bumped to v1.0.6.

## v1.0.5

### Changed
- **Every boss kill now makes all future enemies 18% tankier, compounding and uncapped**
  (`WaveManager.bossKillHpMultiplier()`, applied in `Enemy.scaledHealth()`). This stacks on
  top of the existing wave-scaled HP (+10/wave) and boss HP multiplier (5x), and on top of the
  separate triangular damage-per-boss-kill bonus that already existed -- the two boss-kill
  scalers are independent (HP compounds, damage grows triangularly and caps at wave 51).
  Deliberately left uncapped: players are expected to keep pace through the same boss kills
  via Armored's stacking Defense, Grow's per-wave Max HP, and their own enchant/upgrade
  investment, rather than this being tuned to stay flat on its own.
- Version bumped to v1.0.5.

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
