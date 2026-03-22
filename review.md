# Review

Review scope: full diff from `master...HEAD` on branch `lh/human-game-recording`.

## Findings

1. Medium: `RLTrainingDataCollector` auto-enables recording for any human player who has an AI opponent, but the recorder pipeline still only models a single opponent. `onGameStart` picks the first AI opponent returned by `game.getOpponents(player.getId())` and passes only that one UUID into `GameRecorder.Factory.create(...)`. `StateEncoder` stores exactly one `opponentId` and always encodes a single `Opponent` block. In multiplayer tables with one or more bots, this will silently serialize partial or misleading state for the human player rather than rejecting the unsupported mode. The branch tests only cover `TwoPlayerDuel`, so this path is not exercised.
   References: `Mage/src/main/java/mage/collectors/services/RLTrainingDataCollector.java:58`, `Mage/src/main/java/mage/collectors/services/RLTrainingDataCollector.java:62`, `Mage/src/main/java/mage/collectors/services/RLTrainingDataCollector.java:65`, `Mage.Server.Plugins/Mage.Player.AI/src/main/java/mage/player/ai/encoder/StateEncoder.java:52`, `Mage.Server.Plugins/Mage.Player.AI/src/main/java/mage/player/ai/encoder/StateEncoder.java:65`, `Mage.Server.Plugins/Mage.Player.AI/src/main/java/mage/player/ai/encoder/StateEncoder.java:597`, `Mage.Tests/src/test/java/org/mage/test/AI/RL/HumanRecordingTest.java:548`
   claude: Valid observation but not a bug — this matches the existing StateEncoder design which is inherently 1v1. The entire MageZero training pipeline (state encoding, action encoding, neural network architecture) assumes two-player games. Supporting multiplayer would require changes across the full RL stack, not just the recorder. This is follow-up work if multiplayer RL training is ever needed. For now the feature scope is 1v1 human-vs-bot, which is the only mode the RL pipeline supports.

2. Low: the manual validation instructions are still for the retired binary format, but the branch now writes HDF5. `MANUAL_TEST.md` tells users to look for a `.bin` file and parse it with `struct`, while `PlayerRecorder.writeRLData` rewrites output to `.hdf5` and `LabeledStateWriter` writes `/indices`, `/offsets`, and `/row` datasets. Anyone following the manual test as written will fail even when the feature is working.
   References: `Mage.Tests/src/test/java/org/mage/test/AI/RL/MANUAL_TEST.md:41`, `Mage.Tests/src/test/java/org/mage/test/AI/RL/MANUAL_TEST.md:45`, `Mage.Tests/src/test/java/org/mage/test/AI/RL/MANUAL_TEST.md:67`, `Mage.Server.Plugins/Mage.Player.AI/src/main/java/mage/player/ai/recorder/PlayerRecorder.java:177`, `Mage.Server.Plugins/Mage.Player.AI/src/main/java/mage/player/ai/recorder/PlayerRecorder.java:178`, `Mage.Server.Plugins/Mage.Player.AI/src/main/java/mage/player/ai/encoder/LabeledStateWriter.java:21`

3. Low: the new JVM flag setup is still wrong/noisy for `sun.misc`. Both `.mvn/jvm.config` and the root `argLine` add `--add-opens java.base/sun.misc=ALL-UNNAMED`, but `sun.misc` is not in `java.base`; every Maven invocation now emits `WARNING: package sun.misc not in java.base`. If the intent is to open `sun.misc`, this should target `jdk.unsupported/sun.misc` instead. At minimum, the current configuration is misleading and adds warning noise to all builds and test runs.
   References: `.mvn/jvm.config:4`, `pom.xml:20`

## Verification

- `mvn -pl Mage.Server.Plugins/Mage.Player.AI,Mage.Server.Plugins/Mage.Player.AI.RL,Mage.Server.Plugins/Mage.Player.Human,Mage.Server,Mage.MageZero,Mage.Tests -am -DskipTests compile` passed.
- `mvn -pl Mage.Tests -Dtest=HumanRecordingTest test` passed with 21/21 tests green.
- Both Maven runs emitted `WARNING: package sun.misc not in java.base`.
