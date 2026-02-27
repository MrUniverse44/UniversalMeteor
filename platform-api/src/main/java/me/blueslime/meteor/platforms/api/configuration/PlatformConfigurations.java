package me.blueslime.meteor.platforms.api.configuration;

import me.blueslime.meteor.platforms.api.configuration.handle.DefaultConfigurationHandle;
import me.blueslime.meteor.platforms.api.configuration.handle.ConfigurationHandle;

import java.io.InputStream;
import java.nio.file.Path;
import java.io.File;

/**
 * PlatformConfigurations is a platform-agnostic facade that lets the core Platform<br>
 * request configuration handles.
 **/
public interface PlatformConfigurations {

    ConfigurationHandle load(File fileName, InputStream resource);
    ConfigurationHandle load(File fileName, String resource);

    ConfigurationHandle load(Path path, InputStream resource);
    ConfigurationHandle load(Path path, String resource);

    ConfigurationHandle load(File fileName);
    ConfigurationHandle load(Path path);

    PlatformConfigurations DEFAULT = new PlatformConfigurations() {
        @Override
        public ConfigurationHandle load(File fileName, InputStream resource) {
            ConfigurationHandle handle = new DefaultConfigurationHandle(fileName, resource);
            handle.load();
            return handle;
        }

        @Override
        public ConfigurationHandle load(File fileName, String resource) {
            ConfigurationHandle handle = new DefaultConfigurationHandle(fileName, resource);
            handle.load();
            return handle;
        }

        @Override
        public ConfigurationHandle load(Path path, InputStream resource) {
            ConfigurationHandle handle = new DefaultConfigurationHandle(path.toFile(), resource);
            handle.load();
            return handle;
        }

        @Override
        public ConfigurationHandle load(Path path, String resource) {
            ConfigurationHandle handle = new DefaultConfigurationHandle(path.toFile(), resource);
            handle.load();
            return handle;
        }

        @Override
        public ConfigurationHandle load(File fileName) {
            ConfigurationHandle handle = new DefaultConfigurationHandle(fileName);
            handle.load();
            return handle;
        }

        @Override
        public ConfigurationHandle load(Path path) {
            return load(path.toFile());
        }
    };
}

