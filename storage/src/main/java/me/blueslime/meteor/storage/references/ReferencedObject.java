package me.blueslime.meteor.storage.references;

import java.util.Locale;

public class ReferencedObject {

    private final String original;
    private final String objectId;

    public ReferencedObject(String original, String objectId) {
        this.original = original;
        this.objectId = objectId;
    }

    public String getSavedId() {
        return original.toLowerCase(Locale.ENGLISH);
    }

    public String getObject() {
        return objectId;
    }

    public String getOriginalId() {
        return original;
    }

}
