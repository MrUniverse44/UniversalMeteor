package me.blueslime.meteor.platforms.api.configuration.handle;

import me.blueslime.meteor.implementation.Implements;
import me.blueslime.meteor.platforms.api.logger.PlatformLogger;
import me.blueslime.meteor.platforms.api.utils.FileUtil;

import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Represents a generic handle for configuration files across different platforms.
 * <p>
 * This class provides a unified API to manage configuration data (loading, saving, and retrieving values),
 * abstracting the underlying file format (YAML, JSON, TOML, etc.) or platform implementation.
 */
@SuppressWarnings("unused")
public abstract class ConfigurationHandle {

    protected final InputStream resource;
    protected final File file;

    public ConfigurationHandle(File file, InputStream resource) {
        this.resource = resource;
        this.file = file;
        verify();
        load();
    }

    public ConfigurationHandle(ConfigurationHandle parent, Object parentConfiguration) {
        this.resource = parent.getResource();
        this.file = parent.getFile();
    }

    public ConfigurationHandle(File file, String resource) {
        this.resource = FileUtil.build(resource);
        this.file = file;
        verify();
        load();
    }

    public ConfigurationHandle(File file) {
        this.resource = FileUtil.build(file.getName());
        this.file = file;
        verify();
        load();
    }

    /**
     * Loads or reloads the configuration from the file system into memory.
     */
    public abstract void load();

    /**
     * Verifies that the configuration file exists.
     * If it does not exist, it creates it using the internal resource.
     */
    public final void verify() {
        FileUtil.verifyExist(file, resource);
    }

    /**
     * Checks if the underlying configuration implementation matches the specified class.
     *
     * @param clazz The class to check against.
     * @param <T>   The type of the configuration class.
     * @return True if the implementation is an instance of the specified class.
     */
    public abstract <T> boolean isSpecifiedConfiguration(Class<T> clazz);

    /**
     * Returns the underlying configuration implementation cast to the expected type.
     * <p>
     * Use this when you need access to platform-specific methods not covered by this handle.
     *
     * @param <T> The type to cast to.
     * @return The underlying configuration object.
     */
    public abstract <T> T toSpecifiedConfiguration();

    /**
     * Reloads the configuration.
     * Usually an alias for {@link #load()}.
     */
    public abstract void reload();

    /**
     * Saves the current configuration data from memory to the file system.
     */
    public abstract void save();

    /**
     * Retrieves a String from the specified path.
     *
     * @param path The path to retrieve the string from.
     * @return The string value, or null/empty depending on implementation if not found.
     */
    public abstract String getString(String path);

    /**
     * Retrieves a String from the specified path with a fallback value.
     *
     * @param path The path to retrieve the string from.
     * @param def  The default value to return if the path does not exist.
     * @return The string value found, or the default value.
     */
    public abstract String getString(String path, String def);

    /**
     * Retrieves an integer from the specified path.
     *
     * @param path The path to retrieve the integer from.
     * @return The int value. Returns 0 if not found.
     */
    public abstract int getInt(String path);

    /**
     * Retrieves an integer from the specified path with a fallback value.
     *
     * @param path The path to retrieve the integer from.
     * @param def  The default value to return if the path does not exist.
     * @return The int value found, or the default value.
     */
    public abstract int getInt(String path, int def);

    /**
     * Retrieves along from the specified path.
     *
     * @param path The path to retrieve the long from.
     * @return The long value. Returns 0 if not found.
     */
    public abstract long getLong(String path);

    /**
     * Retrieves along from the specified path with a fallback value.
     *
     * @param path The path to retrieve the long from.
     * @param def  The default value to return if the path does not exist.
     * @return The long value found, or the default value.
     */
    public abstract long getLong(String path, long def);

    /**
     * Retrieves a boolean value from the specified path.
     * <p>
     * This delegates to {@link #getStatus(String)}.
     *
     * @param path The path to retrieve the boolean from.
     * @return The boolean value. Returns false if the path does not exist.
     */
    public boolean getBoolean(String path) {
        return getStatus(path);
    }

    /**
     * Retrieves a boolean value from the specified path with a fallback.
     * <p>
     * This delegates to {@link #getStatus(String, boolean)}.
     *
     * @param path The path to retrieve the boolean from.
     * @param def  The default value to return if the path does not exist.
     * @return The boolean value found, or the default value.
     */
    public boolean getBoolean(String path, boolean def) {
        return getStatus(path, def);
    }

