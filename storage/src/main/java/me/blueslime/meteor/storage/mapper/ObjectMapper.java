package me.blueslime.meteor.storage.mapper;
import me.blueslime.meteor.storage.interfaces.*;
import me.blueslime.meteor.utilities.consumer.PluginConsumer;
import org.bson.Document;
import org.jetbrains.annotations.NotNull;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
@SuppressWarnings("unchecked")
public class ObjectMapper {

    private final Map<Class<?>, PluginConsumer.PluginExecutableConsumer<Map<Object, Object>>> mapCreator = new ConcurrentHashMap<>();
    private final Map<Class<?>, Function<Collection<?>, ?>> collections = new ConcurrentHashMap<>();
    private final Map<Class<?>, Function<String, ?>> converters = new ConcurrentHashMap<>();

    private final Logger logger;

    public ObjectMapper(@NotNull Logger logger) {
        this.logger = logger;
        converters.put(String.class, s -> s);
        converters.put(Integer.class, Integer::parseInt);
        converters.put(int.class, Integer::parseInt);
        converters.put(Boolean.class, Boolean::parseBoolean);
        converters.put(boolean.class, Boolean::parseBoolean);
        converters.put(Double.class, Double::parseDouble);
        converters.put(double.class, Double::parseDouble);
        converters.put(Float.class, Float::parseFloat);
        converters.put(float.class, Float::parseFloat);
        converters.put(Long.class, Long::parseLong);
        converters.put(long.class, Long::parseLong);
        converters.put(Byte.class, Byte::parseByte);
        converters.put(byte.class, Byte::parseByte);
        converters.put(Short.class, Short::parseShort);
        converters.put(short.class, Short::parseShort);
        converters.put(Character.class, s -> s.charAt(0));
        converters.put(char.class, s -> s.charAt(0));
        converters.put(List.class, s -> new ArrayList<>());
        converters.put(ArrayList.class, s -> new ArrayList<>());
        converters.put(Set.class, s -> new HashSet<>());
        converters.put(HashSet.class, s -> new HashSet<>());
        converters.put(LinkedList.class, s -> new LinkedList<>());
        converters.put(Vector.class, s -> new Vector<>());
        converters.put(ConcurrentHashMap.class, s -> new
                ConcurrentHashMap<>());
        converters.put(CopyOnWriteArrayList.class, s -> new
                CopyOnWriteArrayList<>());
        converters.put(Map.class, s -> new HashMap<>());
        converters.put(HashMap.class, s -> new HashMap<>());
        converters.put(BigInteger.class, BigInteger::new);
        converters.put(BigDecimal.class, BigDecimal::new);
        collections.put(ArrayList.class, ArrayList::new);
        collections.put(LinkedList.class, LinkedList::new);
        collections.put(Set.class, HashSet::new);
        collections.put(HashSet.class, HashSet::new);
        collections.put(CopyOnWriteArrayList.class,
                CopyOnWriteArrayList::new);
        collections.put(TreeSet.class, TreeSet::new);
        mapCreator.put(HashMap.class, HashMap::new);
        mapCreator.put(Map.class, HashMap::new);
        mapCreator.put(ConcurrentHashMap.class, ConcurrentHashMap::new);
    }

