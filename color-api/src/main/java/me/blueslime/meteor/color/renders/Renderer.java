package me.blueslime.meteor.color.renders;

import me.blueslime.meteor.color.UniversalColorParser;

import java.util.List;

public interface Renderer<T> {
    T render(List<UniversalColorParser.Segment> segments);
}
