# Manual Test: RL Recording for Human-vs-Bot Games

## Prerequisites
1. Build the project: `mvn clean install -DskipTests`
2. No neural network inference server needed (bot falls back to offline mode)

## Setup
3. Start the Mage server with RL data collection enabled:
   ```
   java -Dxmage.dataCollectors.rlTrainingData=true -jar Mage.Server/target/mage-server.jar
   ```
4. Start the Mage client: `java -jar Mage.Client/target/mage-client.jar`
5. Connect to your local server

## Create a game
6. Create a new table/match
7. Select yourself (human) as one player
8. Select **"Computer - MageZero"** as the opponent
9. Pick any two constructed decks
10. Start the game

## During gameplay
11. Watch the **server logs** for these messages:
    - `"RL init for ... (MZ ver1.0.2)"` -- bot initialized
    - `"RL recording enabled for human opponent: ..."` -- recorder attached to you
    - `"RL recorded PRIORITY decision (total: X states)"` -- states accumulating
    - `"RL recorded CHOOSE_USE decision ..."` -- if you make yes/no choices
    - `"RL recorded CHOOSE_TARGET decision ..."` -- if you target something
12. Play several turns, cast spells, pass priority, make choices

## After the game ends
13. Check the server logs for:
    - `"RL training data: wrote N states for ... to rl_training_data/..."` -- data persisted
14. Check the `rl_training_data/` directory for a `.bin` file
15. Verify the file is non-empty and its name contains your player name

## Validate the output file
You can inspect the binary file with a simple Python script:
```python
import struct, sys

with open(sys.argv[1], 'rb') as f:
    count = struct.unpack('>i', f.read(4))[0]
    print(f"Records: {count}")
    for i in range(min(count, 3)):  # show first 3
        num_indices = struct.unpack('>i', f.read(4))[0]
        indices = [struct.unpack('>i', f.read(4))[0] for _ in range(num_indices)]
        actions = [struct.unpack('>d', f.read(8))[0] for _ in range(128)]
        result_label = struct.unpack('>d', f.read(8))[0]
        state_score = struct.unpack('>d', f.read(8))[0]
        is_player = struct.unpack('>?', f.read(1))[0]
        action_type = struct.unpack('>i', f.read(4))[0]
        action_idx = next((j for j, v in enumerate(actions) if v > 0), -1)
        print(f"  [{i}] features={num_indices}, action_idx={action_idx}, "
              f"result={result_label:.3f}, score={state_score:.3f}, "
              f"isPlayer={is_player}, type={action_type}")
```

## Expected results
- The `.bin` file should contain one record per decision point in the game
- `action_idx=0` entries are pass actions
- `result_label` values should be positive if you won, negative if you lost
- Later states should have `result_label` closer to +1.0 or -1.0 (TD-discount effect)
