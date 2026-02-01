package me.blueslime.meteor.utilities.colors;

import me.blueslime.meteor.utilities.tools.Tools;

import java.awt.*;
import java.util.Locale;

public class JavaColorUtils {

    /**
     * Converts a hex string to a color. If it can't be converted, null is returned.
     * @param hex (i.e. #CCCCCCFF #CCCCCC)
     * @return Color
     */
    private static Color hexadecimal(String hex) {
        hex = hex.replace("#", "");
        return switch (hex.length()) {
            case 6 -> new Color(
                    Integer.valueOf(hex.substring(0, 2), 16),
                    Integer.valueOf(hex.substring(2, 4), 16),
                    Integer.valueOf(hex.substring(4, 6), 16));
            case 8 -> new Color(
                    Integer.valueOf(hex.substring(0, 2), 16),
                    Integer.valueOf(hex.substring(2, 4), 16),
                    Integer.valueOf(hex.substring(4, 6), 16),
                    Integer.valueOf(hex.substring(6, 8), 16));
            default -> Color.WHITE;
        };
    }

    public static Color getColor(String color) {
        if (Tools.isNumber(color)) {
            return new Color(Tools.toInteger(color, 0));
        }
        if (color.contains("#")) {
            return hexadecimal(color);
        }
        if (color.contains(",")) {
            String[] split = color.replace(" ", "").split(",");

            if (split.length == 3) {
                return new Color(
                    Tools.toInteger(split[0], 0),
                    Tools.toInteger(split[1], 0),
                    Tools.toInteger(split[2], 0)
                );
            } else if (split.length == 2) {
                return new Color(
                    Tools.toInteger(split[0], 0),
                    Tools.toInteger(split[1], 0),
                    0
                );
            } else if (split.length >= 4) {
                return new Color(
                    Tools.toInteger(split[0], 0),
                    Tools.toInteger(split[1], 0),
                    Tools.toInteger(split[2], 0),
                    Tools.toInteger(split[3], 0)
                );
            }else {
                return new Color(
                    Tools.toInteger(split[0], 0),
                    0,
                    0
                );
            }
        }

        return switch (color.toLowerCase(Locale.ENGLISH)) {
            case "white", "&r", "&f", "r", "f" -> Color.WHITE;
            case "light-gray", "light_gray", "light gray", "7", "&7" -> Color.LIGHT_GRAY;
            case "gray" -> Color.GRAY;
            case "dark_gray", "dark gray", "dark-gray", "&8", "8" -> Color.DARK_GRAY;
            case "black", "0", "&0" -> Color.BLACK;
            case "red", "&4", "4" -> Color.RED;
            case "&d", "d", "pink" -> Color.PINK;
            case "&6", "6", "orange" -> Color.ORANGE;
            case "green", "dark green", "dark-green", "dark_green", "&2", "2", "a", "&a", "lime" -> Color.GREEN;
            case "magenta", "&5", "5" -> Color.MAGENTA;
            case "cyan", "&b", "b" -> Color.CYAN;
            case "blue", "&1", "&9", "&3", "1", "9", "3" -> Color.BLUE;
            default -> Color.YELLOW;
        };
    }
}


