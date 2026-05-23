package me.blueslime.meteor.storage.query;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class StorageQuery {
    private final Map<String, Object> filters = new HashMap<>();
    private String sortBy = null;
    private boolean sortDescending = false;
    private Integer limit = null;

    public StorageQuery filter(String key, Object value) {
        filters.put(key, value);
        return this;
    }

    // Mayor que
    public StorageQuery greaterThan(String key, Object value) {
        return addOperation(key, "$gt", value);
    }

    public StorageQuery lessThan(String key, Object value) {
        return addOperation(key, "$lt", value);
    }

    public StorageQuery contains(String key, String value) {
        return addOperation(key, "$regex", ".*" + Pattern.quote(value) + ".*");
    }

    public StorageQuery startsWith(String key, String value) {
        return addOperation(key, "$regex", "^" + Pattern.quote(value));
    }

    public StorageQuery endsWith(String key, String value) {
        return addOperation(key, "$regex", Pattern.quote(value) + "$");
    }

    public StorageQuery isPresent(String key, boolean present) {
        return addOperation(key, "$exists", present);
    }

    @SuppressWarnings("unchecked")
    private StorageQuery addOperation(String key, String operator, Object value) {
        Object existing = filters.get(key);
        Map<String, Object> operationMap;

        if (existing instanceof Map) {
            operationMap = (Map<String, Object>) existing;
        } else {
            operationMap = new HashMap<>();
        }

        operationMap.put(operator, value);

        if ("$regex".equals(operator)) {
            operationMap.put("$options", "i");
        }

        filters.put(key, operationMap);
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