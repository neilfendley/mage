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
<<<<<<< HEAD
 * Implementations handle state encoding, action encoding, and data persistence.
 * Defined in core Mage so any player type can use it without AI module dependencies.
 *
 * Use {@link #registerFactory} to register a factory (done by the AI module at startup).
 * Use {@link #createForPlayer} to create a recorder without knowing the implementation.
 */
public interface GameRecorder {

    // Factory registry — allows core/server code to create recorders without AI module dependency
=======
 */
public interface GameRecorder {

>>>>>>> 257d88b400b8488c0398092ba9281d8c2dba4616
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
