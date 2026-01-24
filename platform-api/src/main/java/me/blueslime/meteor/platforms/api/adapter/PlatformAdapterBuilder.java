package me.blueslime.meteor.platforms.api.adapter;

import me.blueslime.meteor.implementation.Implements;
import me.blueslime.meteor.platforms.api.info.PluginInfo;
import me.blueslime.meteor.platforms.api.plugin.PlatformPlugin;
import me.blueslime.meteor.utilities.consumer.PluginConsumer;

import java.lang.reflect.Constructor;

public abstract class PlatformAdapterBuilder<P extends PlatformPlugin<L>, I, L> {

    protected final PluginInfo<L> info = new PluginInfo<>();
    protected final Class<P> clazz;

    public PlatformAdapterBuilder(Class<P> mainClass) {
        this.clazz = mainClass;
    }

    public abstract PlatformAdapterBuilder<P, I, L> registerMainClass(I pluginInstance);

    /**
     * Register a class to the Implements system, so this class will be accessible for all your project.<br>
     * By default, it will be persistent
     * @param clazz to identify
     * @param value instance
     * @return this adapter builder instance
     * @param <T> type of value
     */
    public <T> PlatformAdapterBuilder<P, I, L> register(Class<T> clazz, T value) {
        return register(clazz, value, true);
    }

    /**
     * Register a class to the Implements system, so this class will be accessible for all your project.
     * @param clazz to identify
     * @param value instance
     * @param persist if enabled, this value will not be removed on reload or server shutdown.
     * @return this adapter builder instance
     * @param <T> type of value
     */
    public <T> PlatformAdapterBuilder<P, I, L> register(Class<T> clazz, T value, boolean persist) {
        Implements.setEntry(clazz, value, persist);
        return this;
    }

    /**
     * Register a class to the Implements system, so this class will be accessible for all your project.<br>
     * By default, it will be persistent.
     * @param clazz to identify
     * @param identifier name for this instance
     * @param value instance
     * @return this adapter builder instance
     * @param <T> type of value
     */
    public <T> PlatformAdapterBuilder<P, I, L> register(Class<T> clazz, String identifier, T value) {
        return register(clazz, identifier, value, true);
    }

    /**
     * Register a class to the Implements system, so this class will be accessible for all your project.
     * @param clazz to identify
     * @param identifier name for this instance
     * @param value instance
     * @param persist if enabled, this value will not be removed on reload or server shutdown.
     * @return this adapter builder instance
     * @param <T> type of value
     */
    public <T> PlatformAdapterBuilder<P, I, L> register(Class<T> clazz, String identifier, T value, boolean persist) {
        Implements.setEntry(clazz, identifier, value, persist);
        return this;
    }

    public P build() {
        Constructor<P> constructor = PluginConsumer.ofUnchecked(
            () -> clazz.getDeclaredConstructor(
                PluginInfo.class
            ),
            e -> {
            },
            () -> null
        );

        if (constructor == null) {
            throw new IllegalStateException("No PlatformPlugin constructor provided");
        }

        return PluginConsumer.ofUnchecked(
            () -> {
                constructor.setAccessible(true);

                return constructor.newInstance(info);
            },
            e -> info.getLogger().error(e, "Can't initialize main class"),
            () -> null
        );
    }

}
