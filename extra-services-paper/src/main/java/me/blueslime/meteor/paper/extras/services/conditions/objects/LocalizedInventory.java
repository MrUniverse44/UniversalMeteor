package me.blueslime.meteor.paper.extras.services.conditions.objects;

import me.blueslime.meteor.paper.extras.services.conditions.CompiledCondition;
import me.blueslime.meteor.paper.extras.services.item.ItemWrapper;
import me.blueslime.meteor.paper.extras.services.languages.locale.Locale;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public record LocalizedInventory(
    Locale locale,
    Map<String, Map<String, List<CompiledCondition>>> itemConditions,
    Map<String, List<ItemWrapper>> itemMap,
    Map<String, List<String>> itemActions
) {

    public static LocalizedInventory fromLocale(String locale) {
        return new LocalizedInventory(Locale.fromString(locale), new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), new ConcurrentHashMap<>());
    }

    public static LocalizedInventory fromLocale(Locale locale) {
        return new LocalizedInventory(locale, new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), new ConcurrentHashMap<>());
    }

    public boolean isCompatible(Locale locale) {
        return this.locale.getLanguage().equals(locale.getLanguage());
    }

    public boolean isEmpty() {
        return itemMap.isEmpty();
    }

    public boolean containsInventory(String id) {
        return itemMap.containsKey(id);
    }
}
