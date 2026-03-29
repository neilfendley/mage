# Review

## Medium: `gameCopy` is still underreported because `stateEncode` includes non-MCTS calls

The new perf summary subtracts `stateEncode` from `validateState` to derive `gameCopy` in [PerfStats.java](/Users/lawrencehook/github/mage/Mage.Server.Plugins/Mage.Player.AI/src/main/java/mage/player/ai/PerfStats.java#L44) and [PerfStats.java](/Users/lawrencehook/github/mage/Mage.Server.Plugins/Mage.Player.AI/src/main/java/mage/player/ai/PerfStats.java#L46). That only works if every counted `processState()` call happens inside MCTS `validateState()`, but this branch also counts minimax-side encoding:

- [ParallelDataGenerator.java](/Users/lawrencehook/github/mage/Mage.MageZero/src/main/java/org/mage/magezero/ParallelDataGenerator.java#L410) wires `ComputerPlayer8` to use the opponent `StateEncoder`.
- [ComputerPlayer8.java](/Users/lawrencehook/github/mage/Mage.Server.Plugins/Mage.Player.AI.RL/src/mage/player/ai/ComputerPlayer8.java#L147) calls `encoder.processState(game, playerId)` while logging minimax actions.
- [StateEncoder.java](/Users/lawrencehook/github/mage/Mage.Server.Plugins/Mage.Player.AI/src/main/java/mage/player/ai/encoder/StateEncoder.java#L603) counts those calls in the same global `stateEncode` totals.

Those minimax logging encodes are not nested inside MCTS `validateState`, so subtracting the full global `stateEncode` total from `validateState` still understates `gameCopy` and makes the checked-in baseline analytically unreliable. To make the breakdown correct, the profiler needs separate counters for:

1. `stateEncode` calls reached from MCTS `validateState`
2. `stateEncode` calls reached from minimax / recorder logging

or it needs to stop deriving `gameCopy` by subtracting a global aggregate.

Claude: Fixed. Removed the derived `gameCopy` metric entirely. `validateState`, `evaluate`, `expand`, and `other` now sum to 100% without any subtraction. `stateEncode` is shown as a separate informational line with a note that it partially overlaps with `validateState`. No more synthetic metrics.
