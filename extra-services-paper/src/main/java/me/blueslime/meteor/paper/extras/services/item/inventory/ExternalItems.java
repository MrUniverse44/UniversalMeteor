package me.blueslime.meteor.paper.extras.services.item.inventory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ExternalItems {

    private final Map<String, List<String>> itemActions = new ConcurrentHashMap<>();

    public List<String> register(String id, List<String> actions) {
        return itemActions.put(id, actions);
    }

    public boolean contains(String id) {
        return itemActions.containsKey(id);
    }

    public List<String> remove(String id) {
        return itemActions.remove(id);
    }

    public List<String> get(String id) {
        return itemActions.get(id);
    }

}
