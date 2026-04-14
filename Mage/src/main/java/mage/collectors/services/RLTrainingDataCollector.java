package mage.collectors.services;

import mage.game.Game;
import mage.game.GameRecorder;
import mage.players.Player;
import org.apache.log4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

/**
 * Data collector that writes RL training data to disk when a game ends.
 *
 * Enable with: -Dxmage.dataCollectors.rlTrainingData=true
 */
public class RLTrainingDataCollector extends EmptyDataCollector {

    public static final String SERVICE_CODE = "rlTrainingData";
    private static final Logger logger = Logger.getLogger(RLTrainingDataCollector.class);
    private static final String DEFAULT_OUTPUT_DIR = "rl_training_data";
    private static final double TD_DISCOUNT = 0.95;

    private final String outputDir;

    public RLTrainingDataCollector() {
        this(DEFAULT_OUTPUT_DIR);
    }

    public RLTrainingDataCollector(String outputDir) {
        this.outputDir = outputDir;
    }

    @Override
    public String getServiceCode() {
        return SERVICE_CODE;
    }

    @Override
    public String getInitInfo() {
        return "output dir: " + outputDir;
    }

    @Override
    public void onGameStart(Game game) {
        try {
            Class.forName("mage.player.ai.recorder.PlayerRecorder");
        } catch (ClassNotFoundException e) {
            logger.warn("PlayerRecorder class not found, RL recording will not be available");
            return;
        }

        if (game.getPlayers().size() != 2) {
            return;
        }

        for (Player player : game.getPlayers().values()) {
            if (!player.isHuman() || player.getRecorder() != null) {
                continue;
            }
            for (UUID opponentId : game.getOpponents(player.getId())) {
                Player opponent = game.getPlayer(opponentId);
                if (opponent == null || opponent.isHuman() || opponent.getRecorder() != null) {
                    continue;
                }
                GameRecorder recorder = GameRecorder.Factory.create(player.getId(), opponentId);
                if (recorder != null) {
                    player.setRecorder(recorder);
                    logger.info("RL recording enabled for player: " + player.getName()
                            + " (vs " + opponent.getName() + ")");
                } else {
                    logger.warn("RL recording factory not registered");
                }
                break;
            }
        }
    }

    @Override
    public void onGameEnd(Game game) {
        for (Player player : game.getPlayers().values()) {
            GameRecorder recorder = player.getRecorder();
            if (recorder == null || recorder.getRecordedStateCount() == 0) {
                continue;
            }

            try {
                Path dir = Paths.get(outputDir);
                Files.createDirectories(dir);

                String deckName = "Unknown";
                if (player.getMatchPlayer() != null && player.getMatchPlayer().getDeck() != null) {
                    deckName = player.getMatchPlayer().getDeck().getName();
                    if (deckName == null || deckName.isEmpty()) {
                        deckName = "FoundNoName";
                    }
                }

                Player opponent = null;
                String opponentDeckName = "Unknown";
                try {
                    for (UUID opponentId : game.getOpponents(player.getId())) {
                        opponent = game.getPlayer(opponentId);
                        if (opponent != null && opponent.getMatchPlayer() != null && opponent.getMatchPlayer().getDeck() != null) {
                            opponentDeckName = opponent.getMatchPlayer().getDeck().getName();
                            if (opponentDeckName == null || opponentDeckName.isEmpty()) {
                                opponentDeckName = "FoundNoName";
                            }
                            break;
                        }
                    }
                } catch (IllegalStateException e) {
                    logger.warn("Failed to look up opponent for " + player.getName()
                            + "; falling back to unknown labels", e);
                }

                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                String playerAPath = String.format("%s_vs_%s.%s.hdf5", deckName, opponentDeckName, timestamp);
                String playerBPath = String.format("%s_vs_%s.%s_vs_%s.%s.hdf5",
                        opponentDeckName,
                        deckName,
                        player.getName(),
                        opponent != null ? opponent.getName() : "NoOpponent",
                        timestamp);

                int written = recorder.writeRLData(
                        dir.resolve(playerAPath).toString(),
                        dir.resolve(playerBPath).toString(),
                        player.hasWon(),
                        TD_DISCOUNT
                );
                if (written > 0) {
                    logger.info("RL training data: wrote " + written + " states for "
                            + player.getName() + " deck: " + deckName + ", opponent deck: " + opponentDeckName + ")");
                }
            } catch (Exception e) {
                logger.error("Failed to write RL training data for " + player.getName() + ": " + e.getMessage());
            }
        }
    }
}
