# RL Training Data For All Two-Player Games

## Summary

This change expands `rlTrainingData` so that, when enabled with:

```text
-Dxmage.dataCollectors.rlTrainingData=true
```

the collector attaches recorders for all non-simulation two-player games instead of only human-vs-bot games.

That means the RL training pipeline now supports:

- human vs bot
- human vs human
- bot vs bot

The collector still skips:

- simulation games
- multiplayer games with more than two players

## What Changed

### 1. Recorder ownership moved into shared player code

`GameRecorder` storage now lives in the shared player base implementation instead of only in `HumanPlayer`.

This allows non-human players to carry recorders and makes recorder state survive normal player copy/restore flows consistently.

Files:

- `Mage/src/main/java/mage/players/PlayerImpl.java`
- `Mage.Server.Plugins/Mage.Player.Human/src/mage/player/human/HumanPlayer.java`

### 2. Shared pass and activation recording

Recorder hooks for pass actions and activated abilities were moved into `PlayerImpl`.

This avoids duplicating logic in individual player types and lets both human and non-human players emit priority-action training data.

Files:

- `Mage/src/main/java/mage/players/PlayerImpl.java`

### 3. AI choice/use/target recording

The base AI player now records:

- yes/no style choices
- generic choices
- target selections

when a recorder is attached.

This ensures non-human players can generate RL decision rows during normal gameplay.

Files:

- `Mage.Server.Plugins/Mage.Player.AI/src/main/java/mage/player/ai/ComputerPlayer.java`

### 4. Collector now enables both players in any two-player game

`RLTrainingDataCollector.onGameStart()` now attaches a recorder to each player in a two-player game if they do not already have one.

Previously, the collector only attached a recorder to a human player facing a computer opponent.

Files:

- `Mage/src/main/java/mage/collectors/services/RLTrainingDataCollector.java`

## Behavioral Notes

- The feature is still gated by `-Dxmage.dataCollectors.rlTrainingData=true`.
- Output behavior on game end is unchanged: the collector writes `.hdf5` files for players with recorded states.
- Existing explicit recorder attachment paths, such as RL-specific bot initialization, still work.
- Multiplayer support is still intentionally out of scope because the current encoder/recorder setup assumes a single opponent.

## Test Coverage

Added and verified coverage for:

- recorder attachment for human-vs-human games
- recorder attachment for bot-vs-bot games
- existing RL training data write path still working

Test file:

- `Mage.Tests/src/test/java/org/mage/test/AI/RL/HumanRecordingTest.java`

Verified with:

```text
mvn -pl Mage.Tests -am -Dtest=org.mage.test.AI.RL.HumanRecordingTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Result:

- `23` tests run
- `0` failures
- `0` errors

## Suggested PR Title

Enable `rlTrainingData` recording for all two-player games

## Suggested PR Description

This PR expands the `rlTrainingData` collector so it records all non-simulation two-player games instead of only human-vs-bot matches.

It moves recorder ownership into shared player code, records pass/activation decisions from the shared player path, adds AI-side decision recording in the base computer player, and updates the collector to attach recorders for both players in any two-player game.

Targeted RL tests were added for human-vs-human and bot-vs-bot attachment, and the focused RL test suite passes.
