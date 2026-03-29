package mage.watchers;

import mage.constants.WatcherScope;
import mage.game.Game;
import mage.game.events.GameEvent;
import mage.util.CardUtil;
import org.apache.log4j.Logger;

import java.io.Serializable;
import java.lang.reflect.*;
import java.util.*;

/**
 * watches for certain game events to occur and flags condition
 *
 * @author BetaSteward_at_googlemail.com
 */
public abstract class Watcher implements Serializable {

    private static final Logger logger = Logger.getLogger(Watcher.class);

    // Cache reflection metadata per watcher class to avoid repeated lookups during copy()
    private static final Map<Class<?>, Constructor<?>> CONSTRUCTOR_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<Class<?>, Field[]> FIELD_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    protected UUID controllerId;
    protected UUID sourceId;
    protected boolean condition;
    protected final WatcherScope scope;

    public Watcher(WatcherScope scope) {
        this.scope = scope;
    }

    protected Watcher(final Watcher watcher) {
        this.condition = watcher.condition;
        this.controllerId = watcher.controllerId;
        this.sourceId = watcher.sourceId;
        this.scope = watcher.scope;
    }

    public UUID getControllerId() {
        return controllerId;
    }

    public void setControllerId(UUID controllerId) {
        this.controllerId = controllerId;
    }

    public UUID getSourceId() {
        return sourceId;
    }

    public void setSourceId(UUID sourceId) {
        this.sourceId = sourceId;
    }

    public String getKey() {
        switch (scope) {
            case GAME:
                return getBasicKey();
            case PLAYER:
                return controllerId + getBasicKey();
            case CARD:
                return sourceId + getBasicKey();
            default:
                throw new IllegalArgumentException("Unknown watcher scope: " + this.getClass().getSimpleName() + " - " + scope);
        }
    }

    public boolean conditionMet() {
        return condition;
    }

    public void reset() {
        condition = false;
    }

    protected String getBasicKey() {
        return getClass().getSimpleName();
    }

    public abstract void watch(GameEvent event, Game game);

    public <T extends Watcher> T copy() {
        try {
            Class<?> clazz = this.getClass();

            // Cache constructor lookup
            Constructor<?> constructor = CONSTRUCTOR_CACHE.get(clazz);
            if (constructor == null) {
                Constructor<?>[] constructors = clazz.getDeclaredConstructors();
                if (constructors.length > 1) {
                    logger.error(clazz.getSimpleName() + " has multiple constructors");
                    return null;
                }
                constructor = constructors[0];
                constructor.setAccessible(true);
                CONSTRUCTOR_CACHE.put(clazz, constructor);
            }

            // Instantiate with default args
            Object[] args = new Object[constructor.getParameterCount()];
            for (int index = 0; index < constructor.getParameterTypes().length; index++) {
                Class<?> parameterType = constructor.getParameterTypes()[index];
                if (parameterType.isPrimitive()) {
                    if (parameterType == boolean.class) {
                        args[index] = false;
                    } else {
                        args[index] = 0;
                    }
                }
            }
            T watcher = (T) constructor.newInstance(args);

            // Cache field lookup (filtered to non-static, pre-accessible)
            Field[] fields = FIELD_CACHE.get(clazz);
            if (fields == null) {
                List<Field> allFields = new ArrayList<>();
                for (Field f : clazz.getDeclaredFields()) {
                    if (!Modifier.isStatic(f.getModifiers())) {
                        f.setAccessible(true);
                        allFields.add(f);
                    }
                }
                for (Field f : clazz.getSuperclass().getDeclaredFields()) {
                    if (!Modifier.isStatic(f.getModifiers())) {
                        f.setAccessible(true);
                        allFields.add(f);
                    }
                }
                fields = allFields.toArray(new Field[0]);
                FIELD_CACHE.put(clazz, fields);
            }

            // Copy field values
            for (Field field : fields) {
                Object val = field.get(this);
                if (val == null) {
                    field.set(watcher, null);
                } else {
                    field.set(watcher, CardUtil.deepCopyObject(val));
                }
            }
            return watcher;
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            logger.error("Can't copy watcher: " + e.getMessage(), e);
        }
        return null;
    }

    public WatcherScope getScope() {
        return scope;
    }
}
