package me.blueslime.meteor.modules.api.loader.jar;

import me.blueslime.meteor.modules.api.api.PlatformModule;
import me.blueslime.meteor.modules.api.api.module.data.ModuleData;
import me.blueslime.meteor.modules.api.loader.ModuleIsolatedClassLoader;
import me.blueslime.meteor.modules.api.loader.state.ModuleState;

import java.io.File;

public class ModuleIndividualContainer {

    private ModuleIsolatedClassLoader classLoader;
    private PlatformModule platformModule;
    private final ModuleData data;
    private final File jarFile;
    private ModuleState state;

    public ModuleIndividualContainer(PlatformModule platformModule, ModuleData data, ModuleState state, File jarFile, ModuleIsolatedClassLoader classLoader) {
        this.platformModule = platformModule;
        this.classLoader = classLoader;
        this.data = data;
        this.jarFile = jarFile;
        this.state = state;
    }

    public void setPlatformModule(PlatformModule platformModule) {
        this.platformModule = platformModule;
    }

    public void setClassLoader(ModuleIsolatedClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public void setState(ModuleState state) {
        this.state = state;
    }

    public ModuleIsolatedClassLoader getClassLoader() {
        return classLoader;
    }

    public PlatformModule getPlatformModule() {
        return platformModule;
    }

    public File getJarFile() {
        return jarFile;
    }

    public ModuleData getData() {
        return data;
    }

    public ModuleState getState() {
        return state;
    }
}
