# Manual Test: RL Recording for Human-vs-Bot Games

## Prerequisites
1. Build the project: `mvn clean install -DskipTests`
2. No neural network inference server needed (bot falls back to offline mode)

## Setup
3. Start the Mage server: `java -jar Mage.Server/target/mage-server.jar`
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

## Verify
13. Confirm state count increases in the logs as you play
14. Confirm both PRIORITY (ability activations and passes) and other decision types appear

## Known limitation
States are not automatically written to disk when the game ends. `writeRLData()` has no server-side caller yet -- this needs a DataCollector or game-end hook (follow-up work). This manual test verifies that recording works in-memory.
