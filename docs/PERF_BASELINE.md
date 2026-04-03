# Performance Baseline

**Machine:** MacBook Air M2, 8 cores, 16GB RAM, Java 24
**Date:** 2026-03-29
**Config:** MCTS (player A) vs Minimax (player B), IzzetElementals vs DimirMidrange, TESTS=1
**Note:** "Games completed" may be < GAMES requested due to pre-existing AI/engine crashes.
**Reference:** Neil's Linux machine: 7,200 games in 24 hours ≈ 12s/game (budget=300, minimax opponent)

## Where Time Goes (BUDGET=100, THREADS=2, SKILL=3)

| Operation | % of game time | Per-call | Calls/run | Description |
|-----------|---------------|----------|-----------|-------------|
| **validateState** | **83%** | 1.7ms | 33K | Copy game state + replay + encode for MCTS |
| **other** | **17%** | — | — | Minimax opponent, game engine, GC |
| evaluate | <1% | 0.003ms | 22K | Heuristic value (no NN) |
| expand | <1% | 0.004ms | 22K | Generate MCTS children |

`stateEncode` (0.5ms/call, 34K calls) partially overlaps with `validateState` — encoding happens inside the validate path for MCTS nodes, plus separately for minimax logging.

## Minimax Skill Level (THREADS=2, BUDGET=100, GAMES=5)

| Skill | Avg/game | Wall clock | other % | Description |
|-------|----------|------------|---------|-------------|
| 6     | 41.8s    | 1m 33s     | 40.6%   | Depth 6 — minimax dominates "other" |
| 3     | 23.0s    | 0m 58s     | 17.1%   | Depth 4 — minimax nearly free |

## Search Budget Scaling (THREADS=2, SKILL=3, GAMES=5)

| Budget | Avg/game | Wall clock | validateState % |
|--------|----------|------------|-----------------|
| 100    | 23.0s    | 0m 58s     | 83%             |
| 300    | 41.4s    | 1m 54s     | 84%             |

## Quick Wins (No Architecture Changes)

1. **Use THREADS=2** on 8-core machines (best per-game throughput)
2. **Use SKILL=3** for minimax opponent (2x faster, opponent still plays reasonably)
3. **Combined: `make run-krenko THREADS=2 SKILL=3`** — ~4x faster than default settings

## Optimization Priority (Architecture Changes)

1. **validateState (83%)** — game state deep copy, replay, and encoding. The copy portion requires engine-level changes: copy-on-write, structural sharing, or undo/redo. Estimated 1-2 months, medium risk, touches upstream XMage code. The encoding portion (stateEncode) is in our plugin code with zero upstream risk — hash caching and StringBuilder optimizations already applied.
2. **other (17%)** — minimax opponent search. Already addressable via `SKILL=3`.
