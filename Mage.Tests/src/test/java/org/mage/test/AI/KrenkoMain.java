package org.mage.test.AI;

import org.mage.magezero.Config;
import mage.cards.repository.CardScanner;
import mage.cards.repository.RepositoryUtil;
import org.mage.magezero.ParallelDataGenerator;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class KrenkoMain {
    private static final int GAMES_PER_TEST = 100;
    private static final int MAX_TURNS = 50;
    private static final String PLAYER_DECK = "decks/BWBats.dck";
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
        for (int i = 0; i < DECK_ARRAY.size(); i++) {
            String oppDeck = DECK_ARRAY.get(i);
            System.out.println("Testing deck " + PLAYER_DECK + " against " + oppDeck);
            Config.INSTANCE.playerA.deckPath = PLAYER_DECK;
            Config.INSTANCE.playerB.deckPath = oppDeck;
            Config.INSTANCE.playerA.type = "mcts";
            Config.INSTANCE.playerB.type = "minimax";
            Config.INSTANCE.training.games = GAMES_PER_TEST;
            Config.INSTANCE.training.maxTurns = MAX_TURNS;
            Config.INSTANCE.training.threads = 10;
            try {
                ParallelDataGenerator generator = new ParallelDataGenerator();
                generator.generateData();
                int gamesPlayed = generator.gameCount.get();
                assertEquals(GAMES_PER_TEST, gamesPlayed, "Should complete all games");
                assertTrue(gamesPlayed > 0, "Should play at least one game");
            } catch (Exception e) {
                fail("Deck " + oppDeck + " caused crash: " + e.getMessage(), e);
            }
        }
        
    }
}