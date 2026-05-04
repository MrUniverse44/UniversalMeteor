package me.blueslime.meteor.storage.query;

import java.util.HashMap;
import java.util.Map;

public class StorageQuery {
    private final Map<String, Object> filters = new HashMap<>();
    private String sortBy = null;
    private boolean sortDescending = false;
    private Integer limit = null;

    public StorageQuery filter(String key, Object value) {
        filters.put(key, value);
        return this;
    }

    public StorageQuery sort(String key, boolean descending) {
        this.sortBy = key;
        this.sortDescending = descending;
        return this;
    }

    public StorageQuery limit(int limit) {
        this.limit = limit;
        return this;
    }

    public Map<String, Object> getFilters() { return filters; }
    public String getSortBy() { return sortBy; }
    public boolean isSortDescending() { return sortDescending; }
    public Integer getLimit() { return limit; }
}
