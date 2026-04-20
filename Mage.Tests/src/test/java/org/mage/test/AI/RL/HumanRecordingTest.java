package org.mage.test.AI.RL;

<<<<<<< HEAD
import mage.cards.decks.Deck;
import mage.cards.decks.DeckCardLists;
import mage.cards.decks.importer.DeckImporter;
import mage.cards.repository.CardScanner;
import mage.cards.repository.CardRepository;
import mage.cards.repository.RepositoryUtil;
import mage.collectors.services.RLTrainingDataCollector;
import mage.constants.*;
import mage.game.*;
import mage.game.match.Match;
import mage.game.match.MatchOptions;
import mage.game.mulligan.MulliganType;
import mage.player.ai.ComputerPlayerMCTS2;
import mage.player.ai.encoder.ActionEncoder;
import mage.player.ai.encoder.LabeledState;
import mage.player.ai.encoder.StateEncoder;
import mage.player.ai.recorder.PlayerRecorder;
import mage.player.human.HumanPlayer;
import org.junit.jupiter.api.*;

import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
=======
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

>>>>>>> 257d88b400b8488c0398092ba9281d8c2dba4616
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
<<<<<<< HEAD
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that the StateEncoder / LabelRecorder can be enabled for HumanPlayer,
 * allowing human-vs-bot games to generate RL training data.
 */
public class HumanRecordingTest {

