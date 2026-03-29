# Performance Baseline

**Machine:** MacBook Air M2, 8 cores, 16GB RAM, Java 24
**Date:** 2026-03-29
**Config:** MCTS (player A) vs Minimax (player B), IzzetElementals vs DimirMidrange, TESTS=1
**Note:** "Games completed" may be < GAMES requested due to pre-existing AI/engine crashes.
**Reference:** Neil's Linux machine: 7,200 games in 24 hours ≈ 12s/game (budget=300, minimax opponent)

## Thread Count Scaling (BUDGET=100, GAMES=5)

| Threads | Wall clock | Games | Avg/game | CPU time | validateState % | stateEncode % |
|---------|-----------|-------|----------|----------|----------------|---------------|
| 2       | 1m 06s    | 4     | 21.5s    | 2m 20s   | 45.7%          | 13.5%         |
| 4       | 1m 05s    | 4     | 24.4s    | 2m 51s   | 65.2%          | 20.0%         |
| 8       | 1m 18s    | 3     | 42.7s    | 3m 56s   | 77.8%          | 24.7%         |
| 10      | (prev run)| 4     | 43.1s    | —        | 69.1%          | 22.6%         |

**Observation:** More threads = more CPU contention. Wall clock is roughly the same (games run in parallel but each game takes longer due to resource contention). The per-game time *increases* with more threads because of cache/memory pressure. **2 threads is the sweet spot on 8 cores** for this workload — games are CPU-bound and don't benefit from hyper-threading.

## Search Budget Scaling (THREADS=4, GAMES=5)

| Budget | Wall clock | Games | Avg/game | validateState calls | validateState % | stateEncode % |
|--------|-----------|-------|----------|--------------------|-----------------| --------------|
| 50     | 0m 44s    | 4     | 18.9s    | 15,936             | 57.0%           | 19.1%         |
| 100    | 1m 05s    | 4     | 24.4s    | 25,606             | 65.2%           | 20.0%         |
| 300    | 2m 33s    | 4     | 94.7s    | 92,253             | 85.5%           | 28.2%         |

**Observation:** Time scales roughly linearly with budget (as expected — more simulations per decision). At higher budgets, `validateState` dominates even more (85.5% at budget=300).

## Breakdown: Where Time Goes

At budget=100, 4 threads (representative config):

| Operation | Time | % of game time | Calls | Per-call |
|-----------|------|---------------|-------|----------|
| **validateState** (game copy + replay) | 63.6s | **65.2%** | 25,606 | 2.5ms |
| **stateEncode** (feature extraction) | 19.5s | **20.0%** | 26,375 | 0.7ms |
| evaluate (NN / heuristic) | 0.1s | 0.1% | 21,956 | 0.005ms |
| expand (generate children) | 0.1s | 0.1% | 21,956 | 0.005ms |
| other/overhead | 14.2s | 14.6% | — | — |

## Key Takeaways

1. **validateState is 65-85% of runtime** — This copies and replays the game state for each MCTS simulation. The cost is dominated by deep-copying game objects (`CardUtil.deepCopyObject`, `Watcher.copy`, `AbilityImpl` constructors). This is the #1 optimization target.

2. **stateEncode is 13-28% of runtime** — Feature extraction via `StateEncoder.processState`. The cost is in `Features.stateRefresh()` (recursive HashMap operations) and `Features.addFeature()` (HashMap puts + string hashing). #2 optimization target.

3. **evaluate and expand are negligible** (~0.2% combined) — In offline mode (no NN server), the heuristic evaluation is nearly free. This would change with a remote NN, but locally it's not a bottleneck.

4. **Thread scaling is poor** — Adding threads beyond 2 increases per-game time without reducing wall clock time. This is because each game's MCTS search is single-threaded and CPU-bound, so more concurrent games just fight for cache and memory bandwidth.

5. **Quick win: use fewer threads** — On an 8-core machine, THREADS=2 gives the best throughput per core. For maximizing total games/hour, match thread count to physical core count minus 2 (leave room for OS/GC).

## Optimization Priority

1. **Optimize validateState** — Incremental game state updates, copy-on-write, or structural sharing could reduce the 2.5ms/call cost dramatically.
2. **Optimize stateEncode** — Cache encoded features, avoid redundant string operations, reduce HashMap overhead.
3. **Parallel MCTS within a game** — Would let each game use multiple cores productively instead of adding more concurrent games.
