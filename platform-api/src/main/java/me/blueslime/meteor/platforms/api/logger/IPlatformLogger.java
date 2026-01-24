package me.blueslime.meteor.platforms.api.logger;

import me.blueslime.meteor.platforms.api.logger.type.PlatformLoggerType;

/**
 * PlatformLogger<br>
 * <br>
 * Core logging interface for multiple platforms (Paper, Bungee, Velocity, etc.).<br>
 * Provides default shorthand methods for log levels, line-prefixing utilities,<br>
 * and a contract for creating module-scoped loggers.<br>
 * <br>
 * Concrete implementations must provide the low-level sending logic via<br>
 * {@link #send(String...)} and should override {@link #createModuleLogger(String)}<br>
 * to return a functional module logger that shares the same output channel.<br>
 */
public interface IPlatformLogger {

    /**
     * Logs one or more messages using the ERROR level.<br>
     * Each message may contain line breaks; every resulting line will be prefixed<br>
     * using the prefix returned by {@link #getPrefix(PlatformLoggerType)} for<br>
     * {@link PlatformLoggerType#ERROR}.<br>
     *
     * @param messages messages to log
     */
    default void error(String... messages) { sendWithPrefix(PlatformLoggerType.ERROR, messages); }

    /**
     * Logs an {@link Exception} using the ERROR level.<br>
     * The exception is printed using {@link #printException(Exception)}.<br>
     *
     * @param e exception to log
     */
    default void error(Exception e) { printException(e); }

    /**
     * Logs one or more messages and then prints an {@link Exception} using the ERROR level.<br>
     * Messages are sent first, followed by the detailed exception output.<br>
     *
     * @param e exception to log
     * @param messages additional messages to log
     */
    default void error(Exception e, String... messages) {
        sendWithPrefix(PlatformLoggerType.ERROR, messages);
        printException(e);
    }

    /**
     * Logs a {@link Throwable} using the ERROR level.<br>
     *
     * @param t throwable to log
     */
    default void error(Throwable t) { printThrowable(t); }

    /**
     * Logs one or more messages and then prints a {@link Throwable} using the ERROR level.<br>
     *
     * @param t throwable to log
     * @param messages additional messages to log
     */
    default void error(Throwable t, String... messages) {
        sendWithPrefix(PlatformLoggerType.ERROR, messages);
        printThrowable(t);
    }

    /**
     * Logs messages using the WARN level.<br>
     *
     * @param messages messages to log
     */
    default void warn(String... messages) { sendWithPrefix(PlatformLoggerType.WARN, messages); }

    /**
     * Logs messages using the DEBUG level.<br>
     *
     * @param messages messages to log
     */
    default void debug(String... messages) { sendWithPrefix(PlatformLoggerType.DEBUG, messages); }

    /**
     * Logs messages using the INFO level.<br>
     *
     * @param messages messages to log
     */
    default void info(String... messages) { sendWithPrefix(PlatformLoggerType.INFO, messages); }

    /**
     * Low-level message sender.<br>
     * Implementations must forward the provided messages to the platform output<br>
     * (console, logger, component system, etc.).<br>
     *
     * @param messages formatted messages to send
     */
    void send(String... messages);

    /**
     * Prints an {@link Exception} in a detailed format.<br>
     * Default behavior delegates to {@link #printLog(Throwable)}.<br>
     *
     * @param exception exception to print
     */
    default void printException(Exception exception) { printLog(exception); }

    /**
     * Prints a {@link Throwable} in a detailed format.<br>
     * Default behavior delegates to {@link #printLog(Throwable)}.<br>
     *
     * @param throwable throwable to print
     */
    default void printThrowable(Throwable throwable) { printLog(throwable); }

    /**
     * Central method for printing any {@link Throwable} in a detailed format.<br>
     * Implementations are expected to format headers, stack traces, and causes<br>
     * and forward the result using {@link #send(String...)}.<br>
     *
     * @param throwable throwable to print
     */
    default void printLog(Throwable throwable) {
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

        send(sb.toString());
    }

    /**
     * Sets a custom prefix for a specific {@link PlatformLoggerType}.<br>
     * The prefix is applied to every line sent for that log level.<br>
     *
     * @param log log type
     * @param prefix prefix to apply, or {@code null} to clear
     * @return this logger instance
     */
    IPlatformLogger setPrefix(PlatformLoggerType log, String prefix);

    /**
     * Returns the prefix for the given {@link PlatformLoggerType}.<br>
     * Implementations may provide a default prefix if none was explicitly set.<br>
     *
     * @param prefix log type
     * @return prefix string
     */
    String getPrefix(PlatformLoggerType prefix);

    /**
     * Creates and returns a module-scoped {@link IPlatformLogger}.<br>
     * The returned logger should share the same output channel and configuration<br>
     * as the parent logger, while using the provided module name for identification.<br>
     *
     * @param moduleName module identifier
     * @return module logger instance
     */
    default IPlatformLogger createModuleLogger(String moduleName) {
        throw new UnsupportedOperationException("PlatformLogger implementation must provide createModuleLogger");
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
    default void sendWithPrefix(PlatformLoggerType type, String... messages) {
        String prefix = getPrefix(type);

        if (messages == null || messages.length == 0) {
            send(prefix);
            return;
        }

        String joined = String.join("\n", messages);
        String[] lines = joined.split("\n", -1);

        for (int i = 0; i < lines.length; i++) {
            lines[i] = prefix + lines[i];
        }

        send(String.join("\n", lines));
    }

}
