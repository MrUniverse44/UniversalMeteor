package me.blueslime.meteor.utilities.tools;

public class Tools {

    public static long toLong(String value, int defLong) {
        return isLong(value) ? Long.parseLong(value) : defLong;
    }

    public static int toInteger(String value, int defInt) {
        return isInteger(value) ? Integer.parseInt(value) : defInt;
    }

    public static float toFloat(String value, float defFloat) {
        return isDouble(value) ? Float.parseFloat(value) : defFloat;
    }

    public static double toDouble(String value, float defDouble) {
        return isDouble(value) ? Double.parseDouble(value) : (double)defDouble;
    }

    public static byte toByte(String value, byte defByte) {
        return isByte(value) ? Byte.parseByte(value) : defByte;
    }

    public static boolean isNumber(String value) {
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException var2) {
            return false;
        }
    }

    public static boolean isByte(String value) {
        try {
            Byte.parseByte(value);
            return true;
        } catch (NumberFormatException var2) {
            return false;
        }
    }

    public static boolean isDouble(String value) {
        try {
            Double.parseDouble(value);
            return true;
        } catch (NumberFormatException var2) {
            return false;
        }
    }

    public static boolean isLong(String value) {
        try {
            Long.parseLong(value);
            return true;
        } catch (NumberFormatException var2) {
            return false;
        }
    }

    public static boolean isFloat(String value) {
        try {
            Float.parseFloat(value);
            return true;
        } catch (NumberFormatException var2) {
            return false;
        }
    }

    public static boolean isInteger(String value) {
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException var2) {
            return false;
        }
    }

}



