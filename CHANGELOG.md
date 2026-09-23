# Changelog

All notable changes to this project are documented here.

## v1.0.0

### Fixed
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
