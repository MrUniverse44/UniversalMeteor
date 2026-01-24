package me.blueslime.meteor.implementation.registration;

import me.blueslime.meteor.implementation.service.PersistentService;

public class RegisteredServiceInstance implements PersistentService {
    private static final RegisteredServiceInstance instance = new RegisteredServiceInstance();

    public static RegisteredServiceInstance getInstance() {
        return instance;
    }
}
