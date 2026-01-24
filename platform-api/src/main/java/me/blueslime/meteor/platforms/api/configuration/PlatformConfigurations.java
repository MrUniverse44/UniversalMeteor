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
            return new DefaultConfigurationHandle(fileName, resource);
        }

        @Override
        public ConfigurationHandle load(File fileName, String resource) {
            return new DefaultConfigurationHandle(fileName, resource);
        }

        @Override
        public ConfigurationHandle load(Path path, InputStream resource) {
            return new DefaultConfigurationHandle(path.toFile(), resource);
        }

        @Override
        public ConfigurationHandle load(Path path, String resource) {
            return new DefaultConfigurationHandle(path.toFile(), resource);
        }

        @Override
        public ConfigurationHandle load(File fileName) {
            return new DefaultConfigurationHandle(fileName);
        }

        @Override
        public ConfigurationHandle load(Path path) {
            return load(path.toFile());
        }
    };
}

