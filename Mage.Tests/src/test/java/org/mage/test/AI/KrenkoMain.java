package org.mage.test.AI;

import org.mage.magezero.Config;

import mage.cards.repository.CardRepository;
import mage.cards.repository.CardScanner;
import mage.cards.repository.RepositoryUtil;
import org.mage.magezero.ParallelDataGenerator;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.io.PrintWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;


public class KrenkoMain {
    private static final String PLAYER_DECK = "decks/MonoGLandfall.dck";
    private static final List<String> DECK_ARRAY = Arrays.asList(
            "decks/DimirMidrange.dck",
            "decks/BWBats.dck",
            "decks/JeskaiControl.dck",
            "decks/MonoGLandfall.dck",
            "decks/MonoRAggro.dck",
            "decks/IzzetElementals.dck"
    );
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");


    private static class Options {
        private String configPath = "Mage.MageZero/config/krenko_config.yml";
        private int gamesPerTest = 32;
        private int numberOfTests = 1;
        private int maxTurns = 40;
        private int threads = 24;
        private String playerDeck = PLAYER_DECK;
        private String opponentDeck = "decks/JeskaiControl.dck";
        private String playerAType = "mcts";
        private String playerBType = "minimax";
        private String outputDir = "data";
        private int version = 0;
    }

    private static Options parseArgs(String[] args) {
        Options options = new Options();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--config":
                    options.configPath = args[++i];
                    break;
                case "--games-per-test":
                    options.gamesPerTest = Integer.parseInt(args[++i]);
                    break;
                case "--number-of-tests":
                    options.numberOfTests = Integer.parseInt(args[++i]);
                    break;
                case "--max-turns":
                    options.maxTurns = Integer.parseInt(args[++i]);
                    break;
                case "--threads":
                    options.threads = Integer.parseInt(args[++i]);
                    break;
                case "--player-deck":
                    options.playerDeck = args[++i];
                    break;
                case "--opponent-deck":
                    options.opponentDeck = args[++i];
                    break;
                case "--player-a-type":
                    options.playerAType = args[++i];
                    break;
                case "--player-b-type":
                    options.playerBType = args[++i];
                    break;
                case "--version":
                    options.version = Integer.parseInt(args[++i]);
                    break;
                case "--output-dir":
                    options.outputDir = args[++i];
                    break;
                default:
                    throw new IllegalArgumentException("Unknown argument: " + arg);
            }
        }

        return options;
    }

    public static void main(String[] args) {
        Options options = parseArgs(args);
        System.out.println("Current working directory: " + System.getProperty("user.dir"));
        
        long broadStartTime = System.nanoTime();
        StringBuilder benchmarkResults = new StringBuilder();
        benchmarkResults.append("Benchmark Results - ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
        benchmarkResults.append("=".repeat(80)).append("\n");
        benchmarkResults.append("Configuration:\n");
        benchmarkResults.append("  Games per test: ").append(options.gamesPerTest).append("\n");
        benchmarkResults.append("  Number of tests: ").append(options.numberOfTests).append("\n");
        benchmarkResults.append("  Max turns: ").append(options.maxTurns).append("\n");
        benchmarkResults.append("  Threads: ").append(options.threads).append("\n");
        benchmarkResults.append("  Player A Type: ").append(options.playerAType).append("\n");
        benchmarkResults.append("  Player B Type: ").append(options.playerBType).append("\n");
        benchmarkResults.append("=".repeat(80)).append("\n\n");
        
        Config.load(options.configPath);
        // Initialize card database
        RepositoryUtil.bootstrapLocalDb();
        CardScanner.scan();
        

        System.out.println("Starting KrenkoMain tests using deck " + options.playerDeck
                + " against deck: " + options.opponentDeck
                + " for " + options.gamesPerTest + " games each.");
        
        benchmarkResults.append("Test Execution Times:\n");
        benchmarkResults.append("-".repeat(80)).append("\n");
        
        List<Long> testTimes = new ArrayList<>();
        
        try {
            for (int test = 1; test <= options.numberOfTests; test++) {
                long testStartTime = System.nanoTime();
                System.out.println("=== Starting Test " + test + " ===");
                System.out.println("Testing deck " + options.playerDeck + " against " + options.opponentDeck);
                Config.INSTANCE.playerA.deckPath = options.playerDeck;
                Config.INSTANCE.playerB.deckPath = options.opponentDeck;
                Config.INSTANCE.playerA.type = options.playerAType;
                Config.INSTANCE.playerB.type = options.playerBType;
                    // if (options.playerAOutputDir != null) {
                    //     Config.INSTANCE.playerA.outputDir = options.playerAOutputDir;
                    // }
                    // if (options.playerBOutputDir != null) {
                    //     Config.INSTANCE.playerB.outputDir = options.playerBOutputDir;
                    // }
                String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
                Config.INSTANCE.outputDir = options.outputDir + "/ver" + options.version + "/" + timestamp;
                Config.INSTANCE.training.games = options.gamesPerTest;
                Config.INSTANCE.training.maxTurns = options.maxTurns;
                Config.INSTANCE.training.threads = options.threads;
                try {
                    long generatorStartTime = System.nanoTime();
                    ParallelDataGenerator generator = new ParallelDataGenerator();
                    generator.generateData();
                    long generatorEndTime = System.nanoTime();
                    long generatorTimeMs = (generatorEndTime - generatorStartTime) / 1_000_000;
                    
                    double generatorTimeSeconds = generatorTimeMs / 1000.0;
                    
                    String testResultMsg = String.format("Generator: %.4f seconds", 
                                                        generatorTimeSeconds);
                    System.out.println(testResultMsg);
                    benchmarkResults.append(testResultMsg).append("\n");
                    
                    int gamesPlayed = generator.gameCount.get();
                    benchmarkResults.append("Finished " + gamesPlayed + " games");
                    // assertEquals(GAMES_PER_TEST, gamesPlayed, "Should complete all games");
                    // assertTrue(gamesPlayed > 0, "Should play at least one game");
                } catch (Exception e) {
                    System.out.println("Deck " + options.opponentDeck + " caused crash: " + e.getMessage());
                    e.printStackTrace();
                    benchmarkResults.append("Test ").append(test).append(" FAILED: ").append(e.getMessage()).append("\n");
                }
            }
        } finally {
            
            benchmarkResults.append("-".repeat(80)).append("\n\n");        
            
            // Print to console
            System.out.println("\n" + benchmarkResults.toString());
            
            // Write to file
            String benchmarkFilename = Config.INSTANCE.outputDir + "/benchmark_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")) + ".txt";
            try (PrintWriter writer = new PrintWriter(benchmarkFilename)) {
                writer.print(benchmarkResults.toString());
                System.out.println("Benchmark results written to: " + benchmarkFilename);
            } catch (IOException e) {
                System.err.println("Failed to write benchmark results to file: " + e.getMessage());
                e.printStackTrace();
            }
            
            System.out.println("Tests complete. Exiting cleanly.");
            CardRepository.instance.closeDB(true);
            System.exit(0);
        }
    }
}
