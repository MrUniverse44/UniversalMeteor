package me.blueslime.meteor.platforms.api.utils.files;

import me.blueslime.meteor.utilities.tools.Tools;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A light-weight hierarchical configuration container designed to be multi-platform.
 */
@SuppressWarnings("SameParameterValue")
public final class PluginConfiguration {
    public static final PluginConfiguration EMPTY = new PluginConfiguration();
    private static final char SEPARATOR = '.';

    final Map<String, Object> self;
    private final Map<String, List<String>> comments;
    private final PluginConfiguration defaults;

    private final Map<String, PluginConfiguration> sectionCache = new ConcurrentHashMap<>();

    /**
     * Create an empty configuration node with no defaults.
     */
    public PluginConfiguration() {
        this(null);
    }

    /**
     * Create an empty configuration node with the provided defaults.
     *
     * @param defaults the defaults to consult when a value is not present locally
     */
    public PluginConfiguration(PluginConfiguration defaults) {
        this(new LinkedHashMap<>(), defaults);
    }

    /**
     * Internal constructor that initializes this node from a Map
     * (used by loader code).
     *
     * @param map      the initial map representing keys/values/sections
     * @param defaults defaults for this node's children
     */
    PluginConfiguration(Map<?, ?> map, PluginConfiguration defaults) {
        this.self = new LinkedHashMap<>();
        this.comments = new LinkedHashMap<>();
        this.defaults = defaults;

        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Object value = entry.getValue();

            if (value instanceof Map<?, ?> subMap) {
                this.self.put(key, new PluginConfiguration(subMap, (defaults != null) ? defaults.getSection(key) : null));
            } else {
                this.self.put(key, value);
            }
        }
    }

    /**
     * Store a list of comment lines associated with the provided path.
     * If comments are null or empty, any existing comments for that path are removed.
     *
     * @param path     dot-separated path
     * @param comments list of comment lines (without leading '#')
     */
    public void setComments(String path, List<String> comments) {
        if (comments == null || comments.isEmpty()) {
            this.comments.remove(path);
        } else {
            this.comments.put(path, new ArrayList<>(comments));
        }
    }

    /**
     * Store comment lines associated with the provided path (varargs overload).
     *
     * @param path     dot-separated path
     * @param comments comment lines
     */
    public void setComments(String path, String... comments) {
        setComments(path, Arrays.asList(comments));
    }

    /**
     * Retrieve comment lines associated with the provided path.
     *
     * @param path dot-separated path
     * @return list of comment lines or an empty list if none are present
     */
    public List<String> getComments(String path) {
        return comments.getOrDefault(path, Collections.emptyList());
    }

    /**
     * Internal accessor to get all stored comments for debugging or dump.
     *
     * @return internal comments map
     */
    private Map<String, List<String>> getAllComments() {
        return comments;
    }

    /**
     * Return the section object that should be used to resolve the given path.
     *
     * <p>Important behaviour:
     * - If the provided path has no separator (single segment), this method returns {@code this}.
     * - If the path has separators, this method returns (and if necessary, lazily creates)
     *   the immediate child corresponding to the first segment.</p>
     *
     * @param path path or subpath
     * @return PluginConfiguration that should handle the remainder of the path
     */
    private PluginConfiguration getSectionFor(String path) {
        int index = path.indexOf(SEPARATOR);
        if (index == -1) {
            return this;
        }

        String root = path.substring(0, index);
        PluginConfiguration cached = sectionCache.get(root);
        if (cached != null) return cached;

        Object section = self.get(root);

        if (section instanceof PluginConfiguration configSection) {
            sectionCache.put(root, configSection);
            return configSection;
        }

        PluginConfiguration newDefaults = (defaults != null) ? defaults.getSection(root) : null;
        PluginConfiguration created = new PluginConfiguration(newDefaults);

        self.put(root, created);
        sectionCache.put(root, created);
        return created;
    }
    /**
     * Return the child portion of a dot-separated path.
     * For "a.b.c" returns "b.c", for "single" returns "single".
     *
     * @param path dot-separated path
     * @return child path (remaining path after the first segment)
     */
    private String getChild(String path) {
        int index = path.indexOf(SEPARATOR);
        return (index == -1) ? path : path.substring(index + 1);
    }

    /**
     * Retrieve the exact section at the provided path without creating intermediate nodes.
     * If the path does not exist locally, the defaults are consulted (if present).
     *
     * @param path dot-separated path
     * @return the PluginConfiguration located at the path, or an empty PluginConfiguration if not found locally and no defaults
     */
    public PluginConfiguration getSection(String path) {
        if (path == null || path.isEmpty()) return this;
        String[] parts = path.split("\\.", 2);
        String first = parts[0];

        Object val = self.get(first);
        if (val instanceof PluginConfiguration cfg) {
            if (parts.length == 1) return cfg;
            return cfg.getSection(parts[1]);
        } else {
            return (defaults != null) ? defaults.getSection(path) : new PluginConfiguration();
        }
    }

    /**
     * Return the immediate keys in this node.
     *
     * @return collection of keys at this level (non-deep)
     */
    public Set<String> getKeys(boolean deep) {
        Set<String> keys = new LinkedHashSet<>();

        if (defaults != null) {
            keys.addAll(defaults.getKeys(deep));
        }

        LinkedHashMap<String, Object> keyCopy =  new LinkedHashMap<>(self);

        for (String k : keyCopy.keySet()) {
            Object v = keyCopy.get(k);
            if (deep && v instanceof PluginConfiguration pc) {
                for (String sub : pc.getKeys(true)) {
                    keys.add(k + "." + sub);
                }
            } else {
                keys.add(k);
            }
        }
        return keys;
    }

    /**
     * Return the keys contained in the section located at the specified path.
     * This method will not create any missing sections (non-mutating).
     * If the path does not exist locally, defaults will be consulted (also non-mutating).
     * <br>
     * Examples:
     *  - getKeys("", false)  -> same as getKeys() on this node
     *  - getKeys("a", false) -> immediate children of section 'a' (if exists)
     *  - getKeys("a", true)  -> deep children of section 'a' (e.g. "b", "b.c", ...)
     *
     * @param path    dot-separated path to the section (empty or null means this node)
     * @param deep whether to return keys recursively (deep)
     * @return set of keys (empty set if the path does not exist)
     */
    public Set<String> getKeys(String path, boolean deep) {
        if (path == null || path.isEmpty()) {
            return getKeys(deep);
        }
        PluginConfiguration section = getSection(path);
        return section.getKeys(deep);
    }

    @SuppressWarnings("unchecked")
    public <T> T getOf(String path, T def) {
        PluginConfiguration section = getSectionFor(path);
        String key = getChild(path);

        if (section == this) {
            Object val = self.get(key);
            if (val == null) {
                return def;
            }
            return (T) val;
        } else {
            return section.getOf(key, def);
        }
    }

    public Object get(String path) {
        return getOf(path, getDefault(path));
    }

    public Object getDefault(String path) {
        return (defaults == null) ? null : defaults.get(path);
    }

    public void set(String path, Object value) {
        PluginConfiguration section = getSectionFor(path);
        String key = getChild(path);

        if (section == this) {
            Object previous = self.get(key);
            if (value == null) {
                self.remove(key);
                sectionCache.remove(key);
            } else {
                if (value instanceof Map<?, ?> mapValue) {
                    PluginConfiguration created = new PluginConfiguration(mapValue, (defaults != null) ? defaults.getSection(key) : null);
                    self.put(key, created);
                    sectionCache.put(key, created);
                } else {
                    self.put(key, value);
                    if (previous instanceof PluginConfiguration) {
                        sectionCache.remove(key);
                    }
                }
            }
        } else {
            section.set(key, value);
        }
    }

    public boolean contains(String path) {
        if (path == null || path.isEmpty()) return false;
        if (containsIn(this, path.split("\\."), 0)) return true;
        return defaults != null && defaults.contains(path);
    }

    private static boolean containsIn(PluginConfiguration cfg, String[] parts, int idx) {
        PluginConfiguration current = cfg;
        for (int i = idx; i < parts.length; i++) {
            String part = parts[i];
            Object val = current.self.get(part);
            if (val == null) return false;
            if (i == parts.length - 1) return true;

            if (val instanceof PluginConfiguration pc) {
                current = pc;
            } else {
                return false;
            }
        }
        return false;
    }

    /**
     * Get String value at the path, returning def if not present.
     *
     * @param path dot-separated path
     * @param def  fallback
     * @return string value or def
     */
    public String getString(String path, String def) {
        Object val = getOf(path, def);
        return (val != null) ? String.valueOf(val) : def;
    }

    /**
     * Get int value at the path, returning def if not convertible or not present.
     *
     * @param path dot-separated path
     * @param def  fallback
     * @return int
     */
    public int getInt(String path, int def) {
        Object val = get(path);
        return Tools.toInteger(String.valueOf(val), def);
    }

    /**
     * Get double value at the path, returning def if not convertible or not present.
     *
     * @param path dot-separated path
     * @param def  fallback
     * @return double
     */
    public double getDouble(String path, double def) {
        Object val = get(path);
        return Tools.toDouble(String.valueOf(val), (float)def);
    }

    /**
     * Get long value at the path, returning def if not convertible or not present.
     *
     * @param path dot-separated path
     * @param def  fallback
     * @return long
     */
    public long getLong(String path, long def) {
        Object val = get(path);
        return Tools.toLong(String.valueOf(val), (int)def);
    }

    /**
     * Get boolean value at the path, returning def if not convertible or not present.
     *
     * @param path dot-separated path
     * @param def  fallback
     * @return boolean
     */
    public boolean getBoolean(String path, boolean def) {
        Object val = get(path);
        if (val instanceof Boolean b) return b;
        return Boolean.parseBoolean(String.valueOf(val));
    }

    /**
     * Get a list of strings at the path. If the stored value is a list, each element is converted to String.
     *
     * @param path dot-separated path
     * @return list of strings or an empty list if none
     */
    public List<String> getStringList(String path) {
        Object val = get(path);
        if (val instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object o : list) result.add(String.valueOf(o));
            return result;
        }
        return new ArrayList<>();
    }

    /**
     * Get a list of Integer
     *
     * @param path dot-separated path
     * @return list of strings or an empty list if none
     */
    public List<Integer> getIntList(String path) {
        Object val = get(path);
        if (val instanceof List<?> list) {
            List<Integer> result = new ArrayList<>();
            for (Object o : list) result.add(Tools.toInteger(String.valueOf(o), 0));
            return result;
        }
        return new ArrayList<>();
    }

    /**
     * Get a list of Long
     *
     * @param path dot-separated path
     * @return list of strings or an empty list if none
     */
    public List<Long> getLongList(String path) {
        Object val = get(path);
        if (val instanceof List<?> list) {
            List<Long> result = new ArrayList<>();
            for (Object o : list) result.add(Tools.toLong(String.valueOf(o), 0));
            return result;
        }
        return new ArrayList<>();
    }

    /**
     * Get a list of Double
     *
     * @param path dot-separated path
     * @return list of strings or an empty list if none
     */
    public List<Double> getDoubleList(String path) {
        Object val = get(path);
        if (val instanceof List<?> list) {
            List<Double> result = new ArrayList<>();
            for (Object o : list) result.add(Tools.toDouble(String.valueOf(o), 0));
            return result;
        }
        return new ArrayList<>();
    }

    /**
     * Get a list of Boolean
     *
     * @param path dot-separated path
     * @return list of strings or an empty list if none
     */
    public List<Boolean> getBooleanList(String path) {
        Object val = get(path);
        if (val instanceof List<?> list) {
            List<Boolean> result = new ArrayList<>();
            for (Object o : list) result.add(Boolean.parseBoolean(String.valueOf(o)));
            return result;
        }
        return new ArrayList<>();
    }

    /**
     * Get a list of Float
     *
     * @param path dot-separated path
     * @return list of strings or an empty list if none
     */
    public List<Float> getFloatList(String path) {
        Object val = get(path);
        if (val instanceof List<?> list) {
            List<Float> result = new ArrayList<>();
            for (Object o : list) result.add(Tools.toFloat(String.valueOf(o), 0));
            return result;
        }
        return new ArrayList<>();
    }

    /**
     * Get a list of Byte
     *
     * @param path dot-separated path
     * @return list of strings or an empty list if none
     */
    public List<Byte> getByteList(String path) {
        Object val = get(path);
        if (val instanceof List<?> list) {
            List<Byte> result = new ArrayList<>();
            for (Object o : list) result.add(Tools.toByte(String.valueOf(o), (byte)0));
            return result;
        }
        return new ArrayList<>();
    }

    /**
     * Gets a list of characters from the given path.
     * Each element is converted to a Character when possible.
     *
     * @param path dot-separated path
     * @return list of characters or an empty list if none
     */
    public List<Character> getCharList(String path) {
        Object val = get(path);
        if (val instanceof List<?> list) {
            List<Character> result = new ArrayList<>();
            for (Object o : list) {
                if (o instanceof Character c) {
                    result.add(c);
                } else if (o instanceof String s && !s.isEmpty()) {
                    result.add(s.charAt(0));
                } else if (o instanceof Number n) {
                    result.add((char) n.intValue());
                }
            }
            return result;
        }
        return new ArrayList<>();
    }

    /**
     * Get a list of objects at the path. If the stored value is a list, each element is converted to Object.
     *
     * @param path dot-separated path
     * @return list of strings or an empty list if none
     */
    public List<?> getList(String path) {
        Object val = get(path);
        if (val instanceof List<?> list) {
            return new ArrayList<Object>(list);
        }
        return new ArrayList<>();
    }

    /**
     * Get a list of objects at the path. If the stored value is a list, each element is converted to Object.
     *
     * @param path dot-separated path
     * @param defList default list if the result is null
     * @return list of strings or an empty list if none
     */
    public List<?> getList(String path, List<?> defList) {
        Object val = get(path);
        if (val == null) {
            return defList;
        }
        if (val instanceof List<?> list) {
            return new ArrayList<Object>(list);
        }
        return defList;
    }

    public String getString(String path) { return getString(path, ""); }
    public int getInt(String path) { return getInt(path, 0); }
    public double getDouble(String path) { return getDouble(path, 0); }
    public long getLong(String path) { return getLong(path, 0); }
    public boolean getBoolean(String path) { return getBoolean(path, false); }

    /**
     * Convert this configuration node into a plain Map<String,Object> recursively.
     * Sections are converted to nested maps. Useful before serialization.
     *
     * @return plain map representation
     */
    public Map<String, Object> toMap() {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : self.entrySet()) {
            Object v = e.getValue();
            if (v instanceof PluginConfiguration pc) {
                out.put(e.getKey(), pc.toMap());
            } else {
                out.put(e.getKey(), v);
            }
        }
        return out;
    }
}