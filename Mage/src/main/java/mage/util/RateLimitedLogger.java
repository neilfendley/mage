package mage.util;

import org.slf4j.Logger;

import java.util.HashMap;

import static org.slf4j.LoggerFactory.getLogger;

public class RateLimitedLogger {
    private static final long messageDuration = 1000L;
    private static final HashMap<String, Long> warnMessages = new HashMap<>();
    private static final HashMap<String, Long> infoMessages = new HashMap<>();
    private static final Logger logger = getLogger(RateLimitedLogger.class);


    public static synchronized void warn(String message) {
        long now = System.currentTimeMillis();
        if(!warnMessages.containsKey(message) || now - warnMessages.get(message) > messageDuration) {
            logger.warn(message);
            warnMessages.put(message, now);
        }
    }
    public static synchronized void info(String message) {
        long now = System.currentTimeMillis();
        if(!infoMessages.containsKey(message) || now - infoMessages.get(message) > messageDuration) {
            logger.info(message);
            infoMessages.put(message, now);
        }
    }
}
