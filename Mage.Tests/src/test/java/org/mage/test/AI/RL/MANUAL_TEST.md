# Manual Test: RL Recording for Human-vs-Bot Games

## Prerequisites
1. Build the project: `mvn clean install -DskipTests`
2. No neural network inference server needed (bot falls back to offline mode)
3. Java 17+ `--add-opens` flags are configured automatically via `.mvn/jvm.config`

## Start the server
3. From the project root:
   ```
   make run-server
   ```
   Wait for `Started MAGE server - listening on 0.0.0.0:17171` in the logs.
   Verify you see `Data collectors: rlTrainingData - enabled` in the output.

## Start the client
4. In a separate terminal, from the project root:
   ```
   make run-client
   ```
5. Connect to `localhost:17171` in the client UI

## Create a game
6. Create a new table/match
7. Select yourself (human) as one player
8. Select any computer opponent (any bot type works)
9. Pick any two constructed decks
10. Start the game

## During gameplay
11. Watch the **server logs** for these messages:
    - `"RL recording enabled for human player: ..."` -- recorder attached to you
    - `"RL recorded PRIORITY decision (total: X states)"` -- states accumulating
    - `"RL recorded CHOOSE_USE decision ..."` -- if you make yes/no choices
    - `"RL recorded CHOOSE_TARGET decision ..."` -- if you target something
12. Play several turns, cast spells, pass priority, make choices

## After the game ends
13. Check the server logs for:
    - `"RL training data: wrote N states for ... to rl_training_data/..."` -- data persisted
14. Check the `Mage.Server/rl_training_data/` directory for a `.bin` file
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

## Troubleshooting
- **Port 17171 already in use**: Kill the existing process with `lsof -ti :17171 | xargs kill`
- **SQLite native library error on Apple Silicon**: Ensure `Mage.Server/pom.xml` has `sqlite-jdbc` version `3.42.0.0` or later
- **JavaFX errors (prism_es2/prism_sw)**: These are non-fatal warnings on Apple Silicon; the client still works without the embedded browser
