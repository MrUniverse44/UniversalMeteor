package me.blueslime.meteor.color;

import java.util.*;

public class UniversalColorParser {

    private static final int MAX_SEGMENTS = 50_000;
    private static final int MAX_GRADIENT_EXPANSION = 4096;

    public static class Segment {
        public final String text;
        public final Color color;
        public final boolean gradient;
        public final boolean bold, italic, underlined, strikethrough, obfuscated;

        public Segment(String text, Color color, boolean gradient, boolean bold, boolean italic, boolean underlined, boolean strikethrough, boolean obfuscated) {
            this.text = text;
            this.color = color; this.gradient = gradient; this.bold = bold; this.italic = italic; this.underlined = underlined; this.strikethrough = strikethrough; this.obfuscated = obfuscated;
        }

        public Segment(String text, Color color, boolean bold, boolean italic, boolean underlined, boolean strikethrough, boolean obfuscated) {
            this(text, color, false, bold, italic, underlined, strikethrough, obfuscated);
        }

        public Segment(String text, Color color) {
            this(text, color, false, false, false, false, false, false);
        }

        public String toString() {
            return "[%s %s]%s".formatted(color == null ? "null" : color.toHex(), (bold ? "B" : "") + (italic ? "I" : "") + (underlined ? "U" : "") + (strikethrough ? "S" : ""), (text == null ? "" : text));
        }
    }

    public record Color(int r, int g, int b) {
        public static Color fromHex(String hex) {
            if (hex == null) throw new IllegalArgumentException("hex == null");
            String h = hex.replace("#", "");
            if (h.length() == 3) {
                h = "" + h.charAt(0) + h.charAt(0) + h.charAt(1) + h.charAt(1) + h.charAt(2) + h.charAt(2);
            }
            if (h.length() != 6) throw new IllegalArgumentException("Invalid hex color: " + hex);
            int v = Integer.parseInt(h, 16);
            return new Color((v >> 16) & 0xFF, (v >> 8) & 0xFF, v & 0xFF);
        }

        public String toHex() {
            return "#%02x%02x%02x".formatted(r, g, b);
        }
    }

    private static final Map<String, Character> NAME_TO_LEGACY = Map.ofEntries(
            Map.entry("black", '0'), Map.entry("dark_blue", '1'), Map.entry("dark_green", '2'), Map.entry("darkgreen", '2'),
            Map.entry("dark_aqua", '3'), Map.entry("dark_red", '4'), Map.entry("darkred", '4'), Map.entry("dark_purple", '5'),
            Map.entry("gold", '6'), Map.entry("gray", '7'), Map.entry("dark_gray", '8'), Map.entry("blue", '9'),
            Map.entry("green", 'a'), Map.entry("aqua", 'b'), Map.entry("red", 'c'), Map.entry("light_purple", 'd'),
            Map.entry("yellow", 'e'), Map.entry("white", 'f')
    );

    private static Color legacyCodeToColor(char code) {
        return switch (Character.toLowerCase(code)) {
            case '0' -> new Color(0, 0, 0);
            case '1' -> new Color(0, 0, 170);
            case '2' -> new Color(0, 170, 0);
            case '3' -> new Color(0, 170, 170);
            case '4' -> new Color(170, 0, 0);
            case '5' -> new Color(170, 0, 170);
            case '6' -> new Color(255, 170, 0);
            case '7' -> new Color(170, 170, 170);
            case '8' -> new Color(85, 85, 85);
            case '9' -> new Color(85, 85, 255);
            case 'a' -> new Color(85, 255, 85);
            case 'b' -> new Color(85, 255, 255);
            case 'c' -> new Color(255, 85, 85);
            case 'd' -> new Color(255, 85, 255);
            case 'e' -> new Color(255, 255, 85);
            case 'f' -> new Color(255, 255, 255);
            default -> null;
        };
    }

