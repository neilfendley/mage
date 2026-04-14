package mage.game;

import mage.abilities.Ability;
import mage.abilities.ActivatedAbility;
import mage.cards.Cards;
import mage.target.Target;

import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;

/**
 * Interface for recording game decisions for RL training data.
 */
public interface GameRecorder {

    final class Factory {
        private static BiFunction<UUID, UUID, GameRecorder> instance;

        public static void register(BiFunction<UUID, UUID, GameRecorder> factory) {
            instance = factory;
        }

        public static GameRecorder create(UUID playerId, UUID opponentId) {
            return instance != null ? instance.apply(playerId, opponentId) : null;
        }
    }

    Set<Integer> capturePreDecisionState(Game game, UUID playerId);

    void recordChooseUse(Set<Integer> preDecisionState, boolean choice, UUID playerId);

    void recordMakeChoice(Set<Integer> preDecisionState, String choiceText, UUID playerId);

    void recordTargetSelections(Set<Integer> preDecisionState, Target target,
                                UUID abilityControllerId, Ability source, Game game, Cards cards, UUID playerId);

    void recordPassAction(Game game, UUID playerId);

    void recordActivateAbility(Game game, UUID playerId, ActivatedAbility ability);

    int getRecordedStateCount();

    void trimToCheckpoint(int checkpointSize);

    int writeRLData(String playerAPath, String playerBPath, boolean playerWon, double tdDiscount);
}
