package com.adn.dabaeum.common.logging;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.ibatis.logging.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 매개변수를 노출하지 않고 local/dev SQL 진단 정보를 남기는 MyBatis 로거이다.
 */
public final class SanitizedMyBatisLog implements Log {

    private static final Pattern PARAMETERS = Pattern.compile(
        "(?i)(\\bparameters\\s*:\\s*).*$"
    );

    private final Logger logger;

    public SanitizedMyBatisLog(String loggerName) {
        this.logger = LoggerFactory.getLogger(loggerName);
    }

    @Override
    public boolean isDebugEnabled() {
        return logger.isDebugEnabled();
    }

    @Override
    public boolean isTraceEnabled() {
        return logger.isTraceEnabled();
    }

    @Override
    public void error(String message, Throwable throwable) {
        logger.error(sanitize(message), throwable);
    }

    @Override
    public void error(String message) {
        logger.error(sanitize(message));
    }

    @Override
    public void debug(String message) {
        logger.debug(sanitize(message));
    }

    @Override
    public void trace(String message) {
        logger.trace(sanitize(message));
    }

    @Override
    public void warn(String message) {
        logger.warn(sanitize(message));
    }

    static String sanitize(String message) {
        if (message == null) {
            return null;
        }
        Matcher matcher = PARAMETERS.matcher(message);
        if (matcher.find()) {
            return message.substring(0, matcher.start(1))
                + matcher.group(1)
                + "***MASKED***";
        }
        return message;
    }
}
