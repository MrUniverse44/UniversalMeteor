package me.blueslime.meteor.modules.api.loader.jar;

import me.blueslime.meteor.modules.api.api.PlatformModule;
import me.blueslime.meteor.modules.api.loader.ModuleIsolatedClassLoader;

import java.util.List;

public class ModuleLoaderContainer {

    private final List<Class<? extends PlatformModule>> moduleClasses;
    private final ModuleIsolatedClassLoader classLoader;

    public ModuleLoaderContainer(List<Class<? extends PlatformModule>> moduleClasses, ModuleIsolatedClassLoader classLoader) {
        this.moduleClasses = moduleClasses;
        this.classLoader = classLoader;
    }

    public ModuleIsolatedClassLoader getClassLoader() {
        return classLoader;
    }

    public List<Class<? extends PlatformModule>> getModuleClasses() {
        return moduleClasses;
    }
}
