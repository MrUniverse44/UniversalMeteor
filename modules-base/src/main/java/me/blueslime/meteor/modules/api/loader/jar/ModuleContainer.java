package me.blueslime.meteor.modules.api.loader.jar;

import me.blueslime.meteor.modules.api.api.PlatformModule;
import me.blueslime.meteor.modules.api.loader.ModuleIsolatedClassLoader;

import java.io.File;
import java.util.List;

public record ModuleContainer(List<Class<? extends PlatformModule>> modules, File jarFile, ModuleIsolatedClassLoader classLoader) {
}
