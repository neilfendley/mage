# KrenkoBot (MageZero) Roadmap

## Vision
Train a high-Elo Magic: The Gathering AI through self-play reinforcement learning (AlphaZero-style), integrate it into the Magic ecosystem, and publish findings.

## Current State
- XMage game engine with full rules enforcement (30,000+ cards)
- MCTS + neural network player (ComputerPlayerMCTS2) with state/action encoding
- Self-play data generation pipeline (ParallelDataGenerator) producing HDF5 training data
- Human game recording pipeline (PlayerRecorder) for learning from human play
- PyTorch training pipeline with sparse EmbeddingBag network (4 policy heads + value head)
- Flask inference server for real-time neural network evaluation
- Basic training loop working end-to-end for individual decks

## Workstreams

### 1. Training Loop & Self-Play
**Goal:** Iterative self-play that produces progressively stronger models (AlphaGo/AlphaZero style).

- [ ] **Automated iteration loop** — Script that runs: generate data → train → export model → serve new model → generate more data
- [ ] **ELO tracking** — Measure model strength across iterations (play new model vs previous checkpoints)
- [ ] **Curriculum design** — Which decks/matchups to train on and in what order
- [ ] **Hyperparameter tuning** — Search budget, TD-discount, learning rate schedules, network size
- [ ] **Baseline benchmarks** — Measure current minimax bot strength; track RL model improvement over time
- [ ] **Shared Data Location** - We need a shared repository to share model weights and game recordings that people can easily upload to


### 2. Performance & Scale
**Goal:** Generate training data fast enough to iterate quickly.

- [ ] **Profile bottlenecks** — Where does time go in bot-vs-bot games? (game engine, MCTS search, NN inference, state encoding)
- [ ] **GPU inference batching** — Current micro-batching (batch=2, wait=0ms) is conservative; tune for throughput
- [ ] **Game engine optimization** — Reduce copy/clone overhead in MCTS simulation; pool objects
- [ ] **Parallel game throughput** — Scale beyond 10 threads; distributed self-play across machines
- [ ] **ONNX vs PyTorch serving** — Benchmark ONNX Runtime GPU vs direct PyTorch inference
- [ ] **Faster state encoding** — Profile StateEncoder; consider caching or incremental encoding

### 3. Model Architecture & Training
**Goal:** Better models that learn more efficiently from the data.

- [ ] **Cross-deck generalization** — Current model is deck-local (one model per deck); explore shared embeddings across decks
- [ ] **Network depth** — Current architecture is shallow (EmbeddingBag → 1 hidden layer); try deeper networks, residual blocks
- [ ] **Feature engineering** — Review what StateEncoder captures; are there missing game state signals?
- [ ] **Human data integration** — Mix human game recordings with self-play data for training (different data quality, needs weighting)
- [ ] **Auxiliary losses** — Predict game length, card advantage, etc. as auxiliary training signals
- [ ] **Opponent modeling** — Train the opponent policy head on actual opponent behavior

### 4. Visualization & Debugging
**Goal:** Understand what the model is learning and diagnose training issues.

- [ ] **Training dashboard** — Loss curves, win rates, ELO over time (TensorBoard or custom)
- [ ] **Game replay viewer** — Visualize recorded games with model evaluations overlaid
- [ ] **State embedding visualization** — t-SNE/UMAP of encoded states; do similar board positions cluster?
- [ ] **Policy analysis** — For a given board state, show the model's action distribution vs what a human/expert would do
- [ ] **Feature importance** — Which encoded features most influence the value/policy predictions?

### 5. Web Client
**Goal:** Modern web interface for playing against the AI and viewing games.

- [ ] **Game state API** — REST/WebSocket API exposing game state, legal actions, and AI recommendations
- [ ] **Web UI** — React/Vue frontend rendering the game board, hand, stack
- [ ] **Card rendering** — Display card images, mana costs, stats (Scryfall API for images)
- [ ] **AI integration** — Play against the trained model through the web interface
- [ ] **Spectator mode** — Watch AI-vs-AI games in real-time through the browser

### 6. Research & Publication
**Goal:** Publish findings and secure funding.

- [ ] **Literature review** — Survey existing MTG AI work, RL in imperfect information games
- [ ] **Novel contributions** — What's new here? (sparse hashed state encoding for CCGs? deck-local RL? human data augmentation?)
- [ ] **Experimental design** — Define experiments: model vs baselines, ablation studies, human evaluations
- [ ] **Paper draft** — Structure: problem formulation, method, experiments, results, discussion
- [ ] **Grant applications** — NSF, DARPA, industry research grants; frame as RL in complex imperfect-info environments

## Priorities (suggested sequencing)

**Phase 1: Prove the loop works**
Focus: automated self-play iteration, ELO tracking, basic training dashboard.
This is the foundation — everything else depends on iterative improvement working.

**Phase 2: Scale up**
Focus: performance optimization, parallel self-play, GPU inference tuning.
Faster iteration = more experiments = faster progress.

**Phase 3: Improve the model**
Focus: architecture experiments, cross-deck generalization, human data integration.
Now that the loop is fast, try making the model smarter.

**Phase 4: External-facing**
Focus: web client, paper writing, grant applications.
Show the work to the world once there are strong results to demonstrate.

## Open Questions
- What's the right deck scope? Start with one deck deeply, or breadth across the meta?
- How much does human training data help vs pure self-play?
- Is the sparse hashed encoding expressive enough, or do we need structured features?
- Can a single model generalize across decks, or is deck-local the right approach?
- What ELO can we realistically target? (MTGA Mythic? tournament-level? superhuman?)
