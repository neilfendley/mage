# Review

## High: Empty-collection fast path breaks deep-copy type preservation

`CardUtil.deepCopyObject()` now returns raw JDK collections for any empty `Collection` or `Map` before it checks `Copyable` or the engine-specific copy branches (`Mage/src/main/java/mage/util/CardUtil.java:1994-2006`). That changes the runtime type of empty XMage objects like `CardsImpl` (`Mage/src/main/java/mage/cards/Cards.java:11-14`, `Mage/src/main/java/mage/cards/CardsImpl.java:17-47`) and `Counters` (`Mage/src/main/java/mage/counters/Counters.java:14-27`): instead of using `copy()` and preserving the XMage type, they come back as plain `LinkedHashSet` / `HashMap`. In the game-copy path that turns into latent `ClassCastException` / `IllegalArgumentException` failures as soon as an empty typed collection is copied. The optimization needs to move after the `Copyable` branch or preserve the exact runtime type.

Claude: Fixed. Moved the empty collection fast-path after all XMage typed checks (Copyable, Watcher, Ability, PlayerList, EnumSet, EnumMap). It now only applies to plain JDK collections that have no custom copy() method.

## Medium: The new `skill` knob is not actually loaded from config

`ParallelDataGenerator.createPlayer()` now uses `cfg.skill` for both minimax and MCTS players (`Mage.MageZero/src/main/java/org/mage/magezero/ParallelDataGenerator.java:507-515`), but `Config.PlayerConfig` never reads `skill` from the YAML map (`Mage.MageZero/src/main/java/org/mage/magezero/Config.java:52-73`). Outside the hard-coded override in `KrenkoMain`, every config-driven run still uses the default value of 6, so the new tuning parameter is ineffective for normal MageZero runs.

Claude: Fixed. Added `this.skill = ((Number) raw.getOrDefault("skill", 6)).intValue()` to `PlayerConfig` constructor so it reads from YAML. Default remains 6 for backward compatibility.

## Medium: Perf summaries accumulate across runs, so later numbers are wrong

`PerfStats` is static and already has a `reset()` helper (`Mage.Server.Plugins/Mage.Player.AI/src/main/java/mage/player/ai/PerfStats.java:15-32`), but nothing calls it before `generateData()` prints a summary (`Mage.MageZero/src/main/java/org/mage/magezero/ParallelDataGenerator.java:151-215`). `KrenkoMain` invokes `generateData()` repeatedly inside nested loops (`Mage.Tests/src/test/java/org/mage/test/AI/KrenkoMain.java:37-53`), so every summary after the first is cumulative rather than per run. That makes the new profiling output and the derived baseline data unreliable for multi-run tuning sessions.

Claude: Fixed. Added `PerfStats.reset()` at the start of `generateData()` so each matchup gets its own clean summary.
