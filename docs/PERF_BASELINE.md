# Performance Baseline

**Machine:** MacBook Air M2, 8 cores, 16GB RAM, Java 24
**Date:** 2026-03-29
**Config:** MCTS (player A) vs Minimax (player B), IzzetElementals vs DimirMidrange, TESTS=1
**Note:** "Games completed" may be < GAMES requested due to pre-existing AI/engine crashes.
**Reference:** Neil's Linux machine: 7,200 games in 24 hours ≈ 12s/game (budget=300, minimax opponent)

## Where Time Goes (BUDGET=100, THREADS=2, SKILL=3)

| Operation | % of game time | Per-call | Calls/run | Description |
|-----------|---------------|----------|-----------|-------------|
| **gameCopy** | **56%** | ~1.2ms | 33K | Deep-copy game state for MCTS simulation |
| **stateEncode** | **27%** | 0.5ms | 34K | Convert game state to sparse features |
| **other** | **17%** | — | — | Minimax opponent, game engine, GC |
| evaluate | <1% | 0.003ms | 22K | Heuristic value (no NN) |
| expand | <1% | 0.004ms | 22K | Generate MCTS children |

`gameCopy` and `stateEncode` together are nested inside `validateState` (83% total).

## Minimax Skill Level (THREADS=2, BUDGET=100, GAMES=5)

| Skill | Avg/game | Wall clock | other % | Description |
|-------|----------|------------|---------|-------------|
| 6     | 41.8s    | 1m 33s     | 40.6%   | Depth 6 — minimax dominates "other" |
| 3     | 23.0s    | 0m 58s     | 17.1%   | Depth 4 — minimax nearly free |

## Search Budget Scaling (THREADS=2, SKILL=3, GAMES=5)

| Budget | Avg/game | Wall clock | gameCopy % | stateEncode % |
|--------|----------|------------|-----------|---------------|
| 100    | 23.0s    | 0m 58s     | 56.1%     | 26.6%         |
| 300    | 41.4s    | 1m 54s     | 58.9%     | 24.9%         |

## Quick Wins (No Architecture Changes)

1. **Use THREADS=2** on 8-core machines (best per-game throughput)
2. **Use SKILL=3** for minimax opponent (2x faster, opponent still plays reasonably)
3. **Combined: `make run-krenko THREADS=2 SKILL=3`** — ~4x faster than default settings

## Optimization Priority (Architecture Changes)

1. **gameCopy (56%)** — deep-copying game state objects (permanents, abilities, watchers, effects). Requires engine-level changes: copy-on-write, structural sharing, or undo/redo. Estimated 1-2 months, medium risk, touches upstream XMage code.
2. **stateEncode (27%)** — feature extraction traversal of game state. Fully in our plugin code, zero upstream risk. Hash caching and StringBuilder optimizations already applied.
