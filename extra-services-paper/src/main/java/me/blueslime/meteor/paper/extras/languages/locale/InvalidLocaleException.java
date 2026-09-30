package me.blueslime.meteor.paper.extras.languages.locale;

public final class InvalidLocaleException
        extends IllegalArgumentException {

    public InvalidLocaleException(
            String locale
    ) {
        super(
                "Invalid locale '"
                        + locale
                        + "'. Expected a locale such as 'en', 'en_US' or 'en-US'."
        );
    }
}