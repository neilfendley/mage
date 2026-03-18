package org.mage.test.AI;

import org.mage.magezero.Config;
import mage.cards.repository.CardRepository;
import mage.cards.repository.CardScanner;
import mage.cards.repository.RepositoryUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mage.magezero.Config;
import org.mage.magezero.ParallelDataGenerator;

import static org.junit.jupiter.api.Assertions.*;

public class KrenkoMain {
    private static final int GAMES_PER_TEST = 1;
    private static final int MAX_TURNS = 20;
    private static final String OPPONENT_DECK = "Mage.MageZero/decks/BGRoots.dck";
    private static final String TEST_DECK = "Mage.MageZero/decks/BWBats.dck";

    public static void main(String[] args) {
        System.out.println("Current working directory: " + System.getProperty("user.dir"));
        Config.load("Mage.MageZero/config/krenko_config.yml");
        RepositoryUtil.bootstrapLocalDb();
        CardScanner.scan();
        Config.INSTANCE.playerA.deckPath = TEST_DECK;
        Config.INSTANCE.playerB.deckPath = OPPONENT_DECK;
        Config.INSTANCE.playerA.type = "mcts";
        Config.INSTANCE.playerB.type = "minimax";
        Config.INSTANCE.training.games = GAMES_PER_TEST;
        Config.INSTANCE.training.maxTurns = MAX_TURNS;
        Config.INSTANCE.training.threads = 4;
        try {
            ParallelDataGenerator generator = new ParallelDataGenerator();
            generator.generateData();
            int gamesPlayed = generator.gameCount.get();
            assertEquals(GAMES_PER_TEST, gamesPlayed, "Should complete all games");
            assertTrue(gamesPlayed > 0, "Should play at least one game");
        } catch (Exception e) {
            fail("Deck " + TEST_DECK + " caused crash: " + e.getMessage(), e);
        }
    }
}