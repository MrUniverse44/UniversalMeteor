package me.blueslime.meteor.platforms.standalone.logger;

import me.blueslime.meteor.platforms.api.logger.IPlatformLogger;
import me.blueslime.meteor.platforms.api.logger.type.PlatformLoggerType;
import org.apache.logging.log4j.Logger;

public record AppLogger(Logger logger) implements IPlatformLogger {

    /**
     * Prints an {@link Exception} in a detailed format.<br>
     * Default behavior delegates to {@link #printLog(Throwable)}.<br>
     *
     * @param exception exception to print
     */
    @Override
    public void printException(Exception exception) { printLog(exception); }

    /**
     * Prints a {@link Throwable} in a detailed format.<br>
     * Default behavior delegates to {@link #printLog(Throwable)}.<br>
     *
     * @param throwable throwable to print
     */
    @Override
    public void printThrowable(Throwable throwable) { printLog(throwable); }

    /**
     * Central method for printing any {@link Throwable} in a detailed format.<br>
     * Implementations are expected to format headers, stack traces, and causes<br>
     * and forward the result using {@link #send(String...)}.<br>
     *
     * @param throwable throwable to print
     */
    @Override
    public void printLog(Throwable throwable) {
        String prefix = getPrefix(PlatformLoggerType.ERROR);
        Class<?> current = throwable.getClass();
        String location = current.getName();
        String error = current.getSimpleName();
        String message = throwable.getMessage() != null ? throwable.getMessage() : "No message available";

        StringBuilder sb = new StringBuilder();

        sb.append(prefix).append("&7 -------------------------\n");
        sb.append(prefix).append("&7Location: &b").append(location.replace("." + error, "")).append("\n");
        sb.append(prefix).append("&7Error: &b").append(error).append("\n");
        sb.append(prefix).append("&7Message: &b").append(message).append("\n");

        if (throwable.getCause() != null) {
            sb.append(prefix).append("&7Cause: &b").append(throwable.getCause().toString()).append("\n").append(prefix).append("\n");
        }

        sb.append(prefix).append("&7StackTrace: ").append("\n");

        StackTraceElement[] trace = throwable.getStackTrace();
        int count = Math.min(trace.length, 20);

        for (int i = 0; i < count; i++) {
            StackTraceElement el = trace[i];
            String indent = "   ".repeat(i);
            String arrow = i == 0 ? "&b" : indent + "&8⥚ &b";

            sb.append(prefix).append(arrow)
                    .append(el.getClassName())
                    .append("&7 (Line: &b").append(el.getLineNumber())
                    .append("&7 - Method: &b").append(el.getMethodName()).append("&7)");

            if (i == 0) {
                sb.append("&8 <-&7 Exception point");
            }
            sb.append("\n");
        }

        if (trace.length > 20) {
            sb.append(prefix).append("&8... &7").append(trace.length - 20).append(" more lines\n").append(prefix).append("\n");
        } else {
            sb.append(prefix).append("\n");
        }

        Throwable cause = throwable.getCause();
        int causeDepth = 0;
        while (cause != null && causeDepth < 5) {
            sb.append(prefix).append("&7Caused by: &b").append(cause.getClass().getName())
                    .append(": ").append(cause.getMessage() == null ? "(no message)" : cause.getMessage()).append("\n");

            StackTraceElement[] cTrace = cause.getStackTrace();
            int cCount = Math.min(cTrace.length, 10);
            for (int i = 0; i < cCount; i++) {
                StackTraceElement el = cTrace[i];
                String indent = "   ".repeat(i + 1);
                String arrow = indent + "&8⥚ &b";
                sb.append(prefix).append(arrow)
                        .append(el.getClassName())
                        .append("&7 (Line: &b").append(el.getLineNumber())
                        .append("&7 - Method: &b").append(el.getMethodName()).append("&7)\n");
            }
            cause = cause.getCause();
            causeDepth++;
        }

        sb.append(prefix).append("\n").append(prefix).append("&7 -------------------------");

        error(sb.toString());
    }

    /**
     * Sends messages ensuring that each resulting line is prefixed according to<br>
     * the given {@link PlatformLoggerType}.<br>
     * Messages are joined, split into lines, prefixed line-by-line, and then<br>
     * sent in a single {@link #send(String...)} call.<br>
     *
     * @param type log level
     * @param messages messages to send
     */
    @Override
    public void sendWithPrefix(PlatformLoggerType type, String... messages) {
        String prefix = getPrefix(type);

        if (messages == null || messages.length == 0) {
            error(prefix);
            return;
        }

        String joined = String.join("\n", messages);
        String[] lines = joined.split("\n", -1);

        for (int i = 0; i < lines.length; i++) {
            lines[i] = prefix + lines[i];
        }

        error(String.join("\n", lines));
    }

    /**
     * Low-level message sender.<br>
     * Implementations must forward the provided messages to the platform output<br>
     * (console, logger, component system, etc.).<br>
     *
     * @param messages formatted messages to send
     */
    @Override
    public void send(String... messages) {

    }

    @Override
    public void info(String... messages) {
        for (String message : messages) {
            logger.info(message);
        }
    }

    /**
     * Sets a custom prefix for a specific {@link PlatformLoggerType}.<br>
     * The prefix is applied to every line sent for that log level.<br>
     *
     * @param log    log type
     * @param prefix prefix to apply, or {@code null} to clear
     * @return this logger instance
     */
    @Override
    public IPlatformLogger setPrefix(PlatformLoggerType log, String prefix) {
        return null;
    }

    /**
     * Returns the prefix for the given {@link PlatformLoggerType}.<br>
     * Implementations may provide a default prefix if none was explicitly set.<br>
     *
     * @param prefix log type
     * @return prefix string
     */
    @Override
    public String getPrefix(PlatformLoggerType prefix) {
        return "";
    }

    @Override
    public void debug(String... messages) {
        for (String message : messages) {
            logger.debug(message);
        }
    }

    @Override
    public void error(String... messages) {
        for (String message : messages) {
            logger.error(message);
        }
    }

    @Override
    public void error(Throwable throwable, String... messages) {
        for (String message : messages) {
            logger.error(message);
        }
        logger.error(throwable);
    }

    @Override
    public void warn(String... messages) {
        for (String message : messages) {
            logger.warn(message);
        }
    }
}


