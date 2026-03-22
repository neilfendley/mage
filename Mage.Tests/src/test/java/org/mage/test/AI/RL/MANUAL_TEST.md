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
14. Check the `Mage.Server/rl_training_data/` directory for a `.hdf5` file
15. Verify the file is non-empty and its name contains your player name

## Validate the output file
You can inspect the HDF5 file with a simple Python script (requires `h5py`):
```python
import h5py, sys

with h5py.File(sys.argv[1], 'r') as f:
    indices = f['indices'][:]
    offsets = f['offsets'][:]
    rows = f['row'][:]
    num_records = rows.shape[0]
    print(f"Records: {num_records}")
    print(f"Total sparse features: {len(indices)}")
    for i in range(min(num_records, 3)):  # show first 3
        start, end = int(offsets[i]), int(offsets[i + 1])
        num_features = end - start
        action_idx = next((j for j in range(128) if rows[i, j] > 0), -1)
        result_label = rows[i, 128]
        state_score = rows[i, 129]
        is_player = rows[i, 130]
        action_type = int(rows[i, 131])
        print(f"  [{i}] features={num_features}, action_idx={action_idx}, "
              f"result={result_label:.3f}, score={state_score:.3f}, "
              f"isPlayer={is_player:.0f}, type={action_type}")
```

## Expected results
- The `.hdf5` file should contain one record per decision point in the game
- `action_idx=0` entries are pass actions
- `result_label` values should be positive if you won, negative if you lost
- Later states should have `result_label` closer to +1.0 or -1.0 (TD-discount effect)
- The file can be loaded directly by the MageZero Python training pipeline (`dataset.py`)

## Troubleshooting
- **Port 17171 already in use**: Kill the existing process with `lsof -ti :17171 | xargs kill`
- **SQLite native library error on Apple Silicon**: Ensure `Mage.Server/pom.xml` has `sqlite-jdbc` version `3.42.0.0` or later
- **JavaFX errors (prism_es2/prism_sw)**: These are non-fatal warnings on Apple Silicon; the client still works without the embedded browser
