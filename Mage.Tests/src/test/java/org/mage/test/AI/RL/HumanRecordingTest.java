package org.mage.test.AI.RL;

import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import mage.cards.decks.Deck;
import mage.cards.decks.DeckCardLists;
import mage.cards.decks.importer.DeckImporter;
import mage.cards.repository.CardRepository;
import mage.cards.repository.CardScanner;
import mage.cards.repository.RepositoryUtil;
import mage.collectors.services.RLTrainingDataCollector;
import mage.constants.MultiplayerAttackOption;
import mage.constants.RangeOfInfluence;
import mage.game.Game;
import mage.game.TwoPlayerDuel;
import mage.game.match.Match;
import mage.game.match.MatchOptions;
import mage.game.mulligan.MulliganType;
import mage.player.ai.ComputerPlayer7;
import mage.player.ai.encoder.ActionEncoder;
import mage.player.ai.encoder.StateEncoder;
import mage.player.ai.recorder.PlayerRecorder;
import mage.player.human.HumanPlayer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HumanRecordingTest {

    private static final String TEST_DECK = "Mage.Tests/decks/Standard-MonoR.dck";

    @BeforeAll
    static void setup() {
        if (RepositoryUtil.isDatabaseEmpty()) {
            RepositoryUtil.bootstrapLocalDb();
            CardScanner.scan();
        }
    }

    @Test
    void testCollectorAttachesRecorderForHumanVsBot() throws Exception {
        HumanPlayer human = new HumanPlayer("Human", RangeOfInfluence.ONE, 1);
        ComputerPlayer7 bot = new ComputerPlayer7("Bot", RangeOfInfluence.ONE, 6);
        Game game = createTestGame(human, bot);

        RLTrainingDataCollector collector = new RLTrainingDataCollector();
        collector.onGameStart(game);

        assertNotNull(human.getRecorder(), "Collector should attach a recorder to the human player");
        assertNull(bot.getRecorder(), "Collector should not attach a recorder to the bot");
    }

    @Test
    void testRLTrainingDataCollectorWritesFilesOnGameEnd() throws Exception {
        HumanPlayer human = new HumanPlayer("Human", RangeOfInfluence.ONE, 1);
        HumanPlayer opponent = new HumanPlayer("Opponent", RangeOfInfluence.ONE, 1);
        Game game = createTestGame(human, opponent);

        StateEncoder encoder = new StateEncoder();
        encoder.setAgent(human.getId());
        encoder.setOpponent(opponent.getId());
        human.setRecorder(new PlayerRecorder(encoder));

        for (int i = 0; i < 5; i++) {
            int[] action = new int[128];
            action[i] = 1;
            encoder.addLabeledState(Set.of(i, i + 100), action, 0.1 * i, ActionEncoder.ActionType.PRIORITY, true);
        }

        Path tempDir = Files.createTempDirectory("rl_test_output_");
        RLTrainingDataCollector collector = new RLTrainingDataCollector(tempDir.toString());
        collector.onGameEnd(game);

        File[] files = tempDir.toFile().listFiles((dir, name) -> name.endsWith(".hdf5"));
        assertNotNull(files, "Output directory should exist");
        assertEquals(2, files.length, "Recorder should write player-A and player-B files");

        File playerAFile = null;
        for (File file : files) {
            try (IHDF5Reader reader = HDF5Factory.openForReading(file)) {
                float[][] rows = reader.float32().readMatrix("/row");
                if (rows.length == 5) {
                    playerAFile = file;
                    assertTrue(rows[0][128] < 0, "Labels should be negative when player.hasWon() is false");
                }
            }
        }
        assertNotNull(playerAFile, "One output file should contain the recorded states");
        assertNull(opponent.getRecorder(), "Opponent should remain without a recorder");

        for (File file : files) {
            file.delete();
        }
        tempDir.toFile().delete();
    }

    @Test
    void testCollectorSkipsPlayersWithoutRecordedStates() throws Exception {
        HumanPlayer human = new HumanPlayer("Human", RangeOfInfluence.ONE, 1);
        HumanPlayer opponent = new HumanPlayer("Opponent", RangeOfInfluence.ONE, 1);
        Game game = createTestGame(human, opponent);

        human.setRecorder(new PlayerRecorder(new StateEncoder()));

        Path tempDir = Files.createTempDirectory("rl_empty_test_");
        RLTrainingDataCollector collector = new RLTrainingDataCollector(tempDir.toString());
        collector.onGameEnd(game);

        File[] files = tempDir.toFile().listFiles();
        assertTrue(files == null || files.length == 0, "No files should be created for an empty recorder");

        tempDir.toFile().delete();
    }

    private Game createTestGame(mage.players.Player playerA, mage.players.Player playerB) throws Exception {
        MatchOptions matchOptions = new MatchOptions("test", "test", false);
        Match match = new mage.game.TwoPlayerMatch(matchOptions);
        Game game = new TwoPlayerDuel(
                MultiplayerAttackOption.LEFT,
                RangeOfInfluence.ONE,
                MulliganType.GAME_DEFAULT.getMulligan(0),
                60,
                20,
                7
        );

        DeckCardLists listA = DeckImporter.importDeckFromFile(TEST_DECK, true);
        Deck deckA = Deck.load(listA, false, false);
        DeckCardLists listB = DeckImporter.importDeckFromFile(TEST_DECK, true);
        Deck deckB = Deck.load(listB, false, false);

        game.loadCards(deckA.getCards(), playerA.getId());
        game.addPlayer(playerA, deckA);
        match.addPlayer(playerA, deckA);

        game.loadCards(deckB.getCards(), playerB.getId());
        game.addPlayer(playerB, deckB);
        match.addPlayer(playerB, deckB);

        return game;
    }

    @AfterAll
    static void cleanup() {
        CardRepository.instance.closeDB(true);
    }
}
