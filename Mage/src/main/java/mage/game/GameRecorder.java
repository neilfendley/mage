package mage.game;

import mage.abilities.Ability;
import mage.abilities.ActivatedAbility;
import mage.cards.Cards;
import mage.target.Target;

import java.util.Set;
import java.util.UUID;

/**
 * Interface for recording game decisions for RL training data.
 * Implementations handle state encoding, action encoding, and data persistence.
 * Defined in core Mage so any player type can use it without AI module dependencies.
 */
public interface GameRecorder {

    Set<Integer> capturePreDecisionState(Game game, UUID playerId);

    void recordChooseUse(Set<Integer> preDecisionState, boolean choice, UUID playerId);

    void recordMakeChoice(Set<Integer> preDecisionState, String choiceText, UUID playerId);

    void recordTargetSelections(Set<Integer> preDecisionState, Target target,
                                UUID abilityControllerId, Ability source, Game game, Cards cards, UUID playerId);

    void recordPassAction(Game game, UUID playerId);

    void recordActivateAbility(Game game, UUID playerId, ActivatedAbility ability);

    int getRecordedStateCount();

    void trimToCheckpoint(int checkpointSize);

    int writeRLData(String outputPath, boolean playerWon, double tdDiscount);
}
