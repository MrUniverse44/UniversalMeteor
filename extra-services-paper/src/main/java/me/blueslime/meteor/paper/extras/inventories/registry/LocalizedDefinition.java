package me.blueslime.meteor.paper.extras.inventories.registry;

import java.util.*;

public final class LocalizedDefinition<T> {

    private final Map<String, T> definitions;

    public LocalizedDefinition(
            Map<String, T> definitions
    ) {
        LinkedHashMap<String, T> copy =
                new LinkedHashMap<>();

        if (definitions != null) {
            definitions.forEach(
                    (locale, definition) -> {
                        if (
                                locale == null ||
                                        definition == null
                        ) {
                            return;
                        }

                        copy.put(
                                normalize(locale),
                                definition
                        );
                    }
            );
        }

        this.definitions =
                Collections.unmodifiableMap(
                        copy
                );
    }

    public Optional<T> exact(
            String locale
    ) {
        if (locale == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(
                definitions.get(
                        normalize(locale)
                )
        );
    }

    public Optional<T> resolve(
            String requestedLocale,
            String fallbackLocale
    ) {
        /*
         * 1. Exact requested locale.
         */
        Optional<T> exact =
                exact(
                        requestedLocale
                );

        if (exact.isPresent()) {
            return exact;
        }

        /*
         * 2. "es_ec" -> "es".
         */
        String base =
                baseLocale(
                        requestedLocale
                );

        if (base != null) {
            Optional<T> baseDefinition =
                    exact(base);

            if (baseDefinition.isPresent()) {
                return baseDefinition;
            }
        }

        /*
         * 3. Fallback configured locale.
         */
        Optional<T> fallback =
                exact(
                        fallbackLocale
                );

        if (fallback.isPresent()) {
            return fallback;
        }

        /*
         * 4. Base fallback locale.
         */
        String fallbackBase =
                baseLocale(
                        fallbackLocale
                );

        if (fallbackBase != null) {
            Optional<T> baseFallback =
                    exact(
                            fallbackBase
                    );

            if (baseFallback.isPresent()) {
                return baseFallback;
            }
        }

        /*
         * 5. Static/global definition.
         */
        return exact("*");
    }

    public Map<String, T> definitions() {
        return definitions;
    }

    public boolean isEmpty() {
        return definitions.isEmpty();
    }

    private String baseLocale(
            String locale
    ) {
        if (locale == null) {
            return null;
        }

        String normalized =
                normalize(locale);

        int separator =
                normalized.indexOf('_');

        if (separator <= 0) {
            return null;
        }

        return normalized.substring(
                0,
                separator
        );
    }

    private String normalize(
            String locale
    ) {
        return locale
                .strip()
                .toLowerCase(
                        Locale.ROOT
                )
                .replace(
                        '-',
                        '_'
                );
    }
}
