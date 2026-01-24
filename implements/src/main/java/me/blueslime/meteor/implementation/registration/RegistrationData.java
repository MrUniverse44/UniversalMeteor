package me.blueslime.meteor.implementation.registration;

import me.blueslime.meteor.implementation.service.Service;

public class RegistrationData {
    private final String identifier;
    private final Class<?> clazz;
    private final Service service;

    private RegistrationData(Class<?> clazz) {
        this(null, clazz, null);
    }

    private RegistrationData(Service service, Class<?> clazz) {
        this(service, clazz, null);
    }

    private RegistrationData(Class<?> clazz, String identifier) {
        this(null, clazz, identifier);
    }

    private RegistrationData(Service service, Class<?> clazz, String identifier) {
        if (clazz == null) {
            throw new NullPointerException("Class cannot be null in a @Register");
        }
        this.identifier = identifier;
        this.service = service;
        this.clazz = clazz;
    }

    public static RegistrationData fromData(Class<?> clazz) {
        return new RegistrationData(clazz);
    }

    public static RegistrationData fromData(Class<?> clazz, String identifier) {
        return new RegistrationData(clazz, identifier);
    }

    public static RegistrationData fromData(Service service, Class<?> clazz) {
        return new RegistrationData(service, clazz);
    }

    public static RegistrationData fromData(Service service, Class<?> clazz, String identifier) {
        return new RegistrationData(service, clazz, identifier);
    }

    public Class<?> getInstance() {
        return clazz;
    }

    public String getIdentifier() {
        return identifier;
    }

    public Service getParentService() {
        return service;
    }

    @Override
    public RegistrationData clone() {
        return new RegistrationData(service, clazz, identifier);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RegistrationData that = (RegistrationData) o;
        if (!clazz.equals(that.clazz)) return false;
        return identifier != null ? identifier.equals(that.identifier) : that.identifier == null;
    }

    @Override
    public int hashCode() {
        int result = clazz.hashCode();
        result = 31 * result + (identifier != null ? identifier.hashCode() : 0);
        return result;
    }
}

