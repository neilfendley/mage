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
 * Checks all players for attached GameRecorders and persists their accumulated states.
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
        // Ensure the PlayerRecorder factory is registered (triggers static initializer)
        try {
            Class.forName("mage.player.ai.recorder.PlayerRecorder");
        } catch (ClassNotFoundException e) {
            logger.warn("PlayerRecorder class not found — RL recording will not be available");
            return;
        }

        // Only supported for two-player games (StateEncoder models a single opponent)
        if (game.getPlayers().size() != 2) {
            return;
        }

        // Attach recorders to any human player whose opponent is a bot
        for (Player player : game.getPlayers().values()) {
            if (player.isHuman() && player.getRecorder() == null) {
                // Find an AI opponent
                for (UUID opponentId : game.getOpponents(player.getId())) {
                    Player opponent = game.getPlayer(opponentId);
                    if (opponent != null && opponent.isComputer()) {
                        GameRecorder recorder = GameRecorder.Factory.create(player.getId(), opponentId);
                        if (recorder != null) {
                            player.setRecorder(recorder);
                            logger.info("RL recording enabled for human player: " + player.getName()
                                    + " (vs " + opponent.getName() + ")");
                        } else {
                            logger.warn("RL recording factory not registered — is mage-player-ai loaded?");
                        }
                        break;
                    }
                }
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

                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                String fileName = String.format("%s_%s_%s.hdf5",
                        player.getName(), game.getId().toString().substring(0, 8), timestamp);
                String outputPath = dir.resolve(fileName).toString();

                int written = recorder.writeRLData(outputPath, player.hasWon(), TD_DISCOUNT);
                if (written > 0) {
                    logger.info("RL training data: wrote " + written + " states for "
                            + player.getName() + " to " + outputPath);
                }
            } catch (Exception e) {
                logger.error("Failed to write RL training data for " + player.getName() + ": " + e.getMessage());
            }
        }
    }
}
