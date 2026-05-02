package me.blueslime.meteor.color.renders;

import me.blueslime.meteor.color.UniversalColorParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class ComponentRenderer implements Renderer<net.kyori.adventure.text.Component> {

    private static final ComponentRenderer instance = new ComponentRenderer();

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

    public static List<Component> translateList(Collection<String> collection) {
        return collection.stream().map(ComponentRenderer::translate).collect(Collectors.toList());
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

            if (s.clickAction != null && s.clickValue != null) {
                ClickEvent.Action adventureAction = switch (s.clickAction) {
                    case RUN_COMMAND -> ClickEvent.Action.RUN_COMMAND;
                    case SUGGEST_COMMAND -> ClickEvent.Action.SUGGEST_COMMAND;
                    case OPEN_URL -> ClickEvent.Action.OPEN_URL;
                    case COPY_TO_CLIPBOARD -> ClickEvent.Action.COPY_TO_CLIPBOARD;
                };
                part = part.clickEvent(
                    ClickEvent.clickEvent(
                        adventureAction,
                        ClickEvent.Payload.string(s.clickValue)
                    )
                );
            }

            if (s.hoverValue != null) {
                Component hoverComponent = translate(s.hoverValue);
                part = part.hoverEvent(HoverEvent.showText(hoverComponent));
            }

            result = result.append(part);
        }
        return result;
    }

    public static String stripColor(String message) {
        return UniversalColorParser.stripColor(message);
    }

}

