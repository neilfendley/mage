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

/**
 * Data collector that writes RL training data to disk when a game ends.
 * Checks all players for attached GameRecorders and persists their accumulated states.
 *
 * Enable with: -Dxmage.dataCollectors.rlTrainingData=true
 */
public class RLTrainingDataCollector extends EmptyDataCollector {

    public static final String SERVICE_CODE = "rlTrainingData";
    private static final Logger logger = Logger.getLogger(RLTrainingDataCollector.class);
    private static final String OUTPUT_DIR = "rl_training_data";
    private static final double TD_DISCOUNT = 0.95;

    @Override
    public String getServiceCode() {
        return SERVICE_CODE;
    }

    @Override
    public String getInitInfo() {
        return "output dir: " + OUTPUT_DIR;
    }

    @Override
    public void onGameEnd(Game game) {
        for (Player player : game.getPlayers().values()) {
            GameRecorder recorder = player.getRecorder();
            if (recorder == null || recorder.getRecordedStateCount() == 0) {
                continue;
            }

            try {
                Path dir = Paths.get(OUTPUT_DIR);
                Files.createDirectories(dir);

                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                String fileName = String.format("%s_%s_%s.bin",
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
