package org.mage.test.AI;

import org.mage.magezero.Config;
import mage.cards.repository.CardScanner;
import mage.cards.repository.RepositoryUtil;
import org.mage.magezero.ParallelDataGenerator;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class KrenkoMain {
    // All configurable via system properties, e.g.: make run-krenko GAMES=5 THREADS=4
    private static final int GAMES_PER_TEST = Integer.getInteger("krenko.games", 30);
    private static final int NUMBER_OF_TESTS = Integer.getInteger("krenko.tests", 4);
    private static final int MAX_TURNS = Integer.getInteger("krenko.maxTurns", 25);
    private static final int THREADS = Integer.getInteger("krenko.threads", 10);
    private static final int SEARCH_BUDGET = Integer.getInteger("krenko.searchBudget", 1000);
    private static final int MINIMAX_SKILL = Integer.getInteger("krenko.minimaxSkill", 6);
    private static final String PLAYER_DECK = System.getProperty("krenko.deck", "decks/IzzetElementals.dck");
    private static final List<String> DECK_ARRAY = Arrays.asList(
            "decks/DimirMidrange.dck",
            "decks/BWBats.dck",
            "decks/JeskaiControl.dck",
            "decks/MonoGLandfall.dck",
            "decks/MonoRAggro.dck",
            "decks/IzzetElementals.dck"
    );
    public static void main(String[] args) {
        System.out.println("Current working directory: " + System.getProperty("user.dir"));
        Config.load("Mage.MageZero/config/krenko_config.yml");
        if (RepositoryUtil.isDatabaseEmpty()) {
            RepositoryUtil.bootstrapLocalDb();
            CardScanner.scan();
        }
        System.out.println("Starting KrenkoMain tests using deck" + PLAYER_DECK + " against decks: " + DECK_ARRAY + " for " + GAMES_PER_TEST + " games each.");
        for (int test = 1; test <= NUMBER_OF_TESTS; test++) {
            System.out.println("=== Starting Test " + test + " ===");
            for (int i = 0; i < DECK_ARRAY.size(); i++) {
                String oppDeck = DECK_ARRAY.get(i);
                System.out.println("Testing deck " + PLAYER_DECK + " against " + oppDeck);
                Config.INSTANCE.playerA.deckPath = PLAYER_DECK;
                Config.INSTANCE.playerB.deckPath = oppDeck;
                Config.INSTANCE.playerA.type = "mcts";
                Config.INSTANCE.playerB.type = "minimax";
                Config.INSTANCE.training.games = GAMES_PER_TEST;
                Config.INSTANCE.training.maxTurns = MAX_TURNS;
                Config.INSTANCE.training.threads = THREADS;
                Config.INSTANCE.playerA.mcts.searchBudget = SEARCH_BUDGET;
                Config.INSTANCE.playerB.skill = MINIMAX_SKILL;
                try {
                    ParallelDataGenerator generator = new ParallelDataGenerator();
                    generator.generateData();
                    int gamesPlayed = generator.gameCount.get();
                    assertEquals(GAMES_PER_TEST, gamesPlayed, "Should complete all games");
                    assertTrue(gamesPlayed > 0, "Should play at least one game");
                } catch (Exception e) {
                    System.out.println("Deck " + oppDeck + " caused crash: " + e.getMessage());
                }
            }
        }
    }
}