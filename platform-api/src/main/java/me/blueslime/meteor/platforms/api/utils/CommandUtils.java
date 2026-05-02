package me.blueslime.meteor.platforms.api.utils;

import java.util.ArrayList;
import java.util.List;

public class CommandUtils {

    /**
     * Regroup arguments with '"' in a single argument.
     */
    public static String[] groupQuotedArguments(String[] args) {
        if (args == null || args.length == 0) return args;

        List<String> combined = new ArrayList<>();
        StringBuilder buffer = null;

        for (String arg : args) {
            if (buffer != null) {
                buffer.append(" ").append(arg);
                if (arg.endsWith("\"") && !arg.endsWith("\\\"")) { // Verifica que cierre y no esté escapada
                    buffer.setLength(buffer.length() - 1);
                    combined.add(buffer.toString());
                    buffer = null;
                }
            } else if (arg.startsWith("\"")) {
                if (arg.endsWith("\"") && arg.length() > 1) {
                    combined.add(arg.substring(1, arg.length() - 1));
                } else {
                    buffer = new StringBuilder(arg.substring(1));
                }
            } else {
                combined.add(arg);
            }
        }

        if (buffer != null) {
            combined.add(buffer.toString());
        }

        return combined.toArray(new String[0]);
    }
}
