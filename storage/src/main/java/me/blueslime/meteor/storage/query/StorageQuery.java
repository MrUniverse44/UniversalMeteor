package me.blueslime.meteor.storage.query;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public final class StorageQuery {

    public enum Operator {
        EQUALS,
        GREATER_THAN,
        LESS_THAN,
        CONTAINS,
        STARTS_WITH,
        ENDS_WITH,
        PRESENT
    }

    public record Condition(
            String key,
            Operator operator,
            Object value
    ) {
        public Condition {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(operator, "operator");

            if (key.isBlank()) {
                throw new IllegalArgumentException("StorageQuery key cannot be blank");
            }
        }
    }

    private final Map<String, Object> filters = new LinkedHashMap<>();
    private final List<Condition> conditions = new ArrayList<>();

    private String sortBy;
    private boolean sortDescending;
    private Integer limit;

    public StorageQuery filter(String key, Object value) {
        validateKey(key);

        filters.put(key, value);
        conditions.removeIf(condition -> condition.key().equals(key));
        conditions.add(new Condition(key, Operator.EQUALS, value));

        return this;
    }

    public StorageQuery greaterThan(String key, Object value) {
        return addOperation(key, "$gt", Operator.GREATER_THAN, value);
    }

    public StorageQuery lessThan(String key, Object value) {
        return addOperation(key, "$lt", Operator.LESS_THAN, value);
    }

    public StorageQuery contains(String key, String value) {
        Objects.requireNonNull(value, "value");

        addCondition(key, Operator.CONTAINS, value);

        return addLegacyOperation(
                key,
                "$regex",
                ".*" + Pattern.quote(value) + ".*",
                true
        );
    }

    public StorageQuery startsWith(String key, String value) {
        Objects.requireNonNull(value, "value");

        addCondition(key, Operator.STARTS_WITH, value);

        return addLegacyOperation(
                key,
                "$regex",
                "^" + Pattern.quote(value),
                true
        );
    }

    public StorageQuery endsWith(String key, String value) {
        Objects.requireNonNull(value, "value");

        addCondition(key, Operator.ENDS_WITH, value);

        return addLegacyOperation(
                key,
                "$regex",
                Pattern.quote(value) + "$",
                true
        );
    }

    public StorageQuery isPresent(String key, boolean present) {
        return addOperation(key, "$exists", Operator.PRESENT, present);
    }

    public StorageQuery sort(String key, boolean descending) {
        validateKey(key);

        this.sortBy = key;
        this.sortDescending = descending;

        return this;
    }

    public StorageQuery limit(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("StorageQuery limit must be greater than zero");
        }

        this.limit = limit;

        return this;
    }

    public List<Condition> getConditions() {
        return Collections.unmodifiableList(conditions);
    }

    /**
     * Legacy representation kept for compatibility with older storage backends.
     * New backends should prefer {@link #getConditions()}.
     */
    public Map<String, Object> getFilters() {
        return Collections.unmodifiableMap(filters);
    }

    public String getSortBy() {
        return sortBy;
    }

    public boolean isSortDescending() {
        return sortDescending;
    }

    public Integer getLimit() {
        return limit;
    }

    public boolean isEmpty() {
        return conditions.isEmpty();
    }

    private StorageQuery addOperation(
            String key,
            String legacyOperator,
            Operator operator,
            Object value
    ) {
        addCondition(key, operator, value);

        return addLegacyOperation(
                key,
                legacyOperator,
                value,
                false
        );
    }

    private void addCondition(
            String key,
            Operator operator,
            Object value
    ) {
        validateKey(key);

        conditions.removeIf(condition -> {
            if (!condition.key().equals(key)) {
                return false;
            }

            if (condition.operator() == operator) {
                return true;
            }

            /*
             * The legacy map representation cannot express a scalar equality
             * and operator map for the same key at the same time.
             */
            if (
                    operator != Operator.EQUALS
                            && condition.operator() == Operator.EQUALS
            ) {
                return true;
            }

            /*
             * Legacy regex representation stores one $regex per key, so keep
             * only the most recently requested text-matching operation.
             */
            boolean newRegex =
                    operator == Operator.CONTAINS
                            || operator == Operator.STARTS_WITH
                            || operator == Operator.ENDS_WITH;

            boolean oldRegex =
                    condition.operator() == Operator.CONTAINS
                            || condition.operator() == Operator.STARTS_WITH
                            || condition.operator() == Operator.ENDS_WITH;

            return newRegex && oldRegex;
        });

        conditions.add(
                new Condition(
                        key,
                        operator,
                        value
                )
        );
    }

    @SuppressWarnings("unchecked")
    private StorageQuery addLegacyOperation(
            String key,
            String operator,
            Object value,
            boolean regex
    ) {
        validateKey(key);

        Object existing = filters.get(key);

        Map<String, Object> operationMap;

        if (existing instanceof Map<?, ?> map) {
            operationMap = (Map<String, Object>) map;
        } else {
            operationMap = new LinkedHashMap<>();
        }

        operationMap.put(operator, value);

        if (regex) {
            operationMap.put("$options", "i");
        }

        filters.put(key, operationMap);

        return this;
    }

    private static void validateKey(String key) {
        Objects.requireNonNull(key, "key");

        if (key.isBlank()) {
            throw new IllegalArgumentException("StorageQuery key cannot be blank");
        }
    }
}
