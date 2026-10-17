Optional custom sound effects
-----------------------------
The game makes all its sound effects itself (see src/laststand/fx/Sfx.java), so this folder can stay empty.

To replace one with a recording, put a PCM WAV here named after the effect, lowercase:

  menu_move  menu_select  shoot_arrow  throw_dagger  pin_stab  hit_enemy  enemy_death  player_hurt
  potion  shield_on  buy  error  level_up  ultimate_ready  giant_arrow  thorns_on  thorns_pulse
  wave_start  boss_warn  wave_clear  game_over

e.g.  src/sounds/hit_enemy.wav

Any sample rate, mono or stereo, 8- or 16-bit PCM is fine (it's converted automatically). MP3 and OGG are NOT
supported by Java's built-in audio -- export WAV. Keep clips short. Rebuild with build.sh / build.bat so they
get packed into the JAR. A file that can't be read is skipped and the built-in sound is used instead.

Editing the built-in sounds instead
-----------------------------------
You don't have to record anything: Sound Test (Ctrl+S) -> select a sound -> press E opens the in-game SFX Editor, which
changes the synthesized sound and saves it to laststand_sfx.txt. (If a WAV for the same sound is in this folder, the WAV
is used instead of the edit.)
