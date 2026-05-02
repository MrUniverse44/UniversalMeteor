package me.blueslime.meteor.color.renders;

import me.blueslime.meteor.color.UniversalColorParser;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@SuppressWarnings("deprecation")
public class BungeeRenderer implements Renderer<BaseComponent[]> {

    private static BungeeRenderer instance = null;

    public static BaseComponent[] create(String textToRender) {
        return create(UniversalColorParser.parse(textToRender));
    }

    public static BaseComponent[] create(List<UniversalColorParser.Segment> segments) {
        if (instance == null) {
            instance = new BungeeRenderer();
        }
        return instance.render(segments);
    }

    public static BaseComponent[] translate(String textToRender) {
        return create(UniversalColorParser.parse(textToRender));
    }

    public static BaseComponent[] translate(List<UniversalColorParser.Segment> segments) {
        return instance.render(segments);
    }

    public static List<BaseComponent[]> translateList(Collection<String> collection) {
        return collection.stream().map(BungeeRenderer::translate).collect(Collectors.toList());
    }

    public static TextComponent translateAsSingle(String textToRender) {
        BaseComponent[] components = translate(textToRender);
        return new TextComponent(components);
    }

    public static List<TextComponent> translateListAsSingles(Collection<String> collection) {
        //noinspection Convert2MethodRef
        return collection.stream().map(BungeeRenderer::translate).map(array -> new TextComponent(array)).collect(Collectors.toList());
    }

    @Override
    public BaseComponent[] render(List<UniversalColorParser.Segment> segments) {
        List<BaseComponent> comps = new ArrayList<>();
        for (UniversalColorParser.Segment s : segments) {
            TextComponent tc = new TextComponent(s.text);
            if (s.color != null) {
                try {
                    tc.setColor(ChatColor.of(s.color.toHex()));
                } catch (Throwable ignored) {

                }
            }
            tc.setBold(s.bold);
            tc.setItalic(s.italic);
            tc.setUnderlined(s.underlined);
            tc.setStrikethrough(s.strikethrough);
            tc.setObfuscated(s.obfuscated);

            if (s.clickAction != null && s.clickValue != null) {
                ClickEvent.Action bungeeAction = switch (s.clickAction) {
                    case RUN_COMMAND -> ClickEvent.Action.RUN_COMMAND;
                    case SUGGEST_COMMAND -> ClickEvent.Action.SUGGEST_COMMAND;
                    case OPEN_URL -> ClickEvent.Action.OPEN_URL;
                    case COPY_TO_CLIPBOARD -> ClickEvent.Action.COPY_TO_CLIPBOARD;
                };
                tc.setClickEvent(new ClickEvent(bungeeAction, s.clickValue));
            } else {
                tc.setClickEvent(null);
            }

            if (s.hoverValue != null) {
                BaseComponent[] hoverComponents = translate(s.hoverValue);
                tc.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(hoverComponents)));
            } else {
                tc.setHoverEvent(null);
            }

            comps.add(tc);
        }
        return comps.toArray(new BaseComponent[0]);
    }

    public static String stripColor(String message) {
        return UniversalColorParser.stripColor(message);
    }
}
