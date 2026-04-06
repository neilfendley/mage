package org.mage.test.AI;

import org.mage.magezero.Config;
import mage.cards.repository.CardScanner;
import mage.cards.repository.RepositoryUtil;
import org.mage.magezero.ParallelDataGenerator;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

public class KrenkoMain {
    private static final int GAMES_PER_TEST = 20;
    // private static final int NUMBER_OF_TESTS = 8;
    private static final int MAX_TURNS = 50;
    private static final String PLAYER_DECK = "decks/MonoGLandfall.dck";
    private static final List<String> DECK_ARRAY = Arrays.asList(
            "decks/DimirMidrange.dck",
            "decks/BWBats.dck",
            "decks/JeskaiControl.dck",
            "decks/MonoGLandfall.dck",
            "decks/MonoRAggro.dck",
            "decks/IzzetElementals.dck"
    );

    private static class Options {
        private String configPath = "Mage.MageZero/config/krenko_config.yml";
        private int gamesPerTest = GAMES_PER_TEST;
        private int numberOfTests = 1;
        private int maxTurns = MAX_TURNS;
        private int threads = 20;
        private String playerDeck = PLAYER_DECK;
        private String opponentDeck = "decks/JeskaiControl.dck";
        private String playerAType = "mcts";
        private String playerBType = "minimax";
        private String playerAOutputDir = null;
        private String playerBOutputDir = null;
        private boolean selfPlay = false;
        private int searchBudget=1000;
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
                case "--self-play":
                    options.selfPlay = true;
                    options.playerBType = "mcts";
                    break;
                case "--search-budget":
                    options.searchBudget = Integer.parseInt(args[++i]);
                    break;
                case "--version":
                    options.version = Integer.parseInt(args[++i]);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown argument: " + arg);
            }
        }

        if (options.selfPlay) {
            options.opponentDeck = options.playerDeck;
        }
        return options;
    }

    public static void main(String[] args) {
        Options options = parseArgs(args);
        System.out.println("Current working directory: " + System.getProperty("user.dir"));
        Config.load(options.configPath);
        if (RepositoryUtil.isDatabaseEmpty()) {
            RepositoryUtil.bootstrapLocalDb();
            CardScanner.scan();
        }
        System.out.println("Starting KrenkoMain tests using deck " + options.playerDeck
                + " against deck: " + options.opponentDeck
                + " for " + options.gamesPerTest + " games each.");
        // try {
        for (int test = 1; test <= options.numberOfTests; test++) {
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
                Config.INSTANCE.outputDir = options.output_dir + "/ver" + options.version;
                Config.INSTANCE.training.games = options.gamesPerTest;
                Config.INSTANCE.training.maxTurns = options.maxTurns;
                Config.INSTANCE.training.threads = options.threads;
            Config.INSTANCE.playerA.mcts.searchBudget = options.searchBudget;
            try {
                ParallelDataGenerator generator = new ParallelDataGenerator();
                generator.generateData();
                // int gamesPlayed = generator.gameCount.get();
                // assertEquals(GAMES_PER_TEST, gamesPlayed, "Should complete all games");
                // assertTrue(gamesPlayed > 0, "Should play at least one game");
            } catch (Exception e) {
                System.out.println("Deck " + options.opponentDeck + " caused crash: " + e.getMessage());
                e.printStackTrace();
            }
        }
        // } finally {
        System.out.println("Tests complete. Exiting cleanly.");
        CardRepository.instance.closeDB(true);
        System.exit(0);
        // }
    }
}