    public static List<Segment> parse(String input) {
        List<Segment> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        Color curColor = null;
        boolean bold = false, italic = false, under = false, strike = false, obf = false;
        int segmentsCreated = 0;
        int i = 0, len = input.length();

        while (i < len) {
            if (segmentsCreated > MAX_SEGMENTS) {
                cur.append(input.substring(i));
                break;
            }
            char ch = input.charAt(i);

            if (ch == '&') {
                if (i + 1 < len && input.charAt(i + 1) == '&') { cur.append('&'); i += 2; continue; }

                ParseHexResult ph = tryParseAmpersandHex(input, i);
                if (ph != null) {
                    if (!cur.isEmpty()) { out.add(new Segment(cur.toString(), curColor, false, bold, italic, under, strike, obf)); cur.setLength(0); segmentsCreated++; }
                    curColor = ph.color; i = ph.newIndex; continue;
                }

                if (i + 1 < len && input.charAt(i + 1) == '#') {
                    if (!cur.isEmpty()) { out.add(new Segment(cur.toString(), curColor, false, bold, italic, under, strike, obf)); cur.setLength(0); segmentsCreated++; }
                    int j = i + 2; StringBuilder hx = new StringBuilder();
                    while (j < len && isHexChar(input.charAt(j)) && hx.length() < 6) { hx.append(input.charAt(j)); j++; }
                    if (hx.length() >= 3) { curColor = Color.fromHex(hx.toString()); i = j; continue; }
                }

                int j = i + 1;
                while (j < len && (Character.isLetter(input.charAt(j)) || input.charAt(j) == '_' || input.charAt(j) == '-')) j++;
                if (j < len && j > i + 1 && input.charAt(j) == '&') {
                    String name = input.substring(i + 1, j).toLowerCase();
                    Character code = NAME_TO_LEGACY.get(name);
                    if (code != null) {
                        if (!cur.isEmpty()) { out.add(new Segment(cur.toString(), curColor, false, bold, italic, under, strike, obf)); cur.setLength(0); segmentsCreated++; }
                        curColor = legacyCodeToColor(code); i = j + 1; continue;
                    }
                }

                if (i + 1 < len) {
                    if (!cur.isEmpty()) { out.add(new Segment(cur.toString(), curColor, false, bold, italic, under, strike, obf)); cur.setLength(0); segmentsCreated++; }
                    char code = Character.toLowerCase(input.charAt(i + 1)); i += 2;
                    switch (code) {
                        case 'k' -> obf = true;
                        case 'l' -> bold = true;
                        case 'm' -> strike = true;
                        case 'n' -> under = true;
                        case 'o' -> italic = true;
                        case 'r' -> {
                            curColor = null; bold = false; italic = false; under = false; strike = false; obf = false;
                        }
                        default -> {
                            Color c = legacyCodeToColor(code);
                            if (c != null) {
                                curColor = c; bold = false; italic = false; under = false; strike = false; obf = false;
                            } else {
                                cur.append('&').append(code);
                            }
                            break;
                        }
                    }
                    continue;
                }
                cur.append('&'); i++; continue;
            }

            if (ch == '<') {
                if (i + 1 < len && input.charAt(i + 1) == '#') {
                    int j = i + 2; StringBuilder hx = new StringBuilder();
                    while (j < len && isHexChar(input.charAt(j)) && hx.length() < 6) { hx.append(input.charAt(j)); j++; }
                    if (j < len && input.charAt(j) == '>') {
                        if (!cur.isEmpty()) { out.add(new Segment(cur.toString(), curColor, false, bold, italic, under, strike, obf)); cur.setLength(0); segmentsCreated++; }
                        j++;
                        int closeSimple = input.indexOf("</#>", j);
                        int closePrefix = input.indexOf("</#", j);
                        if (closeSimple != -1) {
                            out.addAll(applyColorOverrideToParsed(input.substring(j, closeSimple), Color.fromHex(hx.toString()), bold, italic, under, strike, obf));
                            int closeEnd = input.indexOf('>', closeSimple);
                            i = (closeEnd == -1) ? len : closeEnd + 1;
                            continue;
                        } else if (closePrefix != -1) {
                            int after = closePrefix + "</#".length();
                            StringBuilder hx2 = new StringBuilder(); int k = after;
                            while (k < len && isHexChar(input.charAt(k)) && hx2.length() < 6) { hx2.append(input.charAt(k)); k++; }
                            if (k < len && input.charAt(k) == '>') {
                                out.addAll(applyColorOverrideToParsed(input.substring(j, closePrefix), Color.fromHex(hx.toString()), bold, italic, under, strike, obf));
                                i = k + 1; continue;
                            }
                        }
                        int nextTag = input.indexOf('<', j);
                        int end = (nextTag == -1) ? len : nextTag;
                        out.addAll(applyColorOverrideToParsed(input.substring(j, end), Color.fromHex(hx.toString()), bold, italic, under, strike, obf));
                        i = end; continue;
                    }
                }

                if (matchesAtIgnoreCase(input, i, "<GRADIENT:")) {
                    int colon = i + "<GRADIENT:".length(); int j = colon; StringBuilder token = new StringBuilder();
                    while (j < len && input.charAt(j) != '>') { token.append(input.charAt(j)); j++; }
                    if (j < len && input.charAt(j) == '>') {
                        if (!cur.isEmpty()) { out.add(new Segment(cur.toString(), curColor, false, bold, italic, under, strike, obf)); cur.setLength(0); segmentsCreated++; }
                        j++;
                        String[] parts = token.toString().split("[,;|:]");
                        List<Color> stops = new ArrayList<>();
                        for (String p : parts) {
                            String p2 = p.trim().replace("#", "");
                            if (p2.length() >= 3 && p2.length() <= 6) stops.add(Color.fromHex(p2));
                        }
                        if (stops.isEmpty()) { i = j; continue; }

                        int closeIdx = indexOfIgnoreCase(input, "</GRADIENT:", j);
                        String endHex = null; int closeStart = -1, closeEnd = -1;
                        if (closeIdx != -1) {
                            int k = closeIdx + "</GRADIENT:".length(); StringBuilder hx2 = new StringBuilder();
                            while (k < len && isHexChar(input.charAt(k)) && hx2.length() < 6) { hx2.append(input.charAt(k)); k++; }
                            if (k < len && input.charAt(k) == '>') { closeStart = closeIdx; closeEnd = k + 1; endHex = hx2.toString(); }
                        }

                        if (closeStart == -1) {
                            int close2 = indexOfIgnoreCase(input, "</GRADIENT>", j);
                            if (close2 != -1) { closeStart = close2; closeEnd = close2 + "</GRADIENT>".length(); }
                        }

                        if (endHex != null && endHex.length() >= 3) {
                            Color endColor = Color.fromHex(endHex);
                            if (stops.isEmpty() || !Objects.equals(stops.getLast(), endColor)) stops.add(endColor);
                        }

                        if (closeStart == -1) {
                            out.addAll(expandMultiStopGradientFromParsed(input.substring(j), stops, bold, italic, under, strike, obf));
                            return mergeSegments(out);
                        } else {
                            out.addAll(expandMultiStopGradientFromParsed(input.substring(j, closeStart), stops, bold, italic, under, strike, obf));
                            i = closeEnd; continue;
                        }
                    }
                }

                if (matchesAtIgnoreCase(input, i, "<RAINBOW")) {
                    int openClose = input.indexOf('>', i);
                    if (openClose != -1) {
                        if (!cur.isEmpty()) { out.add(new Segment(cur.toString(), curColor, false, bold, italic, under, strike, obf)); cur.setLength(0); segmentsCreated++; }
                        int afterOpen = openClose + 1;
                        int close = indexOfIgnoreCase(input, "</RAINBOW>", afterOpen);
                        if (close == -1) {
                            out.addAll(expandRainbowFromParsed(input.substring(afterOpen), bold, italic, under, strike, obf));
                            return mergeSegments(out);
                        }
                        out.addAll(expandRainbowFromParsed(input.substring(afterOpen, close), bold, italic, under, strike, obf));
                        i = close + "</RAINBOW>".length(); continue;
                    }
                }

                String tag = readTagName(input, i);
                if (tag != null) {
                    String tagLower = tag.toLowerCase(); Character code = NAME_TO_LEGACY.get(tagLower);
                    boolean isStyleTag = tagLower.equals("b") || tagLower.equals("bold") || tagLower.equals("i") || tagLower.equals("italic") || tagLower.equals("u") || tagLower.equals("underline") || tagLower.equals("s") || tagLower.equals("strikethrough") || tagLower.equals("obf") || tagLower.equals("obfuscated");
                    boolean isResetTag = tagLower.equals("reset") || tagLower.equals("r");

                    if (isResetTag) {
                        int openEnd = input.indexOf('>', i);
                        if (openEnd == -1) { i++; continue; }
                        if (!cur.isEmpty()) { out.add(new Segment(cur.toString(), curColor, false, bold, italic, under, strike, obf)); cur.setLength(0); segmentsCreated++; }

                        int close = indexOfIgnoreCase(input, "</" + tag + ">", openEnd + 1);
                        if (close == -1) {
                            curColor = null; bold = false; italic = false; under = false; strike = false; obf = false;
                            i = openEnd + 1; continue;
                        }
                        // Si hay cierre, parsea el contenido con estado limpio (ya que parse() arranca de cero)
                        out.addAll(parse(input.substring(openEnd + 1, close)));
                        i = close + tag.length() + 3; continue;
                    }

                    if (isStyleTag) {
                        int openEnd = input.indexOf('>', i);
                        if (openEnd == -1) { i++; continue; }
                        if (!cur.isEmpty()) { out.add(new Segment(cur.toString(), curColor, false, bold, italic, under, strike, obf)); cur.setLength(0); segmentsCreated++; }
                        int close = indexOfIgnoreCase(input, "</" + tag + ">", openEnd + 1);
                        if (close == -1) {
                            out.addAll(applyStyleOverrideToParsed(input.substring(openEnd + 1), tagLower));
                            return mergeSegments(out);
                        }
                        out.addAll(applyStyleOverrideToParsed(input.substring(openEnd + 1, close), tagLower));
                        i = close + tag.length() + 3; continue;
                    }

                    if (code != null) {
                        int openEnd = input.indexOf('>', i);
                        if (openEnd == -1) { i++; continue; }
                        if (!cur.isEmpty()) { out.add(new Segment(cur.toString(), curColor, false, bold, italic, under, strike, obf)); cur.setLength(0); segmentsCreated++; }
                        int close = indexOfIgnoreCase(input, "</" + tag + ">", openEnd + 1);
                        if (close == -1) {
                            out.addAll(applyColorOverrideToParsed(input.substring(openEnd + 1), legacyCodeToColor(code), bold, italic, under, strike, obf));
                            return mergeSegments(out);
                        }
                        out.addAll(applyColorOverrideToParsed(input.substring(openEnd + 1, close), legacyCodeToColor(code), bold, italic, under, strike, obf));
                        i = close + tag.length() + 3; continue;
                    }
                }
            }
            cur.append(ch); i++;
        }
        if (!cur.isEmpty()) out.add(new Segment(cur.toString(), curColor, false, bold, italic, under, strike, obf));
        return mergeSegments(out);
    }

