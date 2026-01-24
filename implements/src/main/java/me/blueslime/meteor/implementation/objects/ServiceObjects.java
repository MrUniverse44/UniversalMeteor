package me.blueslime.meteor.implementation.objects;

import me.blueslime.meteor.implementation.Implementer;
import me.blueslime.meteor.implementation.Implements;
import me.blueslime.meteor.implementation.service.Service;
import me.blueslime.meteor.utilities.consumer.PluginConsumer;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

@SuppressWarnings("unused")
public class ServiceObjects<T extends Service> implements Implementer {

    private final Set<Exception> exceptionSet = new HashSet<>();
    private final Set<T> serviceSet = new HashSet<>();

    public ServiceObjects() {

    }

    @SafeVarargs
    public final ServiceObjects<T> at(T... service) {
        Collections.addAll(serviceSet, service);
        return this;
    }

    @SafeVarargs
    public final ServiceObjects<T> at(Class<? extends T>... service) {
        for (Class<? extends T> clazz : service) {
            PluginConsumer.process(
                () -> serviceSet.add(Implements.createInstance(clazz)),
                exceptionSet::add
            );
        }
        return this;
    }

    public ServiceObjects<T> onThrow(Consumer<Exception> consumer) {
        for (Exception exception : exceptionSet) {
            consumer.accept(exception);
        }
        return this;
    }

    public ServiceObjects<T> execute(Consumer<T> execute) {
        serviceSet.forEach(
            service -> PluginConsumer.process(
                () -> execute.accept(service), exceptionSet::add
            )
        );
        return this;
    }

}
