# Last Stand — v1.1.5

See [CHANGELOG.md](CHANGELOG.md) for what changed in this release.

A local 2-player, wave-survival game built in pure Java (Swing/Java2D — **no
external libraries**, so there's nothing to download or configure;
`javac`/`java` from any JDK 17+ is enough).

## Run it

**Easiest -- the prebuilt executable JAR** (needs only a Java 17+ runtime; nothing to install otherwise):

```
java -jar release/LastStand-v1.1.5.jar
```

On most systems you can also just double-click the `.jar`. Save files (`laststand_save_solo.dat`,
`laststand_save_coop.dat`) are written next to where you launch it from.

**Build it yourself** (any JDK 17+; the repo has no dependencies):

```
./build.sh          # Linux / macOS         -> LastStand.jar
build.bat           # Windows               -> LastStand.jar
```

or by hand:

```
javac --release 17 -d out -sourcepath src src/laststand/Main.java
java -cp out laststand.Main
```

(Or just open the folder in IntelliJ/VS Code and hit Run on `Main.java`.)

**A native Windows `.exe`?** A JAR is the portable executable. A real `.exe` has to be built *on Windows*
with the `jpackage` tool that ships with JDK 17+ (it can't cross-build), e.g.:

```
jpackage --type app-image --name LastStand --input . --main-jar LastStand.jar --main-class laststand.Main
```

That makes a self-contained `LastStand\LastStand.exe` folder (no Java needed on the player's machine);
add `--type exe` (needs the WiX toolset) for an installer.

## Controls

| Action                       | Player 1        | Player 2         |
|-------------------------------|-----------------|------------------|
| Move                          | W A S D         | Arrow keys       |
| Attack / use selected item    | SPACE (tap — holding does **not** auto-repeat) | ENTER (same) |
| Select hotbar slot 1 (weapon) | 1               | 0                |
| Select hotbar slot 2 (Healing Potion) | 2       | 9                |
| Select hotbar slot 3 (shield) | 3               | 8                |
| Select hotbar slot 4 (secondary — Ranger's Combat Pin) | 4 | 7 |
| Open shop, or enchant if near the Enchanting Center | E | P |
| Enchant wheel spins (Default / 30 / 90 / 270 / 450 tier, cheapest to priciest) | 1 2 3 4 5 | 7 8 9 0 - |
| Toggle fullscreen             | F11 or Alt+Enter (works on every screen) |
| Sound on / off                | F9 (works on every screen; shows a small "Sound: ON/OFF" note) |
| Music on / off                | F10 (only matters once a song is playing; F9 mutes it too) |
| **Sound Test** (secret)       | **Ctrl+S** from any screen -- see "Sound Test" below |
| Menu confirm                  | SPACE or ENTER (either works, either player) |
| Menu navigate                 | W/S or Up/Down (either works, either player) |
| Open/close the Settings menu (was instant quit-to-menu — now opens How to Play / Back to Main Menu, the latter behind a Yes/No confirm) | ESC | ESC |

**Inventory hotbar**: pressing a number selects that slot (shown highlighted in
the HUD); your next attack-key press *uses* whatever's selected, then
automatically snaps back to slot 1 (weapon) so you don't have to remember to
switch back before you can attack again — except slot 4, which stays selected
after firing (see below). Slot 1 is just your normal attack.
Slot 4 only does anything for the Ranger (fires a Combat Pin on demand,
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

**Saving.** ESC → **Save Game** writes a save file next to wherever the game
runs from; main menu → **Load Save** restores it (after a Yes/No
confirmation showing what's in it). Solo and 2-player runs each get their
own separate save file (`laststand_save_solo.dat` / `laststand_save_coop.dat`)
-- a solo save can only ever be loaded into a solo session, a 2-player save
only into a 2-player session; which file every menu action reads/writes is
picked by the P1/P2 toggle on the main menu at that moment, not anything
recorded in the save itself (see `SaveManager`'s class doc). A save holds
both players' class, LVL/EXP, weapon/armor tier, upgrades, enchants, Healing
Potion (owned + charges), shield unlock, wallets, and wave progress -- but deliberately *not*
the arena theme or the "pause during shop/enchant" option (those are
session/display settings, not run progress, so loading never overrides
whatever you currently have them set to), and *not* position, current HP,
or live enemies: loading always resumes at the start of the saved wave at
full health. To discourage save-scumming (reloading to undo a bad enchant
roll or a risky fight), a save is a **one-time checkpoint** -- loading
deletes it -- and **Save Game is greyed out
for 5 waves after a load**. **Dying destroys that mode's save** (game over =
every player down), even one you made earlier in the same run; a solo death
never touches a 2-player save or vice versa, and the game-over screen says so
when it happens. Saves from older versions may be unreadable (retired perks
in an old save are simply dropped on load; old Medic Kit counts aren't
carried over).

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
same Attack-Defense formula (`Player.armorDefense()`,
folded into `takeDamage()`). The buff bar at the bottom of the screen shows 3
small armor-piece icons next to the upgrade icons, tinted by the player's
current tier, so a purchase is visible immediately without opening the shop.

## Upgrade system

8 perks, each leveling independently 0-20, bought with yellow orbs (1 per
level) from a row of 3 random options at the bottom of each player's shop
column. All levels reset to 0 on a new game. Every purchase re-randomizes all
3 offers (so stacking the same perk needs a lucky reroll each time); manually
rerolling costs 20 LVL and is gated behind reaching LVL 20 in the first place
(`Player.REROLL_LEVEL_REQUIREMENT`) -- the shop shows "requires LVL 20" in
place of the reroll line until then.

| Perk | Effect |
|---|---|
| Speedy | +6% move speed / level (was +10% -- nerfed 40%; Lv20 is x2.2, not x3.0) |
| Innate Talent | (renamed from Recovery, reworked) Tank: restores 1% of Max HP per enemy slain, +1% per level (20% at Lv20). Ranger: 3% chance per level that a shot doesn't use an arrow (60% at Lv20) |
| Critical | +3% chance to double a hit / level -- same mechanic for both classes |
| Preserved Power | +1 Max HP AND +1 damage per 20 LVL held (milestones at 20,40,...,500), per level -- unified interval/increment for both stats now. Rises/falls live as LVL is gained or spent. Capped at +500 Max HP / +500 damage (level 20, LVL 500) |
| Dark Sorcery | +1-4 bonus dark orbs per wave cleared, scaling to +20-80 at level 20 |
| Silver Bank | +1-2 bonus silver orbs per wave cleared, scaling to +20-40 at level 20 |
| Reforged | +2 flat damage per wave cleared, per level (uncapped, stacks forever, applies to every attack) |
| Rechargeability | -1 charge needed for your ultimate per level (40 -> 20 at Lv20) |

Armored and Grow were removed in v1.1.5. Rechargeability took one of those two
slots; the other is intentionally vacant for now. (They remain in `UpgradeType`
as hidden "retired" constants purely so older save files still deserialize --
never offered, shown or applied; use `UpgradeType.active()` for anything
player-facing.)

**Attack - Defense combat formula**: whenever a player takes a hit,
`finalDamage = max(1, Attack - Defense)`. `Player.totalDefense()` is now the
single source of truth for Defense (armor gear + the Protection enchant's
percentage) — the HUD's `DEF` readout used to
only show the armor-gear portion, which made it look like upgrades and
enchants weren't doing anything; it now shows the real total. Defense is
checked in `Player.takeDamage()`, the single entry point for anything
hitting a player (melee contact and enemy projectiles alike). HP Boost no
longer needs special handling there — its bonus lives directly inside
`maxHealth` (see the Enchanting section below), so a hit just comes off
real HP like normal.

Dark Sorcery/Silver Bank/Reforged bonuses are all personal (only the
buyer's own wallet/stats), on top of the flat +10 dark orbs both players
always get per wave.

(Historical, pre-v1.1.5:) Melee Power, Toughen, and Dodge were reworked into Recovery, Grow, and
Reforged respectively -- Melee Power's old flat damage bonus is gone (that
role now belongs to weapon tiers and Preserved Power), Toughen's one-time %
HP grant became Grow's recurring per-wave HP gain, and Dodge's evasion chance
became Reforged's recurring per-wave flat damage bonus. Critical was
originally a distance-scaled bonus for the Ranger, but it barely moved the
needle in testing, so it's the same 3%/level double-damage chance as the
Tank's version, for both classes.

## What's implemented

- Main menu → character select (Tank/Ranger, duplicates allowed) → play loop → game over
- Cross-shaped arena (55×45 tiles of playable area; odd sizes keep the cross exactly
  symmetric -- the horizontal arms are longer than the vertical ones) on a **floating island**: an irregular rock border (at least 3 blocks
  thick on every side, up to 6, rounded corners, textured stone with cracks and
  highlights; sandstone in the Desert theme) wraps the playable area, with a jagged
  rocky underside hanging below and sky-blue open air with slow blocky clouds
  beyond it. The border is solid -- nothing can walk or shoot through it. Inside:
  flat single-color obstacle tiles (no
  bevel/border — that was reworked away for looking too "blocky"), ~5% of
  which get a small grass-tuft (Forest) or cactus (Desert) decoration doodle,
  rolled once at generation so it doesn't flicker as the camera pans. A
  portal at the end of each of the 4 arms, a center **sentry** block + dashed
  **trade zone** ring. Forest/Desert themes, toggle with T from Options
- EXP bar under each player's HP bar, capped at 50 ("50/50") per level -- a
  full bar rolls over into +1 LVL rather than just capping there, up to a
  hard cap of LVL 500 (the bar just sits full past that point). LVL gates
  the upgrade reroll (needs LVL 20) and is spent on enchant wheel spins
- Bottom-of-screen buff bar (P1 bottom-left, P2 bottom-right) showing every
  active upgrade level at a glance, same quantity-badge style as the inventory hotbar
- Beginner wave curriculum for waves 1-15 (fixed enemy pools per your spec),
  fully random pool from wave 16 on. Each **portal** is locked to one enemy type
  for the whole wave (fixes the old "one portal spawning 2+ types" bug) — with
  multiple portals open you'll see different types from different portals, e.g.
  wave 24's 3 portals each spawning a different type
- All 8 enemy types with a simple pixel face each, wave-scaled health (+10/wave)
  and damage (flat +2/wave for every type), +10% speed every 5 waves
- Boss rounds every 5th wave: 5× health, 2.5× damage, knockback-immune, regular
  speed for their type (the old 0.5× slow was removed in v1.1.5), and each boss
  rolls a **boss special** (see "Wave scenarios & boss specials" below)
- Every boss defeated makes ALL future enemies 11% tankier (compounding, uncapped) -- on top of
  the existing damage-per-boss-kill bonus. This is meant to be offset by the player's own
  progress through the same boss kills: Reforged's per-wave damage,
  and whatever armor/enchants/upgrades a player has invested in, not by capping the multiplier itself
- Weight-based knockback for players and enemies; knockback now routes through
  the same collision system as normal movement so nothing gets shoved into a wall
- **Tank**: throws an infinite-ammo Dagger (flips with facing), green when ready /
  red while on cooldown, a quick forward flick on attack — no melee swing anymore.
  Short range (~3.0 tiles, dissipates on its own), ~400ms cooldown, small knockback,
  a bit smaller than an arrow. Damage scales with weapon tier
- **Ranger**: bow beside the character (same color/cooldown behavior), fires
  ruby-red projectiles while it has arrows; automatically switches to firing
  a Combat Pin (20% of current effective bow damage) when out of
  arrows -- scales down with the bow instead of falling further behind it as
  the bow gets upgraded. The Combat Pin is also a fired projectile (not a melee
  swing): tiny range (~1.75 tiles), a very fast ~100ms cooldown fully independent
  of the bow's, no knockback at all, and it can be fired on demand from hotbar
  slot 4 even with arrows left. No more passive ammo regen — arrows are bought from
  the shop
- Passive healing: Tank +3 HP/sec, Ranger +1 HP/sec (plus Innate Talent / Regen)
- **Per-player economy**: separate wallets for P1/P2. Dark orbs (+10 to *both*
  players per wave cleared), silver orbs (33% chance, credited to whoever got
  the kill), yellow orbs (boss kills, or traded from 100 dark / 20 silver)
- **Shop** (press E): two independent columns, one per player. Weapon tier
  upgrades (Wooden → Flint → Iron → Golden → Diamond, up to +100% damage),
  arrows (10 for 1 dark orb, Ranger only), the Healing Potion (below),
  Shield (unlock for 5 silver orbs, then 20s cooldown between activations --
  nullifies all damage and knockback while active, contact or projectile),
  and a full-set Armor upgrade (see the Armor section above). Enchanting is
  fully implemented (see its section)
- Floating damage numbers appear over a hit enemy on every player attack --
  normal hits fade in 0.5s; a Critical proc is bigger, flashes yellow/orange,
  and fades over 1.5s instead, Terraria-style
- Game over: resets wave to 1 and **both wallets to zero**; a clickable
  "BACK TO MENU" button as well as SPACE/ENTER

## Ultimates (v1.1.5)

The box beside the hotbar's 4th slot is the **ultimate**. It needs **40 charge** -- 1 per enemy you
slay (so 40 kills), less 1 per Rechargeability level (20 at Lv20). While charging, its icon is blurred and
a colored bar fills upward inside it; once full the icon turns crisp and crackles with electricity. **Tap the
weapon key 3 times quickly** (all within 0.8 s, with the weapon slot selected) to fire it. Kills made *by* an
ultimate never recharge it (they still pay EXP/orbs as normal). Charge is saved with the game.

- **Ranger -- Giant Arrow.** A huge arrow with its own sprite, auto-aimed at the *nearest* enemy no matter which
  way you face. Moderate speed (7.5, vs 9 for a normal arrow), pierces every enemy it touches (each once, along
  its whole 84 px body), **no knockback**, flies **through walls** until it leaves the map, and hits for **150%** of
  your current arrow damage (no crit roll). Doesn't fire (and doesn't spend the charge) if there's nothing to hit.
- **Tank -- Thorns.** For **10 s** a red shield with 6 dark-green spikes bulges in and out around you. You take
  **80% less damage** from everything (applied to the *whole* hit, so a Savage hit's %-of-Max-HP bonus is cut too;
  the regular Shield stays the only full block). Every 0.5 s, as the spikes reach full extension, each enemy
  touching the aura takes **50% of your current damage (no crit) + 10% of its own Max HP** and is **knocked back**
  (bosses take the damage but are knockback-immune). While Thorns runs the regular Shield's timers are frozen
  (both its active time and its cooldown resume afterwards) and it can't be activated on top.
  While Thorns is up the ultimate box drains as a green bar to show the time left.

Code: `Player` (charge, triple-tap buffer, Thorns state, shield clock), `GiantArrow`, `fx/UltimateArt` (all the
drawing), and `GamePanel.activateUltimate / updateGiantArrows / updateThorns`.

## Sound (v1.1.5)

All the sound effects are **synthesized in code** -- no audio files -- in an 8-bit chiptune style (square, triangle,
sawtooth and noise voices with pitch slides, vibrato and 8-bit quantization), played through a small software mixer
(`fx/Sfx.java`). 21 effects: menu blips, bow / dagger / combat-pin, hit and death, getting hurt, potion, shield,
shop buy / error buzz, level-up, ultimate ready / Giant Arrow / Thorns on + pulse, wave start, boss warning, wave
cleared, and game over. Rapid repeats are rate-limited so a crowd getting hit doesn't turn into noise. **F9** mutes, **F10** turns the background music off (see Controls).
If the machine has no usable audio device the game just stays silent.

Each effect is a short "recipe" at the top of `Sfx.java` -- pitch, length and loudness are plain numbers, so
retuning one is a one-line edit. To use a recording instead, drop a WAV named after the effect into `src/sounds/`
(see the README there; MP3/OGG aren't supported by Java's built-in audio -- export WAV).

### Sound Test (secret screen)

Press **Ctrl+S** on any screen -- main menu, mid-wave, shop, game over -- to open a hidden soundboard, in the style of
MyInstants: one big glossy round button per sound effect, labelled with its name and colour-coded by group (Menu blue,
Combat red, Items & Shop amber, Ultimates purple, Waves green). **Click a button to play it.** Ctrl+S or ESC closes the
screen and puts you straight back where you were; the game is frozen while it's open.

| Input | Does |
|---|---|
| Mouse click on a button | play that sound (the button squashes, sends out a ripple, and a ring sweeps around it while it plays) |
| Arrows / WASD, then SPACE or ENTER | the same, from the keyboard (a dashed ring marks the keyboard selection) |
| R | play all 21 in order, one after another (R again to stop) |
| L | loop songs on/off (or click the LOOP pill beside the title): a song then repeats seamlessly until you press X |
| X | stop / silence everything |
| E | with any sound selected: open its editor (or click the EDIT pill): the **SFX Editor** for sound effects, the **Music Editor** for "??? 2". The first "???" is the original track and can't be edited |
| F9 | sound on/off (a red banner reminds you when it's off) |

The strip along the bottom describes the selected effect: where in the game it plays, its length, peak level, a
**waveform with a moving playhead**, whether it's synthesized or a WAV override from `src/sounds/`, and its in-game rate
limit (the soundboard ignores the rate limit so you can mash a button and always hear it). It plays through the same
mixer and the same samples as the game, so what you hear is exactly what the game plays. A new `Sfx.Id` gets a button
automatically; give it a line in the `INFO` table of `ui/SoundTestUI.java` to name, colour and describe it.

### SFX Editor (change any sound effect inside the game)

Select any sound effect on the Sound Test and press **E** (or click **EDIT SOUND**). Every effect is a short stack of
**tones** -- layers of square, triangle, saw, noise or smooth air -- and the editor shows that stack: the list on the left is the
layers, the panel on the right edits the selected one, and the strip on top is the exact waveform that will play (the selected
layer's stretch of time is highlighted).

| Setting | What it does |
|---|---|
| WAVE | square (with PULSE WIDTH: thin and reedy to full), triangle, saw, noise (hiss; NOISE SHORT is the metallic kind) or air (smooth whoosh) |
| STARTS AT / LENGTH | when the layer begins and how long it lasts (5 ms steps) |
| PITCH AT START / END | the pitch slides from one to the other, in semitone steps (shown with the nearest note name) |
| VOLUME, FADE-OUT SPEED, FADE-IN | loudness and the shape of each layer |
| VIBRATO SPEED / DEPTH | a wobble on the pitch |
| HISS RATE / AIR CENTRE / BAND SHARPNESS | the same pitch controls for the noise and air waves |
| ADD TONE / DUPLICATE / DELETE | build the sound out of more or fewer layers (up to 40) |
| 8-BIT CRUNCH | on = the retro 8-bit rounding; off = smooth (used by the Dagger throw) |

Hold a **-** or **+** to repeat. **Space** plays the sound as it is now (unsaved); **AUTO-PLAY** also plays it after every change.
**Save** (Ctrl+S) writes `laststand_sfx.txt`, and from then on **the whole game** plays your version of that sound -- hits,
shots, shop, menus, everything -- not only the Sound Test. **Discard** goes back to the last saved version, **RESET TO ORIGINAL**
brings back the built-in sound (saving that removes the edit), **Ctrl+Z** undoes, and leaving with unsaved edits asks first.

`laststand_sfx.txt` is a small text file with one `[SECTION]` per edited sound, so it can be backed up or shared; delete a section
(or the file) to get the built-in sounds back. If a recorded WAV for a sound is in `src/sounds/`, the WAV still wins in the game
(the editor warns you), so remove that file to hear your edit.

### Music Editor (edit the "??? 2" track inside the game)

Select **"??? 2"** on the Sound Test and press **E** (or click **EDIT TRACK**). It opens a piano roll for the hidden
workshop tune -- no code, no rebuild. Nothing changes in the game until you **Save**; **Discard** throws your edits away.

| Do this | To |
|---|---|
| Click an empty cell | add a note (drag right to stretch it as you click) |
| Drag a note / drag its right edge | move it (time and pitch) / change its length |
| Right-click, or Del | remove a note |
| Arrow keys (with a note selected) | nudge it a step / up or down a semitone (Shift = octave) |
| **1-5**, or the NEW NOTE LENGTH buttons | length of the next notes you add: 1/16, 1/8, 1/4, 1/2, a whole bar |
| Chord row: **‹ ›** and the middle | move the bar's chord root by a semitone / switch major <-> minor (bass and arpeggio follow it) |
| TEMPO, BARS, DRUMS COME IN AT, FULL SOUND AT | speed, length of the tune, and the bars where the drums and the fuller melody sound start (hold + / - to repeat) |
| **Ctrl+A** / **Shift+click** / **Shift+drag** | select **several notes**: Ctrl+A takes them all, Shift+click adds or removes one note, Shift+drag on an empty spot draws a selection box. Then **drag any selected note**, or press the **arrows** (Shift+Up/Down = an octave, Shift+Left/Right = a bar), and they all move together, keeping their spacing and stopping when one reaches an edge. **Del** removes them all, **Esc** deselects, and one **Ctrl+Z** undoes the whole move |
| **G** (or the **SNAP** button) | the **grid** notes snap to when you place or move them, separate from the note length: **1/16** (the default: any note can start on any sixteenth, so a 1/4 or 1/2 note can sit on an off-beat), **1/8**, **1/4**, or **AUTO** (the old rule: a note snaps to its own length, at most a beat). The arrow keys nudge by the same grid |
| **F** (or the **CHORDS FOLLOW** pill) | with it **ON** (the default), when **all** notes are selected (Ctrl+A) and moved, the **chords go with them**: moving up/down transposes every chord by the same number of semitones, and moving by whole bars (Shift+Left/Right, or dragging) slides the chords along by that many bars (a bar left with nothing becomes "none"; a chord pushed off the end is gone until Ctrl+Z). Moving only part of the tune leaves the chords alone, because the notes left behind still need them. One Ctrl+Z undoes notes and chords together |
| **Ctrl+Up** / **Ctrl+Down** | move **every chord** up or down a semitone. Chords and notes are separate (moving notes never changes the chords, and the chord cells below only recolour the notes that fit), so this is how to shift the chords with the notes |
| **Right-click** a chord cell | **no chord** for that bar: no bass and no arpeggio (the drums still play). Click the cell again to bring its chord back; in the file it's written `-`, e.g. `chords=C - Am F` |
| **T** (with a note selected) | **tie** it to the note touching it on its left: it then carries that note on with no new start (T again to untie) |
| **Space** / **L** | play or stop the tune you're editing (unsaved) / loop it |
| Wheel, Shift+wheel, PgUp / PgDn, the SONG strip | scroll pitch / bars / by 4 bars / jump anywhere |
| **Ctrl+S** or SAVE | save |
| DISCARD | go back to the last saved version |
| **Ctrl+Z** / UNDO | undo (even a discard) |
| **Esc** or BACK | leave; if there are unsaved edits it asks: save, discard, or keep editing |

The melody is **one voice**, like a real NES square channel: a new note replaces whatever it lands on, and a note can't
cross a bar line (use two notes, and press **T** on the second one to tie it: the sound carries on across the line). Rows tinted green are the notes that fit the bar's chord -- a handy guide.

**Saving** writes `laststand_workshop_track.txt` next to the game (the same place as the save files); from then on
"??? 2" plays your version. It's a small text file -- one `barN=E5 1, G5 .5, R .5 ...` line per bar (`R` is a rest, `T 1.5` is a tie: it holds the note before it for 1.5 more beats) -- so it can also be
edited by hand, backed up, or shared. **Delete the file** to get the original starter tune back (RESET TO STARTER in the
editor does the same thing as an edit you can still save or discard). A broken file is ignored with a note on the console.


**Pasting bars from code into the tune file.** Bars can be pasted the way they look in code: quotes, a trailing comma, a
`// comment` and curly quotes are all ignored, and lines without `bar1=` are taken as the next bar in order (so a tune file
can be just the bar lines). The melody and the chords are separate lines: pasting bars changes only the melody, so also set
`chords=` (and `bpm=`), or the chords stay as they were. A line that really can't be read isn't skipped quietly any more:
the Music Editor and the "??? 2" button in the Sound Test say what's wrong, and when `bpm=` or `chords=` is missing they
say what was assumed (96 BPM / every bar C).
## Healing Potion (replaced the stackable Medic Kit in v1.1.5)

One rechargeable flask with **3 capacity bars** (shown on hotbar slot 2). It heals **25% of
Max HP** per use, has a **5-second cooldown** between uses, and is never wasted at full health.
Buy it once for **3 silver orbs** (starts full, 3/3); it regains **+1 charge each time a wave is
cleared**, never above 3. While it isn't full, the shop also offers a 3-silver refill.

## Wave scenarios & boss specials (hidden in-game)

Waves 1-5 play out as classic. From wave 6, every non-boss wave quietly rolls a scenario --
nothing on screen tells the player which (run with `-Dlaststand.debug=true` to log it):
Swarm (30 fragile fast enemies, 3 portals), Heavy Hitters (8/15 slow hard hitters, 1-2
portals), Standard (20 classic-stat enemies), Power Rangers (forces 3 portals: 20 skeletons +
10 blaster golems, 10% on every wave), and from wave 26: Savages (2 portals, 40% there) and
Ghastlies (1-2 portals, 8%). Savage enemies hit for their normal damage **plus** 16% of the
victim's Max HP (25% for a Savage boss) -- the % part ignores Defense, the Shield still
blocks it all -- and Savage shooters (skeleton / blaster golem variants) fire purple bullets
75% faster. After a Savage/Ghastly-type hit lands, further hits of that kind are ignored for
0.5 s (`Player.HYBRID_HIT_IMMUNITY_MS`) so a bunched-up pack can't land every strike in the
same frame; damage per hit is unchanged. Ghastlies are almost invisible, knockback-proof, and hit for raw + 3% Max HP.
Odds and counts live in `wave/Scenario.java`.

Every boss wave rolls one special, named in yellow above the boss: **Miniature** (tiny, fast,
speed 60% of the player's +1% per boss defeated, capped at the player's speed), **Armored**
(takes 80% damage), **Speedy**, **Strong** (2.5x damage on top of the boss multiplier),
**Sorcerer** (summons rings of 5/7/10 of its own type at 75/50/25% HP), and from wave 25
**Savage** (speed 60% of the player's +2% per boss defeated; 25% of boss rolls, 50% from
wave 50). See `wave/BossScenario.java`.

Enemies also navigate now: they only attack with a clear line to the target, and route
around the corner blocks (and reposition, if ranged) instead of pinning against a wall or
shooting into stone (`Arena.hasLineOfSight` / `Arena.waypointToward`).

## Display & fullscreen (v1.1.5)

The window is 1000×740. **F11 / Alt+Enter** toggles real fullscreen: the game is laid out at
that fixed design size and scaled to the monitor, and the HUD and world view stretch to fill
the extra width on widescreens (menus stay centered). The camera is always dead-centered on
the player (the midpoint of both in co-op) and never clamped to the map, so near the edge
you see the island's rock border, the sky and the clouds (`world/Sky.java`).

## Weapon Remake (v1.0.7)

Melee swings don't exist anymore, for either class. Both weapons are thrown/fired projectiles now:

| | Tank's **Dagger** | Ranger's **Combat Pin** |
|---|---|---|
| Replaces | the old melee sword swing | the old melee dagger-fallback |
| Ammo | infinite | infinite |
| Range | ~3.0 tiles, dissipates on its own | ~1.75 tiles, dissipates on its own |
| Cooldown | ~400ms (medium) | ~100ms (very fast) -- its own cooldown, fully independent of the bow's ~1000ms (slow, unchanged reference) |
| Knockback | small (~4, vs. the bow's ~8) | none at all |
| Projectile size | a little smaller than an arrow | a lot smaller than the Dagger |
| Damage formula | tier + Preserved Power + Dagger Damage enchant + Reforged, uncapped (an old 80-damage cap here was a bug -- Diamond's base alone is 110 -- fixed) | 20% of effective bow damage + Reforged, uncapped |
| Fires | on the normal attack key, slot 1 | automatically once arrows run out, or on demand from slot 4 even with arrows left |

The Combat Pin's cooldown is deliberately its own thing rather than reusing the bow's --
otherwise running out of arrows would either inherit the bow's slow rate (contradicting "very
fast") or force the shared cooldown down to 100ms game-wide (breaking potion/shield timing
too). Because of that, slot 4 no longer auto-deselects back to the weapon slot after firing —
at a ~100ms fire rate, having to reselect the slot before every shot would make it unusable.

The idle weapon rig beside the character now does a quick forward recoil "flick" for both the
Dagger and the Combat Pin, instead of the old rotating swing arc, since neither is swung anymore.

These are first-pass numbers, tuned by feel rather than by any spec -- all five constants for
each weapon live together in `Player.java` (`DAGGER_MAX_RANGE_PX`, `DAGGER_SPEED_PX`,
`DAGGER_KNOCKBACK`, `DAGGER_PROJECTILE_RADIUS`, and the matching `COMBAT_PIN_*` ones) if you
want to hand me different values.

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
  multiplier — Dagger: 50 (Wooden) / 60 / 75 / 95 / 110 (Diamond). Bow: 90
  (Wooden) / 105 / 130 / 165 / 220 (Diamond), exactly as specified. Both
  classes are 100 max HP.
- **Ranger's Combat Pin damage** is 20% of the Ranger's current *effective*
  bow damage (`arrowDamage()`, so it includes tier and Preserved Power), plus
  Reforged, uncapped. This was the flagged "possible throwable-darts
  rework" placeholder -- it happened: see the Weapon Remake section below.
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
| Select Dagger | V (Tank only) | V (Tank only) |
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

Entering the menu always starts on your one main weapon slot (Dagger for
Tank, Bow for Ranger) — pressing an armor key a **second time** (while that
same piece is already selected) toggles back to it too, so there's always an
obvious way back without hunting for V/M.

**It's a gamble, not a menu.** Each of the 5 gear categories (Helmet,
Chestplate, Leggings, Dagger, Bow) has its own pool of 3 enchant types and its
own *independent* enchant state — a helmet and a pair of leggings can have
completely different Regen levels, even though all 3 armor pieces still
share one unified `ArmorTier` for their raw Defense stat. The pools match
your spec exactly (Regen/Protection/HP Boost for every armor piece; Quick
Charge/Arrow Damage/Arrow Speed for bow; Throw Speed/Dagger Damage/Lucky
Strike for the Dagger), each with I/II/III amplifiers. (Internally the enum
still calls this category SWORD and the enchants SWING_SPEED/SWORD_DAMAGE --
kept as-is post-remake so old saves' enchant slots keep resolving; only the
displayed names changed.)

**Protection and HP Boost were reworked to be percentage-based** rather
than flat numbers. Protection now grants +10%/+25%/+50% of your *total*
Defense (armor gear, combined) rather than a
flat +2/+5/+10 — summed across every armor piece that has it socketed, then
applied once to the total. **HP Boost (v1.0.0 fix) now resizes your Max HP
bar directly instead of a hidden absorb-pool** — it adds +20%/+45%/+90% of
your *real* (un-boosted) Max HP as bonus Max HP, summed across every armor
piece that has it socketed. Your current HP is left exactly where it was —
58/100 rolling a 10% boost becomes 58/110, not 68/110 — so the bar visibly
grows and natural regen/the Healing Potion just fill the new headroom over time.
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

Every enchant effect is live in combat: Dagger/Arrow Damage add straight to
your hit, Throw Speed/Quick Charge shrink your attack cooldown (Throw Speed
does NOT touch the Combat Pin's own fixed cooldown -- see the Weapon Remake
section), Protection adds to Defense, Regen adds to passive healing, HP
Boost directly grows your Max HP (see above), Lucky Strike stacks its own
crit chance onto the Critical perk (Dagger throws only), and Arrow Speed
boosts both arrow velocity and the knockback it deals (the Dagger and
Combat Pin have their own fixed knockback, untouched by this enchant).

## Not yet built

A replacement for the vacant perk slot.
The Potion of Experience was dropped. Armor, the upgrade-perks shop, and
enchanting are all implemented (see the sections above).

## Project layout

```
src/laststand/
  Main.java
  core/    GameWindow, GamePanel (loop + state machine), InputHandler, Constants
  entity/  Entity, Player, PlayerClass, Enemy, EnemyType, Projectile, WeightClass, AttackType
  shop/    Wallet, WeaponTier, ArmorTier, Currency, ShopItem, ShopUI, UpgradeType,
           EnchantCategory, EnchantSpinOption, EnchantType, EnchantUI
  world/   Arena, Portal, TileType, ArenaTheme, Sky
  wave/    WaveManager, Scenario, BossScenario
  ui/      HUD, SoundTestUI
  fx/      DamagePopup
```
