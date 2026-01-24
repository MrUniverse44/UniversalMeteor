package me.blueslime.meteor.platforms.api.service;

import me.blueslime.meteor.implementation.service.Service;
import me.blueslime.meteor.platforms.api.Platforms;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * ServiceContainer<br>
 * <br>
 * Small helper to declare a group of services that apply to a set of platforms.<br>
 * Example Usage:
 *   <pre>{@code ServiceContainer.of(Platforms.PAPER).with(PaperServiceOne.class, PaperServiceTwo.class)}</pre>
 */
public final class ServiceContainer {

    private final Set<Platforms> platforms = new HashSet<>();
    private Class<? extends Service>[] serviceClasses;

    private ServiceContainer(Platforms... platforms) {
        if (platforms == null || platforms.length == 0) this.platforms.add(Platforms.UNIVERSAL);
        else this.platforms.addAll(Arrays.asList(platforms));
    }

    @SuppressWarnings("SafeVarargsOnNonReifiableType")
    @SafeVarargs
    public static ServiceContainer of(Platforms... platforms) {
        return new ServiceContainer(platforms);
    }

    @SafeVarargs
    public final ServiceContainer with(Class<? extends Service>... classes) {
        this.serviceClasses = classes;
        return this;
    }

    public boolean appliesTo(Platforms p) {
        if (platforms.contains(Platforms.UNIVERSAL)) return true;
        return platforms.contains(p);
    }

    @SuppressWarnings("unchecked")
    public Class<? extends Service>[] getServiceClasses() {
        return serviceClasses == null ? new Class[0] : serviceClasses;
    }
}

