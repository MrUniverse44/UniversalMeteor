package me.blueslime.meteor.paper.extras.languages.locale;

import java.util.Objects;

public record Locale(
        String language,
        String country
) {

    public Locale {
        if (
            language == null ||
            language.isBlank()
        ) {
            throw new InvalidLocaleException(
                    String.valueOf(language)
            );
        }

        language =
                language
                        .strip()
                        .toLowerCase(
                                java.util.Locale.ROOT
                        );

        if (country != null) {
            country =
                    country.strip();

            if (country.isBlank()) {
                country =
                        null;

            } else {
                country =
                        country.toUpperCase(
                                java.util.Locale.ROOT
                        );
            }
        }
    }

    public Locale(
            String language
    ) {
        this(
                language,
                null
        );
    }

    public String getLanguage() {
        return language;
    }

    public String getCountry() {
        return country;
    }

    public boolean hasCountry() {
        return country != null;
    }

    /**
     * Canonical Meteor locale id:
     *
     * en
     * en_US
     * es_EC
     */
    public String getId() {
        if (!hasCountry()) {
            return language;
        }

        return language
                + "_"
                + country;
    }

    public Locale base() {
        if (!hasCountry()) {
            return this;
        }

        return new Locale(
                language
        );
    }

    public boolean sameLanguage(
            Locale other
    ) {
        return other != null &&
                language.equals(
                        other.language
                );
    }

    public java.util.Locale toJavaLocale() {
        if (!hasCountry()) {
            return java.util.Locale.of(
                    language
            );
        }

        return java.util.Locale.of(
                language,
                country
        );
    }

    public static Locale fromJava(
            java.util.Locale locale
    ) {
        Objects.requireNonNull(
                locale,
                "locale"
        );

        String language =
                locale.getLanguage();

        String country =
                locale.getCountry();

        if (
                language == null ||
                        language.isBlank()
        ) {
            throw new InvalidLocaleException(
                    locale.toLanguageTag()
            );
        }

        return new Locale(
                language,
                country == null ||
                        country.isBlank()
                        ? null
                        : country
        );
    }

    public static Locale fromString(
            String input
    ) {
        if (
                input == null ||
                        input.isBlank()
        ) {
            throw new InvalidLocaleException(
                    String.valueOf(input)
            );
        }

        /*
         * Accept both:
         *
         * en_US
         * en-US
         * en
         */
        String normalized =
                input
                        .strip()
                        .replace(
                                '_',
                                '-'
                        );

        java.util.Locale locale =
                java.util.Locale.forLanguageTag(
                        normalized
                );

        if (
                locale.getLanguage() == null ||
                        locale.getLanguage().isBlank()
        ) {
            throw new InvalidLocaleException(
                    input
            );
        }

        return fromJava(
                locale
        );
    }

    @Override
    public String toString() {
        return getId();
    }
}