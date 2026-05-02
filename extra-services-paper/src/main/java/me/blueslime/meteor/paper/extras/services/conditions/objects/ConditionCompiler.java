package me.blueslime.meteor.paper.extras.services.conditions.objects;

import me.blueslime.meteor.implementation.Implements;
import me.blueslime.meteor.paper.extras.services.conditions.CompiledCondition;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConditionCompiler {
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("^(.*?)\\s*(==|>=|<=|>|<|!=|=i=|\\|-|-\\|)\\s*(.*?)$");

    public static CompiledCondition compile(String configLine) {
        if (configLine.startsWith("[permission]")) {
            String perm = configLine.replace("[permission]", "").trim();
            return player -> player.hasPermission(perm);
        }

        if (configLine.startsWith("[placeholder]")) {
            String expression = configLine.replace("[placeholder]", "").trim();
            Matcher matcher = PLACEHOLDER_PATTERN.matcher(expression);

            if (matcher.find()) {
                String leftRaw = matcher.group(1).trim();
                String operator = matcher.group(2).trim();
                String rightRaw = matcher.group(3).trim();

                return player -> {
                    boolean placeholders = Implements.fetch(Boolean.class, "placeholders");
                    if (placeholders) {
                        String left = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, leftRaw);
                        String right = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, rightRaw);
                        return compare(left, right, operator);
                    }
                    return true;
                };
            }
        }
        return player -> true;
    }

    private static boolean compare(String value1, String value2, String operator) {
        try {
            double num1 = Double.parseDouble(value1);
            double num2 = Double.parseDouble(value2);
            return switch (operator) {
                case "==" -> num1 == num2;
                case ">" -> num1 > num2;
                case ">=" -> num1 >= num2;
                case "<" -> num1 < num2;
                case "<=" -> num1 <= num2;
                case "!=" -> num1 != num2;
                default -> false;
            };
        } catch (NumberFormatException e) {
            return switch (operator) {
                case "==" -> value1.equals(value2);
                case ">" -> value1.compareTo(value2) > 0;
                case ">=" -> value1.compareTo(value2) >= 0;
                case "<" -> value1.compareTo(value2) < 0;
                case "<=" -> value1.compareTo(value2) <= 0;
                case "!=" -> !value1.equals(value2);
                case "|-" -> value1.startsWith(value2);
                case "-|" -> value1.endsWith(value2);
                case "=i=" -> value1.equalsIgnoreCase(value2);
                default -> false;
            };
        }
    }
}


