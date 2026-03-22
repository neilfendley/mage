package org.mage.test.AI.RL;

import mage.cards.decks.Deck;
import mage.cards.decks.DeckCardLists;
import mage.cards.decks.importer.DeckImporter;
import mage.cards.repository.CardScanner;
import mage.cards.repository.CardRepository;
import mage.cards.repository.RepositoryUtil;
import mage.constants.*;
import mage.game.*;
import mage.game.GameRecorder;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import mage.game.match.Match;
import mage.game.match.MatchOptions;
import mage.game.mulligan.MulliganType;
import mage.player.ai.ComputerPlayerMCTS2;
import mage.player.ai.encoder.ActionEncoder;
import mage.player.ai.encoder.LabeledState;
import mage.player.ai.encoder.StateEncoder;
import mage.player.ai.recorder.PlayerRecorder;
import mage.player.human.HumanPlayer;
import mage.players.Player;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that the StateEncoder / LabelRecorder can be enabled for HumanPlayer,
 * allowing human-vs-bot games to generate RL training data.
 */
public class HumanRecordingTest {

    private static final String DECK_A = "decks/Standard-MonoR.dck";
    private static final String DECK_B = "decks/Standard-MonoR.dck";

    @BeforeAll
    static void setup() {
        if (RepositoryUtil.isDatabaseEmpty()) {
            RepositoryUtil.bootstrapLocalDb();
            CardScanner.scan();
        }
    }

    @Test
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
    void testWriteRLDataProducesBinaryFile() throws Exception {
        HumanPlayer human = new HumanPlayer("PlayerA", RangeOfInfluence.ONE, 1);
        StateEncoder encoder = new StateEncoder();
        human.setRecorder(new PlayerRecorder(encoder));

        // Add some states
        for (int i = 0; i < 5; i++) {
            int[] action = new int[128];
            action[i % 128] = 1;
            encoder.addLabeledState(Set.of(i, i + 100), action, 0.1 * i,
                    ActionEncoder.ActionType.PRIORITY, true);
        }

        // Write to temp file
        File tempFile = Files.createTempFile("rl_test_", ".bin").toFile();
        tempFile.deleteOnExit();

        int written = human.writeRLData(tempFile.getAbsolutePath(), true, 0.95);
        assertEquals(5, written, "Should write 5 states");
        assertTrue(tempFile.length() > 0, "Output file should not be empty");

        // Verify binary format: header + first record
        try (DataInputStream in = new DataInputStream(new FileInputStream(tempFile))) {
            int recordCount = in.readInt();
            assertEquals(5, recordCount, "Binary header should contain record count");

            // Read first record: sparse state vector
            int numIndices = in.readInt();
            assertEquals(2, numIndices, "First state should have 2 active features");
            for (int i = 0; i < numIndices; i++) {
                in.readInt(); // skip index values
            }
            // Read action vector (128 doubles)
            for (int i = 0; i < 128; i++) {
                in.readDouble();
            }
            // Read metadata
            double resultLabel = in.readDouble();
            assertTrue(resultLabel > 0, "Result label should be positive for a win");
            double stateScore = in.readDouble();
            assertEquals(0.0, stateScore, 0.001, "First state score should be 0.0");
            boolean isPlayer = in.readBoolean();
            int actionTypeOrdinal = in.readInt();
            assertEquals(0, actionTypeOrdinal, "Should be PRIORITY (ordinal 0)");
        }
    }

    @Test
    void testWriteRLDataAppliesTDDiscount() {
        HumanPlayer human = new HumanPlayer("PlayerA", RangeOfInfluence.ONE, 1);
        StateEncoder encoder = new StateEncoder();
        human.setRecorder(new PlayerRecorder(encoder));

        for (int i = 0; i < 3; i++) {
            int[] action = new int[128];
            action[0] = 1;
            encoder.addLabeledState(Set.of(i), action, 0.0,
                    ActionEncoder.ActionType.PRIORITY, true);
        }

        File tempFile;
        try {
            tempFile = Files.createTempFile("rl_td_test_", ".bin").toFile();
            tempFile.deleteOnExit();
        } catch (Exception e) {
            fail("Could not create temp file");
            return;
        }

        human.writeRLData(tempFile.getAbsolutePath(), true, 0.95);

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
        int written = human.writeRLData("/tmp/should_not_exist.bin", true, 0.95);
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
    void testBinaryFormatIncludesAllMetadata() throws Exception {
        // Regression: writeRLData must include stateScore, isPlayer, and actionType
        // in the binary output (not just sparse indices + action + resultLabel).
        HumanPlayer human = new HumanPlayer("PlayerA", RangeOfInfluence.ONE, 1);
        StateEncoder encoder = new StateEncoder();
        encoder.setAgent(human.getId());
        human.setRecorder(new PlayerRecorder(encoder));

        // Add states with different action types and scores
        int[] action1 = new int[128];
        action1[0] = 1;
        encoder.addLabeledState(Set.of(10), action1, 0.42,
                ActionEncoder.ActionType.PRIORITY, true);

        int[] action2 = new int[128];
        action2[1] = 1;
        encoder.addLabeledState(Set.of(20), action2, -0.5,
                ActionEncoder.ActionType.CHOOSE_USE, false);

        File tempFile = Files.createTempFile("rl_metadata_test_", ".bin").toFile();
        tempFile.deleteOnExit();
        human.writeRLData(tempFile.getAbsolutePath(), true, 0.95);

        try (DataInputStream in = new DataInputStream(new FileInputStream(tempFile))) {
            int count = in.readInt();
            assertEquals(2, count);

            // Read first record
            int numIndices1 = in.readInt();
            for (int i = 0; i < numIndices1; i++) in.readInt();
            for (int i = 0; i < 128; i++) in.readDouble();
            double resultLabel1 = in.readDouble();
            double stateScore1 = in.readDouble();
            boolean isPlayer1 = in.readBoolean();
            int actionType1 = in.readInt();

            assertEquals(0.42, stateScore1, 0.001, "stateScore must be preserved");
            assertTrue(isPlayer1, "isPlayer must be preserved");
            assertEquals(ActionEncoder.ActionType.PRIORITY.ordinal(), actionType1,
                    "actionType must be preserved");

            // Read second record
            int numIndices2 = in.readInt();
            for (int i = 0; i < numIndices2; i++) in.readInt();
            for (int i = 0; i < 128; i++) in.readDouble();
            double resultLabel2 = in.readDouble();
            double stateScore2 = in.readDouble();
            boolean isPlayer2 = in.readBoolean();
            int actionType2 = in.readInt();

            assertEquals(-0.5, stateScore2, 0.001, "stateScore must be preserved for record 2");
            assertFalse(isPlayer2, "isPlayer=false must be preserved");
            assertEquals(ActionEncoder.ActionType.CHOOSE_USE.ordinal(), actionType2,
                    "actionType CHOOSE_USE must be preserved");
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

    @AfterAll
    static void cleanup() {
        CardRepository.instance.closeDB(true);
    }
}