    private static List<Segment> applyStyleOverrideToParsed(String body, String styleTag) {
        List<Segment> parsed = parse(body);
        boolean addBold = styleTag.equals("b") || styleTag.equals("bold");
        boolean addItalic = styleTag.equals("i") || styleTag.equals("italic");
        boolean addUnder = styleTag.equals("u") || styleTag.equals("underline");
        boolean addStrike = styleTag.equals("s") || styleTag.equals("strikethrough");
        boolean addObf = styleTag.equals("obf") || styleTag.equals("obfuscated");

        List<Segment> out = new ArrayList<>();
        for (Segment s : parsed) {
            out.add(new Segment(s.text, s.color, s.gradient, s.bold || addBold, s.italic || addItalic, s.underlined || addUnder, s.strikethrough || addStrike, s.obfuscated || addObf));
        }
        return mergeSegments(out);
    }

    private record ParseHexResult(Color color, int newIndex) {}

    private static ParseHexResult tryParseAmpersandHex(String input, int idx) {
        int len = input.length();
        if (idx + 1 >= len) return null;
        char x = input.charAt(idx + 1);
        if (x != 'x' && x != 'X') return null;

        int pos = idx + 2;
        if (pos < len && input.charAt(pos) == '&') {
            int p = pos; StringBuilder hx = new StringBuilder(6);
            for (int k = 0; k < 6; k++) {
                if (p >= len || input.charAt(p) != '&') return null;
                p++; if (p >= len) return null;
                char hc = input.charAt(p);
                if (!isHexChar(hc)) return null;
                hx.append(hc); p++;
            }
            return new ParseHexResult(Color.fromHex(hx.toString()), p);
        }

        if (pos + 6 <= len) {
            boolean ok = true;
            for (int k = 0; k < 6; k++) {
                if (!isHexChar(input.charAt(pos + k))) { ok = false; break; }
            }
            if (ok) return new ParseHexResult(Color.fromHex(input.substring(pos, pos + 6)), pos + 6);
        }
        return null;
    }

