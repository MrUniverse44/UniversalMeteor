package me.blueslime.meteor.platforms.api.configuration.handle;

import me.blueslime.meteor.platforms.api.utils.files.PluginConfiguration;
import me.blueslime.meteor.platforms.api.utils.files.YamlConfiguration;
import me.blueslime.meteor.utilities.consumer.PluginConsumer;

import java.util.List;
import java.util.Set;

public class DefaultSectionConfigurationHandle extends ConfigurationHandle {

    private final PluginConfiguration configuration;

    public DefaultSectionConfigurationHandle(ConfigurationHandle parent, PluginConfiguration parentConfiguration) {
        super(parent, parentConfiguration);
        configuration = parentConfiguration;
    }

    /**
     * Loads or reloads the configuration from the file system into memory.
     */
    @Override
    public void load() {

    }

    /**
     * Checks if the underlying configuration implementation matches the specified class.
     *
     * @param clazz The class to check against.
     * @return True if the implementation is an instance of the specified class.
     */
    @Override
    public <T> boolean isSpecifiedConfiguration(Class<T> clazz) {
        return clazz.equals(PluginConfiguration.class);
    }

    /**
     * Returns the underlying configuration implementation cast to the expected type.
     * <p>
     * Use this when you need access to platform-specific methods not covered by this handle.
     *
     * @return The underlying configuration object.
     */
    @SuppressWarnings("unchecked")
    @Override
    public <T> T toSpecifiedConfiguration() {
        return (T) configuration;
    }

    /**
     * Reloads the configuration.
     * Usually an alias for {@link #load()}.
     */
    @Override
    public void reload() {
        load();
    }

    /**
     * Saves the current configuration data from memory to the file system.
     */
    @Override
    public void save() {
        PluginConsumer.process(
                () -> YamlConfiguration.save(configuration, file),
                e -> getLogger().error(e, "Failed to save file: " + file.getName() + " due to: " + e.getMessage())
        );
    }

    /**
     * Retrieves a String from the specified path.
     *
     * @param path The path to retrieve the string from.
     * @return The string value, or null/empty depending on implementation if not found.
     */
    @Override
    public String getString(String path) {
        return configuration.getString(path);
    }

    /**
     * Retrieves a String from the specified path with a fallback value.
     *
     * @param path The path to retrieve the string from.
     * @param def  The default value to return if the path does not exist.
     * @return The string value found, or the default value.
     */
    @Override
    public String getString(String path, String def) {
        return configuration.getString(path, def);
    }

    /**
     * Retrieves an integer from the specified path.
     *
     * @param path The path to retrieve the integer from.
     * @return The int value. Returns 0 if not found.
     */
    @Override
    public int getInt(String path) {
        return configuration.getInt(path);
    }

    /**
     * Retrieves an integer from the specified path with a fallback value.
     *
     * @param path The path to retrieve the integer from.
     * @param def  The default value to return if the path does not exist.
     * @return The int value found, or the default value.
     */
    @Override
    public int getInt(String path, int def) {
        return configuration.getInt(path, def);
    }

    /**
     * Retrieves along from the specified path.
     *
     * @param path The path to retrieve the long from.
     * @return The long value. Returns 0 if not found.
     */
    @Override
    public long getLong(String path) {
        return configuration.getLong(path);
    }

    /**
     * Retrieves along from the specified path with a fallback value.
     *
     * @param path The path to retrieve the long from.
     * @param def  The default value to return if the path does not exist.
     * @return The long value found, or the default value.
     */
    @Override
    public long getLong(String path, long def) {
        return configuration.getLong(path, def);
    }

    /**
     * Retrieves the boolean status of a path.
     *
     * @param path The path to check.
     * @return The boolean value. Implementations should return false if the path is missing.
     */
    @Override
    public boolean getStatus(String path) {
        return configuration.getBoolean(path);
    }

    /**
     * Retrieves the boolean status of a path with a default value.
     *
     * @param path The path to check.
     * @param def  The default value to return if the path does not exist.
     * @return The boolean value found, or the default value.
     */
    @Override
    public boolean getStatus(String path, boolean def) {
        return configuration.getBoolean(path, def);
    }

    @Override
    public Object get(String path) {
        return configuration.get(path);
    }

    @Override
    public Object get(String path, Object def) {
        return configuration.getOf(path, def);
    }

    /**
     * Retrieves a raw List from the specified path.
     *
     * @param path The path of the list.
     * @return The list found, or null/empty depending on implementation.
     */
    @Override
    public List<?> getList(String path) {
        return configuration.getList(path);
    }

    /**
     * Retrieves a raw List from the specified path with a fallback.
     *
     * @param path The path of the list.
     * @param def  The default list to return if the path does not exist.
     * @return The list found, or the default list.
     */
    @Override
    public List<?> getList(String path, List<?> def) {
        return configuration.getList(path, def);
    }

    /**
     * Retrieves a list of Strings from the specified path.
     *
     * @param path The path of the list.
     * @return A list of strings. Returns an empty list if not found.
     */
    @Override
    public List<String> getStringList(String path) {
        return configuration.getStringList(path);
    }

    /**
     * Retrieves a list of Integers from the specified path.
     *
     * @param path The path of the list.
     * @return A list of integers.
     */
    @Override
    public List<Integer> getIntList(String path) {
        return configuration.getIntList(path);
    }

    @Override
    public List<Long> getLongList(String path) {
        return configuration.getLongList(path);
    }

    @Override
    public List<Boolean> getBooleanList(String path) {
        return configuration.getBooleanList(path);
    }

    @Override
    public List<Byte> getByteList(String path) {
        return configuration.getByteList(path);
    }

    @Override
    public List<Character> getCharList(String path) {
        return configuration.getCharList(path);
    }

    @Override
    public List<Float> getFloatList(String path) {
        return configuration.getFloatList(path);
    }

    /**
     * Checks if the specified path exists in the configuration.
     *
     * @param path The path to check.
     * @return True if the path exists, false otherwise.
     */
    @Override
    public boolean contains(String path) {
        return configuration.contains(path);
    }

    /**
     * Sets a value at the specified path.
     * <p>
     * Note: This usually requires calling {@link #save()} afterward to persist changes to disk.
     *
     * @param path  The path where the value will be set.
     * @param value The value to store.
     */
    @Override
    public void set(String path, Object value) {
        configuration.set(path, value);
    }

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
    @Override
    public Set<String> getKeys(String path, boolean getKeys) {
        return configuration.getKeys(path, getKeys);
    }

    /**
     * Retrieves a set of all keys in the configuration.
     *
     * @param deep If true, returns all keys recursively (e.g., "section.subsection.key").
     *             If false, returns only top-level keys.
     * @return A set of keys.
     */
    @Override
    public Set<String> getKeys(boolean deep) {
        return configuration.getKeys(deep);
    }

    /**
     * Retrieves a subsection of the configuration.
     *
     * @param path The path of the section.
     * @return A ConfigurationHandle representing that section.
     */
    @Override
    public ConfigurationHandle getSection(String path) {
        return new DefaultSectionConfigurationHandle(this, configuration.getSection(path));
    }

    /**
     * Creates a new empty section at the specified path.
     *
     * @param path The path to create the section at.
     * @return The newly created ConfigurationHandle section.
     */
    @Override
    public ConfigurationHandle createSection(String path) {
        return getSection(path);
    }
}
