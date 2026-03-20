package me.blueslime.meteor.color.renders;

import me.blueslime.meteor.color.UniversalColorParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class VelocitySpongeRenderer implements Renderer<net.kyori.adventure.text.Component> {

    private static final VelocitySpongeRenderer instance = new VelocitySpongeRenderer();

    public static Component create(String textToRender) {
        return create(UniversalColorParser.parse(textToRender));
    }

    public static Component create(List<UniversalColorParser.Segment> segments) {
        return instance.render(segments);
    }

    public static Component translate(String textToRender) {
        return create(UniversalColorParser.parse(textToRender));
    }

    public static Component translate(List<UniversalColorParser.Segment> segments) {
        return instance.render(segments);
    }

    public static List<Component> translate(Collection<String> collection) {
        return collection.stream().map(VelocitySpongeRenderer::translate).collect(Collectors.toList());
    }

    @Override
    public Component render(List<UniversalColorParser.Segment> segments) {
        Component result = Component.empty();
        for (UniversalColorParser.Segment s : segments) {
            Component part = Component.text(s.text == null ? "" : s.text);
            if (s.color != null) {
                part = part.color(TextColor.color(s.color.r(), s.color.g(), s.color.b()));
            }
            part = part.decoration(TextDecoration.BOLD, s.bold);
            part = part.decoration(TextDecoration.ITALIC, s.italic);
            part = part.decoration(TextDecoration.UNDERLINED, s.underlined);
            part = part.decoration(TextDecoration.STRIKETHROUGH, s.strikethrough);
            part = part.decoration(TextDecoration.OBFUSCATED, s.obfuscated);

            result = result.append(part);
        }
        return result;
    }
}
