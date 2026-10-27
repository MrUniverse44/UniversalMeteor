package me.blueslime.meteor.storage.database;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Restricted single-document update. No raw Mongo operators can be injected.
 * Use integer cents for financial increments, never binary floating point.
 */
public final class AtomicMutation {
    private static final Pattern FIELD = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*");

    private final Map<String, Object> set;
    private final Map<String, Long> increment;
    private final Set<String> unset;

    private AtomicMutation(Builder builder) {
        this.set = Collections.unmodifiableMap(new LinkedHashMap<>(builder.set));
        this.increment = Collections.unmodifiableMap(new LinkedHashMap<>(builder.increment));
        this.unset = Collections.unmodifiableSet(new LinkedHashSet<>(builder.unset));
        if (set.isEmpty() && increment.isEmpty() && unset.isEmpty()) {
            throw new IllegalArgumentException("AtomicMutation cannot be empty");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public Map<String, Object> sets() {
        return set;
    }

    public Map<String, Long> increments() {
        return increment;
    }

    public Set<String> unsets() {
        return unset;
    }

    private static String validField(String field) {
        Objects.requireNonNull(field, "field");
        if (!FIELD.matcher(field).matches()
                || field.equals("_id") || field.startsWith("_id.")
                || field.startsWith("__meteor_")) {
            throw new IllegalArgumentException("Invalid or reserved storage field: " + field);
        }
        return field;
    }

    public static final class Builder {
        private final Map<String, Object> set = new LinkedHashMap<>();
        private final Map<String, Long> increment = new LinkedHashMap<>();
        private final Set<String> unset = new LinkedHashSet<>();

        private Builder() {
        }

        private void reserve(String field) {
            validField(field);
            // Prevent conflicting update paths such as 'balance' and 'balance.amount'.
            for (String old : set.keySet()) conflict(old, field);
            for (String old : increment.keySet()) conflict(old, field);
            for (String old : unset) conflict(old, field);
        }

        private void conflict(String old, String field) {
            if (old.equals(field) || old.startsWith(field + ".") || field.startsWith(old + ".")) {
                throw new IllegalArgumentException("Conflicting atomic mutation paths: " + old + " and " + field);
            }
        }

        public Builder set(String field, Object value) {
            reserve(field);
            set.put(field, value);
            return this;
        }

        public Builder increment(String field, long delta) {
            reserve(field);
            if (delta == 0) throw new IllegalArgumentException("Increment must be nonzero");
            increment.put(field, delta);
            return this;
        }

        public Builder unset(String field) {
            reserve(field);
            unset.add(field);
            return this;
        }

        public AtomicMutation build() {
            return new AtomicMutation(this);
        }
    }
}