    private static boolean isHexChar(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private static boolean matchesAtIgnoreCase(String input, int idx, String prefix) {
        return input.regionMatches(true, idx, prefix, 0, prefix.length());
    }

    private static int indexOfIgnoreCase(String s, String sub, int from) {
        if (from < 0) from = 0;
        int max = s.length() - sub.length();
        for (int i = from; i <= max; i++) {
            if (s.regionMatches(true, i, sub, 0, sub.length())) return i;
        }
        return -1;
    }

    private static String readTagName(String input, int idx) {
        if (input.charAt(idx) != '<') return null;
        int j = idx + 1; StringBuilder b = new StringBuilder();
        while (j < input.length()) {
            char c = input.charAt(j);
            if (Character.isLetter(c) || c == '_' || c == '-') { b.append(c); j++; } else break;
        }
        return b.isEmpty() ? null : b.toString();
    }

    private static List<Segment> applyColorOverrideToParsed(String body, Color color, boolean bold, boolean italic, boolean under, boolean strike, boolean obf) {
        List<Segment> parsed = parse(body);
        List<Segment> out = new ArrayList<>();
        for (Segment s : parsed) {
            out.add(new Segment(s.text, color, s.bold || bold, s.italic || italic, s.underlined || under, s.strikethrough || strike, s.obfuscated || obf));
        }
        return mergeSegments(out);
    }

    private static List<Segment> expandMultiStopGradientFromParsed(String body, List<Color> stops, boolean bold, boolean italic, boolean under, boolean strike, boolean obf) {
        List<Segment> inner = parse(body);
        record CE(char ch, boolean b, boolean i, boolean u, boolean s, boolean k) {}
        List<CE> chars = new ArrayList<>();
        for (Segment s : inner) {
            for (int j = 0; j < s.text.length(); j++) {
                chars.add(new CE(s.text.charAt(j), s.bold || bold, s.italic || italic, s.underlined || under, s.strikethrough || strike, s.obfuscated || obf));
            }
        }

        int n = chars.size();
        if (n == 0) return Collections.emptyList();
        int numStops = stops == null ? 0 : stops.size();
        if (numStops == 0) return Collections.singletonList(new Segment(body, null, bold, italic, under, strike, obf));

        List<Segment> out = new ArrayList<>(n);
        if (numStops == 1) {
            Color c = stops.getFirst();
            for (CE ce : chars) out.add(new Segment(String.valueOf(ce.ch()), c, true, ce.b(), ce.i(), ce.u(), ce.s(), ce.k()));
            return mergeSegments(out);
        }

        for (int idx = 0; idx < n; idx++) {
            double tGlobal = (double) idx / Math.max(1, n - 1);
            double scaled = tGlobal * (numStops - 1);
            int left = Math.min((int) Math.floor(scaled), numStops - 2);
            if (left < 0) left = 0;
            Color a = stops.get(left), b = stops.get(left + 1);
            CE ce = chars.get(idx);
            out.add(new Segment(String.valueOf(ce.ch()), lerpColor(a, b, scaled - left), true, ce.b(), ce.i(), ce.u(), ce.s(), ce.k()));
        }
        return mergeSegments(out);
    }

    private static List<Segment> expandRainbowFromParsed(String body, boolean bold, boolean italic, boolean under, boolean strike, boolean obf) {
        List<Segment> inner = parse(body);
        record CE(char ch, boolean b, boolean i, boolean u, boolean s, boolean k) {}
        List<CE> chars = new ArrayList<>();
        for (Segment s : inner) {
            for (int j = 0; j < s.text.length(); j++) {
                chars.add(new CE(s.text.charAt(j), s.bold || bold, s.italic || italic, s.underlined || under, s.strikethrough || strike, s.obfuscated || obf));
            }
        }

        int n = chars.size();
        if (n == 0) return Collections.emptyList();

        if (n > MAX_GRADIENT_EXPANSION) {
            int chunk = (int) Math.ceil((double) n / MAX_GRADIENT_EXPANSION);
            List<Segment> out = new ArrayList<>();
            for (int start = 0; start < n; start += chunk) {
                int end = Math.min(n, start + chunk);
                Color c = hsvToRgb((double) start / Math.max(1, n - 1), 1.0, 1.0);
                StringBuilder sb = new StringBuilder();
                boolean B = false, O = false, U = false, S = false, K = false;
                for (int k = start; k < end; k++) {
                    CE ce = chars.get(k); sb.append(ce.ch()); B |= ce.b(); O |= ce.i(); U |= ce.u(); S |= ce.s(); K |= ce.k();
                }
                out.add(new Segment(sb.toString(), c, true, B, O, U, S, K));
            }
            return out;
        }

        List<Segment> out = new ArrayList<>();
        for (int idx = 0; idx < n; idx++) {
            CE ce = chars.get(idx);
            out.add(new Segment(String.valueOf(ce.ch()), hsvToRgb((double) idx / Math.max(1, n - 1), 1.0, 1.0), true, ce.b(), ce.i(), ce.u(), ce.s(), ce.k()));
        }
        return mergeSegments(out);
    }

    private static Color lerpColor(Color a, Color b, double t) {
        return new Color(
                clamp((int) Math.round(a.r() + (b.r() - a.r()) * t)),
                clamp((int) Math.round(a.g() + (b.g() - a.g()) * t)),
                clamp((int) Math.round(a.b() + (b.b() - a.b()) * t))
        );
    }

    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    private static Color hsvToRgb(double h, double s, double v) {
        int i = (int) Math.floor(h * 6);
        double f = h * 6 - i, p = v * (1 - s), q = v * (1 - f * s), t = v * (1 - (1 - f) * s);
        double r = 0, g = 0, b = 0;
        switch (i % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return new Color((int) Math.round(r * 255), (int) Math.round(g * 255), (int) Math.round(b * 255));
    }

    private static List<Segment> mergeSegments(List<Segment> in) {
        if (in.isEmpty()) return in;
        List<Segment> out = new ArrayList<>();
        Segment cur = in.getFirst();
        for (int i = 1; i < in.size(); i++) {
            Segment s = in.get(i);
            if (Objects.equals(cur.color, s.color) && cur.bold == s.bold && cur.italic == s.italic &&
                    cur.underlined == s.underlined && cur.strikethrough == s.strikethrough &&
                    cur.obfuscated == s.obfuscated && cur.gradient == s.gradient) {
                cur = new Segment(cur.text + s.text, cur.color, cur.gradient, cur.bold, cur.italic, cur.underlined, cur.strikethrough, cur.obfuscated);
            } else {
                out.add(cur);
                cur = s;
            }
        }
        out.add(cur);
        return out;
    }
}