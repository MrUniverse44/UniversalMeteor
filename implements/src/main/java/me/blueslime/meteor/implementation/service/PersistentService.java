package me.blueslime.meteor.implementation.service;

public interface PersistentService extends RegisteredService {
    @Override
    default boolean isPersistent() {
        return true;
    }

    @Override
    default void unregisterImplementedService() {

    }
}