    private static final String DECK_A = "decks/Standard-MonoR.dck";
    private static final String DECK_B = "decks/Standard-MonoR.dck";
=======

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HumanRecordingTest {

    private static final String TEST_DECK = "Mage.Tests/decks/Standard-MonoR.dck";
>>>>>>> 257d88b400b8488c0398092ba9281d8c2dba4616

    @BeforeAll
    static void setup() {
        if (RepositoryUtil.isDatabaseEmpty()) {
            RepositoryUtil.bootstrapLocalDb();
            CardScanner.scan();
        }
    }

    @Test
<<<<<<< HEAD
    void testEnableRLRecordingOnHumanPlayer() {
        HumanPlayer human = new HumanPlayer("TestHuman", RangeOfInfluence.ONE, 1);
        assertFalse(human.isRlRecordingEnabled(), "Recording should be disabled by default");
        assertNull(human.getRecorder(), "Recorder should be null by default");

        StateEncoder encoder = new StateEncoder();
        PlayerRecorder recorder = new PlayerRecorder(encoder);
        human.setRecorder(recorder);

        assertTrue(human.isRlRecordingEnabled(), "Recording should be enabled after setRecorder()");
        assertNotNull(human.getRecorder(), "Recorder should be set");
        assertSame(recorder, human.getRecorder(), "Should return the same recorder that was set");
        assertSame(encoder, recorder.getStateEncoder(), "Recorder should wrap the encoder");
    }

    @Test
    void testStateEncoderAccumulatesStates() {
        StateEncoder encoder = new StateEncoder();
        assertTrue(encoder.labeledStates.isEmpty(), "Encoder should start with no states");

        // Manually add a labeled state (simulating what activateAbility does)
        Set<Integer> fakeState = Set.of(1, 2, 3, 100, 500);
        int[] fakeAction = new int[128];
        fakeAction[0] = 1; // one-hot
        encoder.addLabeledState(fakeState, fakeAction, 0.0, ActionEncoder.ActionType.PRIORITY, true);

        assertEquals(1, encoder.labeledStates.size(), "Should have one labeled state");

        LabeledState ls = encoder.labeledStates.get(0);
        assertEquals(fakeState, ls.stateVector, "State vector should match");
        assertEquals(1.0, ls.actionVector[0], 0.001, "Action vector[0] should be 1.0 (one-hot)");
        assertEquals(0.0, ls.stateScore, 0.001, "Score should be 0.0 for human actions");
        assertTrue(ls.isPlayer, "isPlayer should be true for PlayerA");
        assertEquals(ActionEncoder.ActionType.PRIORITY, ls.actionType, "Should be PRIORITY action type");
    }

    @Test
    void testManualEncoderWiringMatchesRLInitLogic() {
        // Tests the same wiring that RLInit performs when it detects a human opponent.
        // We test this directly because RLInit requires a fully started game (getOpponent
        // checks player range), but the wiring logic itself is straightforward.
        HumanPlayer human = new HumanPlayer("PlayerB", RangeOfInfluence.ONE, 1);
        ComputerPlayerMCTS2 bot = new ComputerPlayerMCTS2("PlayerA", RangeOfInfluence.ONE, 6);

        // Verify precondition
        assertFalse(human.isRlRecordingEnabled());
        assertTrue(human.isHuman(), "HumanPlayer should report isHuman()=true");

        // Replicate what RLInit does when opponent.isHuman()
        StateEncoder humanEncoder = new StateEncoder();
        humanEncoder.setAgent(human.getId());
        humanEncoder.setOpponent(bot.getId());
        PlayerRecorder recorder = new PlayerRecorder(humanEncoder);
        human.setRecorder(recorder);

        // Verify the wiring
        assertTrue(human.isRlRecordingEnabled(),
                "Recording should be enabled after wiring");
        assertEquals(human.getId(), recorder.getStateEncoder().getMyPlayerId(),
                "Human encoder's agent should be the human's ID");

        // Verify recording works
        Set<Integer> state = Set.of(1, 2, 3);
        int[] action = new int[128];
        action[5] = 1;
        recorder.getStateEncoder().addLabeledState(state, action, 0.0,
                ActionEncoder.ActionType.PRIORITY, false);
        assertEquals(1, recorder.getStateEncoder().labeledStates.size(),
                "Should accumulate states after wiring");
    }

    @Test
    void testMultipleStatesAccumulate() {
        StateEncoder encoder = new StateEncoder();

        // Simulate multiple priority decisions
        for (int i = 0; i < 10; i++) {
            Set<Integer> state = Set.of(i, i + 100, i + 200);
            int[] action = new int[128];
            action[i % 128] = 1;
            encoder.addLabeledState(state, action, 0.0, ActionEncoder.ActionType.PRIORITY, true);
        }

        assertEquals(10, encoder.labeledStates.size(), "Should accumulate all 10 states");

        // Verify each state is distinct
        for (int i = 0; i < 10; i++) {
            LabeledState ls = encoder.labeledStates.get(i);
            assertTrue(ls.stateVector.contains(i), "State " + i + " should contain feature " + i);
        }
    }

    @Test
    void testTDDiscountPostProcessing() {
        // Test the same TD-discount logic used by ParallelDataGenerator
        StateEncoder encoder = new StateEncoder();

        // Add some states with scores
        for (int i = 0; i < 5; i++) {
            Set<Integer> state = Set.of(i);
            int[] action = new int[128];
            action[0] = 1;
            encoder.addLabeledState(state, action, 0.1 * i, ActionEncoder.ActionType.PRIORITY, true);
        }

        // Apply TD-discount (same logic as ParallelDataGenerator.generateLabeledStatesForGame)
        double tdDiscount = 0.95;
        boolean playerWon = true;
        int N = encoder.labeledStates.size();
        double discountedFuture = playerWon ? 1.0 : -1.0;
        for (int i = N - 1; i >= 0; i--) {
            discountedFuture = (tdDiscount * discountedFuture)
                    + (encoder.labeledStates.get(i).stateScore * (1 - tdDiscount));
            encoder.labeledStates.get(i).resultLabel = discountedFuture;
        }

        // Verify labels were set
        for (int i = 0; i < N; i++) {
            LabeledState ls = encoder.labeledStates.get(i);
            assertNotEquals(0.0, ls.resultLabel, "Result label should be set after TD processing");
        }

        // Last state should be closest to the win signal (1.0)
        assertTrue(encoder.labeledStates.get(N - 1).resultLabel > encoder.labeledStates.get(0).resultLabel,
                "Later states should have higher result labels for a winning game");
    }

    @Test
    void testPassActionRecordsAtIndexZero() {
        // Pass is index 0 in ActionEncoder. When the human passes priority,
        // a one-hot at index 0 should be recorded.
        StateEncoder encoder = new StateEncoder();
        HumanPlayer human = new HumanPlayer("PlayerA", RangeOfInfluence.ONE, 1);
        human.setRecorder(new PlayerRecorder(encoder));

        // Simulate what recordPassAction does internally
        Set<Integer> state = Set.of(10, 20, 30);
        int[] passAction = new int[128];
        passAction[0] = 1; // Pass = index 0
        encoder.addLabeledState(state, passAction, 0.0, ActionEncoder.ActionType.PRIORITY, true);

        assertEquals(1, encoder.labeledStates.size());
        LabeledState ls = encoder.labeledStates.get(0);
        assertEquals(1.0, ls.actionVector[0], 0.001, "Pass action should be at index 0");
        for (int i = 1; i < 128; i++) {
            assertEquals(0.0, ls.actionVector[i], 0.001, "Non-pass indices should be 0");
        }
    }

    @Test
    void testMixedActionsAndPassesAccumulate() {
        StateEncoder encoder = new StateEncoder();
        ActionEncoder actionEncoder = new ActionEncoder();

        // Record a pass
        int[] passAction = new int[128];
        passAction[0] = 1;
        encoder.addLabeledState(Set.of(1), passAction, 0.0, ActionEncoder.ActionType.PRIORITY, true);

        // Record an ability activation (non-zero index)
        int[] abilityAction = new int[128];
        abilityAction[42] = 1;
        encoder.addLabeledState(Set.of(2), abilityAction, 0.0, ActionEncoder.ActionType.PRIORITY, true);

        // Record another pass
        int[] passAction2 = new int[128];
        passAction2[0] = 1;
        encoder.addLabeledState(Set.of(3), passAction2, 0.0, ActionEncoder.ActionType.PRIORITY, true);

        assertEquals(3, encoder.labeledStates.size(), "Should have 3 recorded states");
        assertEquals(1.0, encoder.labeledStates.get(0).actionVector[0], 0.001, "First should be pass");
        assertEquals(1.0, encoder.labeledStates.get(1).actionVector[42], 0.001, "Second should be ability");
        assertEquals(1.0, encoder.labeledStates.get(2).actionVector[0], 0.001, "Third should be pass");
    }

    @Test
    void testChooseUseRecordsCorrectActionType() {
        StateEncoder encoder = new StateEncoder();

        // Simulate a "yes" chooseUse decision
        int[] yesAction = new int[128];
        yesAction[1] = 1; // yes = index 1
        encoder.addLabeledState(Set.of(10), yesAction, 0.0,
                ActionEncoder.ActionType.CHOOSE_USE, true);

        // Simulate a "no" chooseUse decision
        int[] noAction = new int[128];
        noAction[0] = 1; // no = index 0
        encoder.addLabeledState(Set.of(20), noAction, 0.0,
                ActionEncoder.ActionType.CHOOSE_USE, true);

        assertEquals(2, encoder.labeledStates.size());
        assertEquals(ActionEncoder.ActionType.CHOOSE_USE, encoder.labeledStates.get(0).actionType);
        assertEquals(1.0, encoder.labeledStates.get(0).actionVector[1], 0.001, "Yes should be index 1");
        assertEquals(1.0, encoder.labeledStates.get(1).actionVector[0], 0.001, "No should be index 0");
    }

    @Test
    void testChooseTargetRecordsCorrectActionType() {
        StateEncoder encoder = new StateEncoder();
        ActionEncoder actionEncoder = new ActionEncoder();

        // Simulate a target choice
        String targetName = "Goblin Guide";
        int idx = actionEncoder.getTargetIndex(targetName);
        int[] action = new int[128];
        action[idx] = 1;
        encoder.addLabeledState(Set.of(5, 10), action, 0.0,
                ActionEncoder.ActionType.CHOOSE_TARGET, true);

        assertEquals(1, encoder.labeledStates.size());
        assertEquals(ActionEncoder.ActionType.CHOOSE_TARGET, encoder.labeledStates.get(0).actionType);
        assertEquals(1.0, encoder.labeledStates.get(0).actionVector[idx], 0.001);
    }

    @Test
    void testMakeChoiceRecordsCorrectActionType() {
        StateEncoder encoder = new StateEncoder();

        String choiceText = "Red";
        int idx = (Math.abs(choiceText.hashCode()) % 127) + 1;
        int[] action = new int[128];
        action[idx] = 1;
        encoder.addLabeledState(Set.of(1), action, 0.0,
                ActionEncoder.ActionType.MAKE_CHOICE, true);

        assertEquals(1, encoder.labeledStates.size());
        assertEquals(ActionEncoder.ActionType.MAKE_CHOICE, encoder.labeledStates.get(0).actionType);
        assertEquals(1.0, encoder.labeledStates.get(0).actionVector[idx], 0.001);
    }

    @Test
    void testWriteRLDataProducesHDF5File() throws Exception {
        HumanPlayer human = new HumanPlayer("PlayerA", RangeOfInfluence.ONE, 1);
        StateEncoder encoder = new StateEncoder();
        human.setRecorder(new PlayerRecorder(encoder));

        for (int i = 0; i < 5; i++) {
            int[] action = new int[128];
            action[i % 128] = 1;
            encoder.addLabeledState(Set.of(i, i + 100), action, 0.1 * i,
                    ActionEncoder.ActionType.PRIORITY, true);
        }

        Path tempDir = Files.createTempDirectory("rl_test_");
        tempDir.toFile().deleteOnExit();
        File outputFile = tempDir.resolve("outputa.hdf5").toFile();
        File outputFileb = tempDir.resolve("outputb.hdf5").toFile();

        int written = human.writeRLData(outputFile.getAbsolutePath(), outputFileb.getAbsolutePath(), true, 0.95);
        assertEquals(5, written, "Should write 5 states");
        assertTrue(outputFile.exists() && outputFile.length() > 0, "Output file should exist and be non-empty");

        try (IHDF5Reader reader = HDF5Factory.openForReading(outputFile)) {
            assertTrue(reader.exists("/indices"));
            assertTrue(reader.exists("/offsets"));
            assertTrue(reader.exists("/row"));

            long[] offsets = reader.int64().readArray("/offsets");
            assertEquals(6, offsets.length, "Should have 5+1 offsets");

            float[][] rows = reader.float32().readMatrix("/row");
            assertEquals(5, rows.length, "Should have 5 rows");
            assertEquals(132, rows[0].length, "Each row should be 128 actions + 4 metadata");
            assertTrue(rows[0][128] > 0, "Result label should be positive for a win");
        }
    }

    @Test
    void testWriteRLDataAppliesTDDiscount() throws Exception {
        HumanPlayer human = new HumanPlayer("PlayerA", RangeOfInfluence.ONE, 1);
        StateEncoder encoder = new StateEncoder();
        human.setRecorder(new PlayerRecorder(encoder));

        for (int i = 0; i < 3; i++) {
            int[] action = new int[128];
            action[0] = 1;
            encoder.addLabeledState(Set.of(i), action, 0.0,
                    ActionEncoder.ActionType.PRIORITY, true);
        }

        Path tempDir = Files.createTempDirectory("rl_td_test_");
        tempDir.toFile().deleteOnExit();

        human.writeRLData(tempDir.resolve("outputa.hdf5").toString(), tempDir.resolve("outputb.hdf5").toString(), true, 0.95);

        // After writing, the encoder's states should have resultLabels set
        for (LabeledState ls : encoder.labeledStates) {
            assertTrue(ls.resultLabel > 0, "Result labels should be positive for a win");
        }
        // Last state should have highest label (closest to the win)
        assertTrue(encoder.labeledStates.get(2).resultLabel > encoder.labeledStates.get(0).resultLabel);
    }

    @Test
    void testWriteRLDataReturnsZeroWhenDisabled() {
        HumanPlayer human = new HumanPlayer("PlayerA", RangeOfInfluence.ONE, 1);
        // Recording not enabled
        int written = human.writeRLData("/tmp/should_not_exist.hdf5","/tmp/should_not_exist.hdf5", true, 0.95);

        assertEquals(0, written, "Should return 0 when recording is disabled");
    }

    // === Regression tests for codex review findings ===

    @Test
    void testCopyConstructorPreservesRLState() {
        // Regression: copy constructor must preserve stateEncoder, actionEncoder, rlRecordingEnabled
        // so that checkpoint/rollback doesn't silently drop recording.
        HumanPlayer original = new HumanPlayer("TestPlayer", RangeOfInfluence.ONE, 1);
        StateEncoder encoder = new StateEncoder();
        original.setRecorder(new PlayerRecorder(encoder));

        // Add a state before copying
        int[] action = new int[128];
        action[5] = 1;
        encoder.addLabeledState(Set.of(1, 2), action, 0.0, ActionEncoder.ActionType.PRIORITY, true);

        // Copy the player (simulates checkpoint)
        HumanPlayer copy = new HumanPlayer(original);

        assertTrue(copy.isRlRecordingEnabled(), "Copy should preserve recorder");
        assertSame(original.getRecorder(), copy.getRecorder(),
                "Copy should share the same recorder instance");
        PlayerRecorder copyRecorder = (PlayerRecorder) copy.getRecorder();
        assertEquals(1, copyRecorder.getStateEncoder().labeledStates.size(),
                "Copy should see states accumulated before the copy");

        // Add a state through the copy — should be visible from original's encoder too
        int[] action2 = new int[128];
        action2[10] = 1;
        copyRecorder.getStateEncoder().addLabeledState(Set.of(3, 4), action2, 0.0,
                ActionEncoder.ActionType.PRIORITY, true);
        assertEquals(2, encoder.labeledStates.size(),
                "States added via copy should accumulate in the shared encoder");
    }

    @Test
    void testIsPlayerUsesIdNotName() {
        // Regression: isPlayer must be based on player ID, not the literal name "PlayerA".
        // A human player named "Alice" should still get correct isPlayer labeling
        // across all recorder methods.
        HumanPlayer human = new HumanPlayer("Alice", RangeOfInfluence.ONE, 1);
        UUID opponentId = UUID.randomUUID();
        StateEncoder encoder = new StateEncoder();
        encoder.setAgent(human.getId());
        encoder.setOpponent(opponentId);
        PlayerRecorder recorder = new PlayerRecorder(encoder);
        human.setRecorder(recorder);

        Set<Integer> fakeState = Set.of(1, 2, 3);

        // Test recordChooseUse passes playerId correctly
        recorder.recordChooseUse(fakeState, true, human.getId());
        assertTrue(encoder.labeledStates.get(0).isPlayer,
                "chooseUse: isPlayer should be true for the agent's own ID");

        recorder.recordChooseUse(fakeState, false, opponentId);
        assertFalse(encoder.labeledStates.get(1).isPlayer,
                "chooseUse: isPlayer should be false for opponent ID");

        // Test recordMakeChoice passes playerId correctly
        recorder.recordMakeChoice(fakeState, "Red", human.getId());
        assertTrue(encoder.labeledStates.get(2).isPlayer,
                "makeChoice: isPlayer should be true for the agent's own ID");

        recorder.recordMakeChoice(fakeState, "Red", opponentId);
        assertFalse(encoder.labeledStates.get(3).isPlayer,
                "makeChoice: isPlayer should be false for opponent ID");
    }

    @Test
    void testWriteRLDataPreservesMetadataInEncoder() throws Exception {
        // Regression: writeRLData must apply TD-discount and set resultLabels.
        // Metadata (stateScore, isPlayer, actionType) is preserved in the encoder's states.
        HumanPlayer human = new HumanPlayer("PlayerA", RangeOfInfluence.ONE, 1);
        StateEncoder encoder = new StateEncoder();
        encoder.setAgent(human.getId());
        human.setRecorder(new PlayerRecorder(encoder));

        int[] action1 = new int[128];
        action1[0] = 1;
        encoder.addLabeledState(Set.of(10), action1, 0.42,
                ActionEncoder.ActionType.PRIORITY, true);

        int[] action2 = new int[128];
        action2[1] = 1;
        encoder.addLabeledState(Set.of(20), action2, -0.5,
                ActionEncoder.ActionType.CHOOSE_USE, false);

        Path tempDir = Files.createTempDirectory("rl_metadata_test_");
        tempDir.toFile().deleteOnExit();
        File outputFile = tempDir.resolve("output.hdf5").toFile();
        File outputFileb = tempDir.resolve("output.b.hdf5").toFile();
        int written = human.writeRLData(outputFile.getAbsolutePath(), outputFileb.getAbsolutePath(), true, 0.95);
        assertEquals(2, written, "Should write 2 states");

        try (IHDF5Reader reader = HDF5Factory.openForReading(outputFile)) {
            float[][] rows = reader.float32().readMatrix("/row");
            assertEquals(2, rows.length, "Should have 2 rows");

            // Row format: [action(128), resultLabel, stateScore, isPlayer, actionType]
            assertEquals(0.42f, rows[0][129], 0.01f, "stateScore must be preserved");
            assertEquals(1f, rows[0][130], 0.01f, "isPlayer=true must be 1.0");
            assertEquals(ActionEncoder.ActionType.PRIORITY.ordinal(), (int) rows[0][131]);

            assertEquals(-0.5f, rows[1][129], 0.01f, "stateScore must be preserved for record 2");
            assertEquals(0f, rows[1][130], 0.01f, "isPlayer=false must be 0.0");
            assertEquals(ActionEncoder.ActionType.CHOOSE_USE.ordinal(), (int) rows[1][131]);
        }
    }

    @Test
    void testStopChoosingRecordedForPartialTargetSelection() {
        // Regression: when target selection is left open (partial/cancelable),
        // a "Stop Choosing" action at index 0 must be recorded.
        StateEncoder encoder = new StateEncoder();
        ActionEncoder actionEncoder = new ActionEncoder();

        // Simulate: one concrete target + stop choosing
        Set<Integer> state = Set.of(1, 2, 3);

        // Record a target
        String targetName = "Lightning Bolt";
        int targetIdx = actionEncoder.getTargetIndex(targetName);
        int[] targetAction = new int[128];
        targetAction[targetIdx] = 1;
        encoder.addLabeledState(state, targetAction, 0.0,
                ActionEncoder.ActionType.CHOOSE_TARGET, true);

        // Record stop choosing
        int[] stopAction = new int[128];
        stopAction[0] = 1; // index 0 = "Stop Choosing"
        encoder.addLabeledState(state, stopAction, 0.0,
                ActionEncoder.ActionType.CHOOSE_TARGET, true);

        assertEquals(2, encoder.labeledStates.size(),
                "Should record both the target and the stop-choosing action");
        assertEquals(ActionEncoder.ActionType.CHOOSE_TARGET, encoder.labeledStates.get(0).actionType);
        assertEquals(ActionEncoder.ActionType.CHOOSE_TARGET, encoder.labeledStates.get(1).actionType);
        assertEquals(1.0, encoder.labeledStates.get(1).actionVector[0], 0.001,
                "Stop Choosing should be at index 0");
    }

    @Test
    void testUndoTrimsStatesRecordedAfterCheckpoint() {
        // Regression: undo must not leave stale policy labels from undone actions.
        // The copy constructor saves checkpoint size; priority() trims on restore.
        HumanPlayer original = new HumanPlayer("TestPlayer", RangeOfInfluence.ONE, 1);
        StateEncoder encoder = new StateEncoder();
        original.setRecorder(new PlayerRecorder(encoder));

        // Record 3 states before checkpoint
        for (int i = 0; i < 3; i++) {
            int[] action = new int[128];
            action[i] = 1;
            encoder.addLabeledState(Set.of(i), action, 0.0, ActionEncoder.ActionType.PRIORITY, true);
        }
        assertEquals(3, encoder.labeledStates.size());

        // Simulate checkpoint (copy constructor saves size=3)
        HumanPlayer checkpointCopy = new HumanPlayer(original);

        // Record 2 more states after checkpoint (these should be undone)
        int[] action4 = new int[128];
        action4[10] = 1;
        encoder.addLabeledState(Set.of(10), action4, 0.0, ActionEncoder.ActionType.PRIORITY, true);
        int[] action5 = new int[128];
        action5[11] = 1;
        encoder.addLabeledState(Set.of(11), action5, 0.0, ActionEncoder.ActionType.PRIORITY, true);
        assertEquals(5, encoder.labeledStates.size(), "Should have 5 states before undo");

        // Simulate undo: the checkpointCopy becomes the active player.
        // Its priority() call should trim states back to checkpoint size (3).
        // We can't call priority() without a full game, so test the trimming logic directly.
        GameRecorder restoredRecorder = checkpointCopy.getRecorder();
        assertSame(original.getRecorder(), restoredRecorder, "Should share same recorder");

        // Replicate what priority() does on restore
        // (checkpointCopy has rlCheckpointSize=3, recorder has 5 states)
        if (restoredRecorder.getRecordedStateCount() > 3) {
            restoredRecorder.trimToCheckpoint(3);
        }

        assertEquals(3, encoder.labeledStates.size(),
                "Undo should trim states back to checkpoint size");
        // Verify the remaining states are the original ones
        for (int i = 0; i < 3; i++) {
            assertTrue(encoder.labeledStates.get(i).stateVector.contains(i),
                    "Remaining states should be the pre-checkpoint ones");
        }
    }

    // === Integration test: full game-end data collection path ===

    /** Helper to set up a game with two players for integration tests. */
    private Game createTestGame(HumanPlayer playerA, HumanPlayer playerB) throws Exception {
        MatchOptions matchOptions = new MatchOptions("test", "test", false);
        Match match = new TwoPlayerMatch(matchOptions);
        Game game = new TwoPlayerDuel(
                MultiplayerAttackOption.LEFT,
                RangeOfInfluence.ONE,
                MulliganType.GAME_DEFAULT.getMulligan(0),
                60, 20, 7
        );
        DeckCardLists listA = DeckImporter.importDeckFromFile(DECK_A, true);
        Deck deckA = Deck.load(listA, false, false);
        DeckCardLists listB = DeckImporter.importDeckFromFile(DECK_B, true);
        Deck deckB = Deck.load(listB, false, false);
        game.loadCards(deckA.getCards(), playerA.getId());
        game.addPlayer(playerA, deckA);
        match.addPlayer(playerA, deckA);
        game.loadCards(deckB.getCards(), playerB.getId());
        game.addPlayer(playerB, deckB);
        match.addPlayer(playerB, deckB);
        return game;
    }

    @Test
    void testRLTrainingDataCollectorWritesFileOnGameEnd() throws Exception {
        HumanPlayer human = new HumanPlayer("TestHuman", RangeOfInfluence.ONE, 1);
        HumanPlayer opponent = new HumanPlayer("TestOpponent", RangeOfInfluence.ONE, 1);
        Game game = createTestGame(human, opponent);

        // Attach recorder with states
=======
    void testImportDeckFromFileBackfillsDeckNameFromPath() {
        DeckCardLists deck = DeckImporter.importDeckFromFile(TEST_DECK, true);
        assertEquals("Standard-MonoR", deck.getName(), "Importer should derive the deck name from the file path");
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

>>>>>>> 257d88b400b8488c0398092ba9281d8c2dba4616
        StateEncoder encoder = new StateEncoder();
        encoder.setAgent(human.getId());
        encoder.setOpponent(opponent.getId());
        human.setRecorder(new PlayerRecorder(encoder));

        for (int i = 0; i < 5; i++) {
            int[] action = new int[128];
<<<<<<< HEAD
            action[i % 128] = 1;
            encoder.addLabeledState(Set.of(i, i + 100), action, 0.1 * i,
                    ActionEncoder.ActionType.PRIORITY, true);
        }

        // Point collector at a temp directory and call onGameEnd
        Path tempDir = Files.createTempDirectory("rl_test_output_");
        tempDir.toFile().deleteOnExit();
        RLTrainingDataCollector collector = new RLTrainingDataCollector(tempDir.toString());
        collector.onGameEnd(game);

        // Verify a .bin file was created in the output directory
        File[] files = tempDir.toFile().listFiles((dir, name) -> name.endsWith(".hdf5"));
        assertNotNull(files, "Output directory should exist");
        assertEquals(1, files.length, "Should produce exactly one .hdf5 file");

        File outputFile = files[0];
        assertTrue(outputFile.getName().startsWith("TestHuman_"),
                "Filename should start with player name");
        assertTrue(outputFile.length() > 0, "Output file should not be empty");

        // Verify HDF5 content
        try (IHDF5Reader reader = HDF5Factory.openForReading(outputFile)) {
            float[][] rows = reader.float32().readMatrix("/row");
            assertEquals(5, rows.length, "File should contain 5 records");
            // human.hasWon() is false (game never started), so labels should be negative
            assertTrue(rows[0][128] < 0, "Result label should be negative when player hasn't won");
        }

        // Verify opponent produced no file (no recorder)
        assertNull(opponent.getRecorder());

        // Clean up
        outputFile.delete();
=======
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
>>>>>>> 257d88b400b8488c0398092ba9281d8c2dba4616
        tempDir.toFile().delete();
    }

    @Test
<<<<<<< HEAD
    void testDataCollectorSkipsPlayersWithoutRecorder() throws Exception {
        HumanPlayer playerA = new HumanPlayer("PlayerA", RangeOfInfluence.ONE, 1);
        HumanPlayer playerB = new HumanPlayer("PlayerB", RangeOfInfluence.ONE, 1);
        Game game = createTestGame(playerA, playerB);

        // Point collector at a temp directory
        Path tempDir = Files.createTempDirectory("rl_skip_test_");
        tempDir.toFile().deleteOnExit();
        RLTrainingDataCollector collector = new RLTrainingDataCollector(tempDir.toString());

        // Neither player has a recorder
        collector.onGameEnd(game);

        // Verify no files were created
        File[] files = tempDir.toFile().listFiles();
        assertTrue(files == null || files.length == 0,
                "No files should be created when no players have recorders");

        tempDir.toFile().delete();
    }

    @Test
    void testDataCollectorSkipsRecorderWithZeroStates() throws Exception {
        HumanPlayer human = new HumanPlayer("TestHuman", RangeOfInfluence.ONE, 1);
        HumanPlayer opponent = new HumanPlayer("Opponent", RangeOfInfluence.ONE, 1);
        Game game = createTestGame(human, opponent);

        // Attach recorder but don't add any states
        human.setRecorder(new PlayerRecorder(new StateEncoder()));

        Path tempDir = Files.createTempDirectory("rl_empty_test_");
        tempDir.toFile().deleteOnExit();
        RLTrainingDataCollector collector = new RLTrainingDataCollector(tempDir.toString());
        collector.onGameEnd(game);

        // Verify no files were created
        File[] files = tempDir.toFile().listFiles();
        assertTrue(files == null || files.length == 0,
                "No files should be created when recorder has zero states");
=======
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
>>>>>>> 257d88b400b8488c0398092ba9281d8c2dba4616

        tempDir.toFile().delete();
    }

<<<<<<< HEAD
=======
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

>>>>>>> 257d88b400b8488c0398092ba9281d8c2dba4616
    @AfterAll
    static void cleanup() {
        CardRepository.instance.closeDB(true);
    }
}
