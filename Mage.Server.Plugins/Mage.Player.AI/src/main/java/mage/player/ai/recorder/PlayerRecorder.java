package mage.player.ai.recorder;

import mage.abilities.Ability;
import mage.abilities.ActivatedAbility;
import mage.cards.Cards;
import mage.game.Game;
import mage.game.GameRecorder;
import mage.player.ai.encoder.ActionEncoder;
import mage.player.ai.encoder.LabeledState;
import mage.player.ai.encoder.LabeledStateWriter;
import mage.player.ai.encoder.StateEncoder;
import mage.target.Target;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Date;


/**
 * Records game decisions for RL training data. Encapsulates all StateEncoder/ActionEncoder
 * logic so player implementations only need to depend on the GameRecorder interface.
 */
public class PlayerRecorder implements GameRecorder {

    private static final Logger logger = Logger.getLogger(PlayerRecorder.class);

    /** Register the factory so core/server code can create recorders without AI imports. */
    static {
        GameRecorder.Factory.register((playerId, opponentId) -> {
            StateEncoder encoder = new StateEncoder();
            encoder.setAgent(playerId);
            encoder.setOpponent(opponentId);
            return new PlayerRecorder(encoder);
        });
    }

    /** Force class loading to trigger the static initializer. */
    public static void ensureRegistered() {
        // no-op — loading this class registers the factory
    }

    private final StateEncoder stateEncoder;
    private final ActionEncoder actionEncoder;

    public PlayerRecorder(StateEncoder encoder) {
        this.stateEncoder = encoder;
        this.actionEncoder = new ActionEncoder();
    }

    public StateEncoder getStateEncoder() {
        return stateEncoder;
    }

    private boolean isPlayer(UUID playerId) {
        return stateEncoder.getMyPlayerId().equals(playerId);
    }

    private void addState(Set<Integer> stateVector, int[] actionVec,
                          ActionEncoder.ActionType actionType, UUID playerId) {
        stateEncoder.addLabeledState(stateVector, actionVec, 0.0, actionType, isPlayer(playerId));
        int count = stateEncoder.labeledStates.size();
        if (count <= 5 || count % 10 == 0) {
            logger.info("RL recorded " + actionType + " decision (total: " + count + " states)");
        }
    }

    @Override
    public Set<Integer> capturePreDecisionState(Game game, UUID playerId) {
        try {
            return stateEncoder.processState(game, playerId);
        } catch (Exception e) {
            logger.warn("Failed to capture pre-decision RL state: " + e.getMessage());
            return null;
        }
    }

    @Override
    public void recordChooseUse(Set<Integer> preDecisionState, boolean choice, UUID playerId) {
        int[] actionVec = new int[128];
        actionVec[choice ? 1 : 0] = 1;
        addState(preDecisionState, actionVec, ActionEncoder.ActionType.CHOOSE_USE, playerId);
    }

    @Override
    public void recordMakeChoice(Set<Integer> preDecisionState, String choiceText, UUID playerId) {
        int[] actionVec = new int[128];
        int idx = (Math.abs(choiceText.hashCode()) % 127) + 1;
        actionVec[idx] = 1;
        addState(preDecisionState, actionVec, ActionEncoder.ActionType.MAKE_CHOICE, playerId);
    }