    private static List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> c = clazz; c != null && c != Object.class; c =
                c.getSuperclass()) {
            Field[] declared = c.getDeclaredFields();
            fields.addAll(Arrays.asList(declared));
        }
        return fields;
    }

    public Map<String, Object> toMap(StorageObject obj) {
        Map<String, Object> map = new HashMap<>();
        for (Field field : getAllFields(obj.getClass())) {
            if (field.isAnnotationPresent(StorageIgnore.class)) continue;
            field.setAccessible(true);
            try {
                Object value = field.get(obj);
                String key = field.getName();
                if (field.isAnnotationPresent(StorageKey.class)) {
                    StorageKey keyAnnotation =
                            field.getAnnotation(StorageKey.class);
                    if (!keyAnnotation.key().isEmpty()) key =
                            keyAnnotation.key();
                    if (value == null && !
                            keyAnnotation.defaultValue().isEmpty()) {
                        value = convertValue(field.getType(),
                                keyAnnotation.defaultValue());
                    }
                }
                value = transformToStorable(value, field.getType());
                map.put(key, value);
            } catch (Exception e) {
                logError("Can't convert field " + field.getName() + " to map", e);
            }
        }
        return map;
    }

    public <T extends StorageObject> T fromMap(Class<T> clazz, Map<String, Object> map, String identifier) {
        try {
            for (Constructor<?> constructor : clazz.getConstructors()) {
                if
                (constructor.isAnnotationPresent(StorageConstructor.class)) {
                    Object[] args = resolveConstructorArgs((Constructor<T>)
                            constructor, map, identifier);
                    return (T) constructor.newInstance(args);
                }
            }
        } catch (Exception e) {
            logError("Can't convert field " + clazz.getName() + " to map", e);
        }
        return null;
    }

    private Object transformToStorable(Object value, Class<?> type) {
        if (value == null) return null;
        
        if (type.isEnum()) {
            return value.toString();
        }
        
        if (type.isArray()) {
            int length = Array.getLength(value);
            List<Object> list = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                Object element = Array.get(value, i);
                list.add(transformToStorable(element, element != null ?
                        element.getClass() : Object.class));
            }
            return list;
        }
        
        if (value instanceof Collection<?> coll) {
            List<Object> list = new ArrayList<>(coll.size());
            for (Object element : coll) {
                list.add(transformToStorable(element, element != null ?
                        element.getClass() : Object.class));
            }
            return list;
        }
        
        if (value instanceof Map<?, ?> mv) {
            Map<Object, Object> newMap = new HashMap<>(mv.size());
            for (Map.Entry<?, ?> entry : mv.entrySet()) {
                Object k = transformToStorable(entry.getKey(),
                        entry.getKey() != null ? entry.getKey().getClass() : Object.class);
                Object v = transformToStorable(entry.getValue(),
                        entry.getValue() != null ? entry.getValue().getClass() : Object.class);
                newMap.put(k, v);
            }
            return newMap;
        }
        
        if (value instanceof StorageObject) {
            return toMap((StorageObject) value);
        }
        
        return value;
    }
    private <T> Object[] resolveConstructorArgs(Constructor<T> constructor, Map<String, Object> map, String identifier) {
        Parameter[] params = constructor.getParameters();
        Object[] values = new Object[params.length];
        Type[] genericTypes = constructor.getGenericParameterTypes();
        for (int i = 0; i < params.length; i++) {
            Parameter param = params[i];
            String key = param.getName();
            if (param.isAnnotationPresent(StorageKey.class)) {
                StorageKey ann = param.getAnnotation(StorageKey.class);
                if (!ann.key().isEmpty()) key = ann.key();
            }
            Object raw = param.isAnnotationPresent(StorageIdentifier.class)
                    ? identifier
                    : map.get(key);
            Class<?> expectedType = param.getType();
            Object adapted = adaptValue(expectedType, genericTypes[i], raw,
                    identifier);
            if (adapted != null && !expectedType.isInstance(adapted)) {
                try {
                    adapted = convertValue(expectedType, adapted.toString());
                } catch (Exception ex) {
                    logError("Failed fall-back convert for parameter: " +
                            key, ex);
                }
            }
            values[i] = adapted;
        }
        return values;
    }

    @SuppressWarnings("rawtypes")
    private Object adaptValue(Class<?> expected, Type genericType, Object
            raw, String identifier) {
        if (raw == null) return null;
        if (expected.isEnum() && raw instanceof String str) {
            return PluginConsumer.ofUnchecked(
                    () -> Enum.valueOf((Class<Enum>) expected, str),
                    e -> logError("Can't adapt enum data", e),
                    () -> null
            );
        }
        if ((expected == Float.class || expected == float.class) && raw instanceof Double d) {
            return d.floatValue();
        }
        if (expected.isArray() && raw instanceof Collection<?> collection) {
            Class<?> componentType = expected.getComponentType();
            Object array = Array.newInstance(componentType,
                    collection.size());
            int j = 0;
            for (Object element : collection) {
                Array.set(array, j++, adaptValue(componentType,
                        componentType, element, identifier));
            }
            return array;
        }
        if (Collection.class.isAssignableFrom(expected) && raw instanceof Collection<?> list) {
            Collection<Object> out = Set.class.isAssignableFrom(expected) ?
                    new HashSet<>() : new ArrayList<>();
            Class<?> elementType = Object.class;
            if (genericType instanceof ParameterizedType pType) {
                Type t = pType.getActualTypeArguments()[0];
                if (t instanceof Class<?> c) elementType = c;
            }
            for (Object element : list) {
                out.add(adaptValue(elementType, elementType, element,
                        identifier));
            }
            return out;
        }
        if (Map.class.isAssignableFrom(expected) && raw instanceof Map<?, ?> map) {
            Map<Object, Object> output = new HashMap<>();
            Class<?> keyType = Object.class, valType = Object.class;
            if (genericType instanceof ParameterizedType pType) {
                if (pType.getActualTypeArguments()[0] instanceof Class<?> k)
                    keyType = k;
                if (pType.getActualTypeArguments()[1] instanceof Class<?> v)
                    valType = v;
            }
            for (Map.Entry<?, ?> e : map.entrySet()) {
                Object key = adaptValue(keyType, keyType, e.getKey(),
                        identifier);
                Object val = adaptValue(valType, valType, e.getValue(),
                        identifier);
                output.put(key, val);
            }
            return output;
        }
        if (raw instanceof Map && isComplexObject(expected)) {
            return fromMap((Class<? extends StorageObject>) expected, (Map<String, Object>) raw, identifier);
        }
        return raw;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public Object convertValue(Class<?> clazz, String value) {
        Function<String, ?> converter = converters.get(clazz);
        if (converter != null) {
            return converter.apply(value);
        }
        if (clazz.isEnum()) {
            return PluginConsumer.ofUnchecked(
                    () -> Enum.valueOf((Class<Enum>) clazz, value),
                    e -> logError("Can't find enum value for class: " +
                            clazz.getSimpleName() + " value: " + value, e),
                    () -> null
            );
        }
        return value;
    }

    public Object convertCollection(Class<? extends Collection<?>> clazz, Collection<?> value) {
        Function<Collection<?>, ?> converter = collections.get(clazz);
        if (converter != null) {
            return converter.apply(value);
        }
        return null;
    }

    public void registerDefaultValueConverter(Class<?> key, Function<String, ?> converter) {
        converters.put(key, converter);
    }

    public void registerCollectionConverter(Class<? extends Collection<?>> key, Function<Collection<?>, ?> converter) {
        collections.put(key, converter);
    }

    public void unregisterDefaultValueConverter(Class<?> clazz) {
        converters.remove(clazz);
    }

    public void unregisterCollectionConverter(Class<? extends Collection<?>> clazz) {
        collections.remove(clazz);
    }

    protected void logError(String message, Exception e) {
        if (e == null) {
            logger.log(Level.SEVERE, message);
            return;
        }
        logger.log(Level.SEVERE, message, e);
    }

    public Map<Object, Object> createMap(Class<?> clazz) {
        return mapCreator.getOrDefault(clazz, HashMap::new).accept();
    }

    public boolean isComplexObject(Class<?> clazz) {
        return !clazz.isPrimitive()
                && !clazz.getName().startsWith("java.lang")
                && (getAllFields(clazz).stream().anyMatch(f ->
                f.isAnnotationPresent(StorageKey.class))
                || Arrays.stream(clazz.getDeclaredConstructors()).anyMatch(c
                -> c.isAnnotationPresent(StorageConstructor.class)));
    }

    /**
     * Serialize a JSON structure "compatible" to storage in TEXT/JSON at SQL.
     * Based on {@link org.bson.Document} to prevent more dependencies.
     */
    @SuppressWarnings("rawtypes")
    public String toJsonCompatible(Object value) {
        if (value == null) return null;
        try {
            if (value instanceof StorageObject so) {
                Map<String, Object> m = toMap(so);
                return new Document(m).toJson();
            }
            if (value instanceof Map) {
                return new Document((Map) value).toJson();
            }
            if (value instanceof Collection) {
                Document d = new Document("__arr", value);
                return d.toJson();
            }
            
            if (value instanceof String) return (String) value;
            
            if (value.getClass().isArray()) {
                int len = Array.getLength(value);
                List<Object> tmp = new ArrayList<>(len);
                for (int i = 0; i < len; i++) tmp.add(Array.get(value, i));
                Document d = new Document("__arr", tmp);
                return d.toJson();
            }
            
            return new Document("v", value).toJson();
        } catch (Exception e) {
            logError("Failed to toJsonCompatible", e);
            return value.toString();
        }
    }
    /**
     * Deserialize JSON previously created por toJsonCompatible.
     * Return Map/List/primitives from the original String if this is not
     a structured JSON.
     */
    public Object fromJsonCompatible(String json) {
        if (json == null) return null;
        try {
            Document doc = Document.parse(json);
            if (doc.containsKey("__arr")) {
                return doc.get("__arr");
            }
            if (doc.containsKey("v")) {
                return doc.get("v");
            }
            
            return new HashMap<>(doc);
        } catch (Exception e) {
            
            return null;
        }
    }
}