    /**
     * Checks if a feature is enabled at the specified path.
     * Alias for {@link #getStatus(String)}.
     *
     * @param path The path to check.
     * @return True if the value at the path is true, false otherwise.
     */
    public boolean isEnabled(String path) {
        return getStatus(path);
    }

    /**
     * Checks if a feature is enabled at the specified path with a fallback.
     * Alias for {@link #getStatus(String, boolean)}.
     *
     * @param path The path to check.
     * @param def  The default value if the path is missing.
     * @return The boolean value found, or the default value.
     */
    public boolean isEnabled(String path, boolean def) {
        return getStatus(path, def);
    }

    /**
     * Retrieves the boolean status of a path.
     *
     * @param path The path to check.
     * @return The boolean value. Implementations should return false if the path is missing.
     */
    public abstract boolean getStatus(String path);

    /**
     * Retrieves the boolean status of a path with a default value.
     *
     * @param path The path to check.
     * @param def  The default value to return if the path does not exist.
     * @return The boolean value found, or the default value.
     */
    public abstract boolean getStatus(String path, boolean def);

    public abstract Object get(String path);

    public abstract Object get(String path, Object def);

    /**
     * Retrieves a raw List from the specified path.
     *
     * @param path The path of the list.
     * @return The list found, or null/empty depending on implementation.
     */
    public abstract List<?> getList(String path);

    /**
     * Retrieves a raw List from the specified path with a fallback.
     *
     * @param path The path of the list.
     * @param def  The default list to return if the path does not exist.
     * @return The list found, or the default list.
     */
    public abstract List<?> getList(String path, List<?> def);

    /**
     * Retrieves a list of Strings from the specified path.
     *
     * @param path The path of the list.
     * @return A list of strings. Returns an empty list if not found.
     */
    public abstract List<String> getStringList(String path);

    /**
     * Retrieves a list of Integers from the specified path.
     *
     * @param path The path of the list.
     * @return A list of integers.
     */
    public abstract List<Integer> getIntList(String path);

    public abstract List<Long> getLongList(String path);

    public abstract List<Boolean> getBooleanList(String path);

    public abstract List<Byte> getByteList(String path);

    public abstract List<Character> getCharList(String path);

    public abstract List<Float> getFloatList(String path);

    /**
     * Checks if the specified path exists in the configuration.
     *
     * @param path The path to check.
     * @return True if the path exists, false otherwise.
     */
    public abstract boolean contains(String path);

    /**
     * Sets a value at the specified path.
     * <p>
     * Note: This usually requires calling {@link #save()} afterward to persist changes to disk.
     *
     * @param path  The path where the value will be set.
     * @param value The value to store.
     */
    public abstract void set(String path, Object value);

    /**
     * Return the keys contained in the section located at the specified path.<br>
     * This method will not create any missing sections (non-mutating).<br>
     * If the path does not exist locally, defaults will be consulted (also non-mutating).<br>
     * <br>
     * Examples:<br>
     *  - getKeys("", false) -> same as getKeys() on this node<br>
     *  - getKeys("a", false) -> immediate children of section 'a' (if exists)<br>
     *  - getKeys("a", true) -> deep children of section 'a' (e.g. "b", "b.c", ...)
     *
     * @param path    dot-separated path to the section (empty or null means this node)
     * @param getKeys whether to return keys recursively (deep)
     * @return set of keys (empty set if the path does not exist)
     */
    public abstract Set<String> getKeys(String path, boolean getKeys);

    /**
     * Retrieves a set of all keys in the configuration.
     *
     * @param deep If true, returns all keys recursively (e.g., "section.subsection.key").
     * If false, returns only top-level keys.
     * @return A set of keys.
     */
    public abstract Set<String> getKeys(boolean deep);

    /**
     * Retrieves a subsection of the configuration.
     *
     * @param path The path of the section.
     * @return A ConfigurationHandle representing that section.
     */
    public abstract ConfigurationHandle getSection(String path);

    /**
     * Creates a new empty section at the specified path.
     *
     * @param path The path to create the section at.
     * @return The newly created ConfigurationHandle section.
     */
    public abstract ConfigurationHandle createSection(String path);

    public Random getRandom() {
        return Implements.fetch(Random.class);
    }

    public PlatformLogger getLogger() {
        return Implements.fetch(PlatformLogger.class);
    }

    public InputStream getResource() {
        return resource;
    }

    public File getFile() {
        return file;
    }
}