    @Override
    public void recordTargetSelections(Set<Integer> preDecisionState, Target target,
                                       UUID abilityControllerId, Ability source, Game game, Cards cards, UUID playerId) {
        // Note: for multi-target selections, the human UI resolves all targets in one
        // blocking call, so we can't capture intermediate states between picks the way
        // the MCTS bot does. Each target is recorded against the pre-decision state.
        try {
            for (UUID targetId : target.getTargets()) {
                int[] actionVec = new int[128];
                String targetName = game.getObject(targetId) != null
                        ? game.getObject(targetId).getName() : targetId.toString();
                int idx = actionEncoder.getTargetIndex(targetName);
                actionVec[idx] = 1;
                addState(preDecisionState, actionVec, ActionEncoder.ActionType.CHOOSE_TARGET, playerId);
            }
            boolean choiceCompleted = cards != null
                    ? target.isChoiceCompleted(abilityControllerId, source, game, cards)
                    : target.isChoiceCompleted(abilityControllerId, source, game, null);
            if (!choiceCompleted) {
                int[] stopVec = new int[128];
                stopVec[0] = 1; // index 0 = "Stop Choosing" in ActionEncoder.targetMap
                addState(preDecisionState, stopVec, ActionEncoder.ActionType.CHOOSE_TARGET, playerId);
            }
        } catch (Exception e) {
            logger.warn("Failed to record RL state for chooseTarget: " + e.getMessage());
        }
    }

    @Override
    public void recordPassAction(Game game, UUID playerId) {
        Set<Integer> stateVector = capturePreDecisionState(game, playerId);
        if (stateVector != null) {
            int[] actionVec = new int[128];
            actionVec[0] = 1; // index 0 = Pass in ActionEncoder
            addState(stateVector, actionVec, ActionEncoder.ActionType.PRIORITY, playerId);
        }
    }

    @Override
    public void recordActivateAbility(Game game, UUID playerId, ActivatedAbility ability) {
        Set<Integer> stateVector = capturePreDecisionState(game, playerId);
        if (stateVector != null) {
            int[] actionVec = new int[128];
            actionVec[actionEncoder.getActionIndex(ability, false)] = 1;
            addState(stateVector, actionVec, ActionEncoder.ActionType.PRIORITY, playerId);
        }
    }

    @Override
    public int getRecordedStateCount() {
        return stateEncoder.labeledStates.size();
    }

    @Override
    public void trimToCheckpoint(int checkpointSize) {
        if (stateEncoder.labeledStates.size() > checkpointSize) {
            stateEncoder.labeledStates.subList(checkpointSize, stateEncoder.labeledStates.size()).clear();
        }
    }

    /**
     * Applies TD-discount to a list of labeled states, setting resultLabel on each.
     * Shared by both PlayerRecorder (human games) and ParallelDataGenerator (bot games).
     */
    public static void applyTDDiscount(List<LabeledState> states, boolean playerWon, double tdDiscount) {
        int N = states.size();
        double discountedFuture = playerWon ? 1.0 : -1.0;
        for (int i = N - 1; i >= 0; i--) {
            discountedFuture = (tdDiscount * discountedFuture)
                    + (states.get(i).stateScore * (1 - tdDiscount));
            states.get(i).resultLabel = discountedFuture;
        }
    }

    @Override
    public int writeRLData(String playerAPath, String playerBPath, boolean playerWon, double tdDiscount) {
        if (stateEncoder.labeledStates.isEmpty()) {
            return 0;
        }

        List<LabeledState> states = stateEncoder.labeledStates;
        applyTDDiscount(states, playerWon, tdDiscount);

        // Write HDF5 in the same CSR format as MageZero training pipeline
        // String hdf5Path = outputPath.endsWith(".hdf5") ? outputPath : outputPath.replace(".bin", ".hdf5");
        LabeledStateWriter fwA;
        LabeledStateWriter fwB;
        try {
            fwA = new LabeledStateWriter(playerAPath);
            fwB = new LabeledStateWriter(playerBPath);
        
            for (LabeledState state : states) {
                if (state.isPlayer){ 
                    fwA.writeRecord(state);
                } else {
                    fwB.writeRecord(state);
                }
            }
        
            fwA.close();
            fwB.close();
        } catch (IOException e) {
            logger.error("Failed to write RL training data files: " + e.getMessage());
        }
        logger.info("Wrote " + states.size() + " RL training states to " + playerAPath + " and " + playerBPath);
        return states.size();
        
    }
}
