package me.blueslime.meteor.storage.mapper;

import me.blueslime.meteor.platforms.api.service.PlatformService;
import me.blueslime.meteor.storage.interfaces.*;
import me.blueslime.meteor.utilities.consumer.PluginConsumer;
import org.bson.Document;

import java.lang.reflect.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Function;

@SuppressWarnings("unchecked")
public class ObjectMapper implements PlatformService {

    private final Map<Class<?>, EntityStructure> structureCache = new ConcurrentHashMap<>();
    private final Map<Class<?>, Function<Collection<?>, ?>> collections = new ConcurrentHashMap<>();
    private final Map<Class<?>, Function<String, ?>> converters = new ConcurrentHashMap<>();

    private final ThreadLocal<Set<Object>> serializationStack = ThreadLocal.withInitial(() -> Collections.newSetFromMap(new IdentityHashMap<>()));

    public ObjectMapper() {
        registerDefaults();
    }

    private void registerDefaults() {
        converters.put(String.class, s -> s);
        converters.put(Integer.class, Integer::parseInt);
        converters.put(int.class, Integer::parseInt);
        converters.put(Double.class, Double::parseDouble);
        converters.put(double.class, Double::parseDouble);
        converters.put(Float.class, Float::parseFloat);
        converters.put(float.class, Float::parseFloat);
        converters.put(Boolean.class, Boolean::parseBoolean);
        converters.put(boolean.class, Boolean::parseBoolean);
        converters.put(Long.class, Long::parseLong);
        converters.put(long.class, Long::parseLong);
        converters.put(Byte.class, Byte::parseByte);
        converters.put(byte.class, Byte::parseByte);
        converters.put(Short.class, Short::parseShort);
        converters.put(short.class, Short::parseShort);
        converters.put(Character.class, s -> s.charAt(0));
        converters.put(char.class, s -> s.charAt(0));
        converters.put(BigInteger.class, BigInteger::new);
        converters.put(BigDecimal.class, BigDecimal::new);

        collections.put(ArrayList.class, ArrayList::new);
        collections.put(LinkedList.class, LinkedList::new);
        collections.put(HashSet.class, HashSet::new);
        collections.put(Set.class, HashSet::new);
        collections.put(CopyOnWriteArrayList.class, CopyOnWriteArrayList::new);
        collections.put(CopyOnWriteArraySet.class, CopyOnWriteArraySet::new);
        collections.put(TreeSet.class, TreeSet::new);
        collections.put(List.class, ArrayList::new);
        collections.put(Collection.class, ArrayList::new);
        collections.put(Map.class, s -> new HashMap<>());
        collections.put(HashMap.class, s -> new HashMap<>());
        collections.put(ConcurrentHashMap.class, concurrentHashMap -> new ConcurrentHashMap<>());
    }

    public Document toDocument(Object obj) {
        if (obj == null) return new Document();

        if (serializationStack.get().contains(obj)) {
            return new Document("_cyclic_ref", obj.getClass().getSimpleName());
        }
        serializationStack.get().add(obj);

        try {
            EntityStructure structure = getStructure(obj.getClass());
            Document doc = new Document();

            if (structure.isRecord) {
                for (RecordComponentData rc : structure.recordComponents) {
                    try {
                        Method accessor = structure.recordAccessors.get(rc.key);
                        Object value = accessor.invoke(obj);

                        if (value == null && rc.hasDefaultValue) {
                            value = convertValue(rc.type, rc.defaultValue);
                        }

                        doc.put(rc.storageKey, transformToStorable(value));
                    } catch (Exception e) {
                        logError("Failed to access record component: " + rc.key, e);
                    }
                }
            } else {
                for (FieldData fieldData : structure.fields) {
                    try {
                        Object value = fieldData.field.get(obj);

                        if (value == null && fieldData.hasDefaultValue) {
                            value = convertValue(fieldData.field.getType(), fieldData.defaultValue);
                        }

                        doc.put(fieldData.storageKey, transformToStorable(value));

                    } catch (IllegalAccessException ignored) {}
                }
            }
            return doc;
        } finally {
            serializationStack.get().remove(obj);
        }
    }

    public <T> T fromDocument(Class<T> clazz, Document doc) {
        if (doc == null) return null;
        EntityStructure structure = getStructure(clazz);

        try {
            if (structure.isRecord) {
                return createRecord(clazz, structure, doc);
            }

            if (structure.annotatedConstructor != null) {
                Object[] args = resolveConstructorArgs(structure.constructorParams, doc);
                return (T) structure.annotatedConstructor.newInstance(args);
            }

            T instance = clazz.getDeclaredConstructor().newInstance();

            for (FieldData fieldData : structure.fields) {
                if (doc.containsKey(fieldData.storageKey)) {
                    Object raw = doc.get(fieldData.storageKey);
                    Object adapted = adaptValue(fieldData.field.getType(), fieldData.genericType, raw);

                    if (adapted != null) {
                        fieldData.field.set(instance, adapted);
                    }
                } else if (fieldData.hasDefaultValue) {
                    Object def = convertValue(fieldData.field.getType(), fieldData.defaultValue);
                    fieldData.field.set(instance, def);
                }
            }
            return instance;

        } catch (Exception e) {
            logError("Failed to instantiate " + clazz.getSimpleName(), e);
            return null;
        }
    }

    private <T> T createRecord(Class<T> clazz, EntityStructure structure, Document doc) throws Exception {
        Object[] args = new Object[structure.recordComponents.size()];

        for (int i = 0; i < structure.recordComponents.size(); i++) {
            RecordComponentData rc = structure.recordComponents.get(i);
            Object raw = doc.get(rc.storageKey);

            if (raw == null && rc.hasDefaultValue) {
                args[i] = convertValue(rc.type, rc.defaultValue);
            } else {
                args[i] = adaptValue(rc.type, rc.genericType, raw);
            }

            if (args[i] == null && isPrimitive(rc.type)) {
                args[i] = getPrimitiveDefault(rc.type);
            }
        }

        Constructor<T> canonical = clazz.getDeclaredConstructor(structure.recordTypes);
        canonical.setAccessible(true);
        return canonical.newInstance(args);
    }

    private Object transformToStorable(Object value) {
        if (value == null) return null;

        if (isPrimitiveOrWrapper(value.getClass()) || value instanceof String) return value;

        if (value.getClass().isEnum()) return value.toString();

        if (value instanceof Collection) {
            List<Object> list = new ArrayList<>();
            for (Object o : (Collection<?>) value) list.add(transformToStorable(o));
            return list;
        }

        if (value instanceof Map) {
            Document doc = new Document();
            ((Map<?, ?>) value).forEach((k, v) -> doc.put(String.valueOf(k), transformToStorable(v)));
            return doc;
        }

        if (value.getClass().isArray()) {
            int len = Array.getLength(value);
            List<Object> list = new ArrayList<>(len);
            for (int i = 0; i < len; i++) list.add(transformToStorable(Array.get(value, i)));
            return list;
        }

        return toDocument(value);
    }

    private Object[] resolveConstructorArgs(List<ParamData> params, Document doc) {
        Object[] args = new Object[params.size()];
        for (int i = 0; i < params.size(); i++) {
            ParamData param = params.get(i);
            Object raw = doc.get(param.storageKey);

            if (raw == null && param.hasDefaultValue) {
                args[i] = convertValue(param.type, param.defaultValue);
            } else {
                args[i] = adaptValue(param.type, param.genericType, raw);
            }

            if (args[i] == null && isPrimitive(param.type)) {
                args[i] = getPrimitiveDefault(param.type);
            }
        }
        return args;
    }

    private Object adaptValue(Class<?> expected, Type genericType, Object raw) {
        if (raw == null) return null;

        if (expected.isEnum() && raw instanceof String) {
            return convertValue(expected, (String) raw);
        }

        if (Number.class.isAssignableFrom(expected) || expected.isPrimitive()) {
            if (raw instanceof Number num) {
                if (expected == int.class || expected == Integer.class) return num.intValue();
                if (expected == long.class || expected == Long.class) return num.longValue();
                if (expected == double.class || expected == Double.class) return num.doubleValue();
                if (expected == float.class || expected == Float.class) return num.floatValue();
                if (expected == byte.class || expected == Byte.class) return num.byteValue();
                if (expected == short.class || expected == Short.class) return num.shortValue();
                if (expected == boolean.class || expected == Boolean.class) return num.intValue() == 1;
            }
            if (raw instanceof Boolean b && (expected == boolean.class || expected == Boolean.class)) {
                return b;
            }
            if (raw instanceof String str) {
                try { return convertValue(expected, str); } catch (Exception ignored) {}
            }
        }

        if (isComplexObject(expected) && raw instanceof Document) {
            return fromDocument(expected, (Document) raw);
        }

        if (Collection.class.isAssignableFrom(expected) && raw instanceof Collection<?> list) {
            Collection<Object> out = (Collection<Object>) collections.getOrDefault(expected, ArrayList::new).apply(list);

            Class<?> elementType = Object.class;
            if (genericType instanceof ParameterizedType pType) {
                Type t = pType.getActualTypeArguments()[0];
                if (t instanceof Class<?>) elementType = (Class<?>) t;
            }

            for (Object element : list) {
                out.add(adaptValue(elementType, elementType, element));
            }
            return out;
        }

        if (Map.class.isAssignableFrom(expected) && raw instanceof Map<?, ?> map) {
            Map<Object, Object> out = new HashMap<>();
            Class<?> valType = Object.class;
            if (genericType instanceof ParameterizedType pType && pType.getActualTypeArguments().length > 1) {
                if (pType.getActualTypeArguments()[1] instanceof Class<?>)
                    valType = (Class<?>) pType.getActualTypeArguments()[1];
            }
            for (Map.Entry<?, ?> e : map.entrySet()) {
                out.put(e.getKey(), adaptValue(valType, valType, e.getValue()));
            }
            return out;
        }

        return raw;
    }

    @SuppressWarnings("rawtypes")
    public Object convertValue(Class<?> clazz, String value) {
        Function<String, ?> converter = converters.get(clazz);
        if (converter != null) return converter.apply(value);
        if (clazz.isEnum()) {
            return PluginConsumer.ofUnchecked(
                () -> Enum.valueOf((Class<Enum>) clazz, value),
                e -> logError("Enum error: " + clazz.getSimpleName(), e),
                () -> null
            );
        }
        return value;
    }

    public String toJson(Object value) {
        if (value instanceof StorageObject) return toDocument(value).toJson();
        if (value instanceof Collection) return new Document("list", value).toJson();
        return new Document("v", value).toJson();
    }

    public <T> T fromJson(String json, Class<T> clazz) {
        try {
            Document doc = Document.parse(json);
            if (doc.containsKey("v") && !isComplexObject(clazz)) {
                return (T) adaptValue(clazz, clazz, doc.get("v"));
            }
            if (doc.containsKey("list") && Collection.class.isAssignableFrom(clazz)) {
                return (T) adaptValue(clazz, clazz, doc.get("list"));
            }
            return fromDocument(clazz, doc);
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isComplexObject(Class<?> clazz) {
        return StorageObject.class.isAssignableFrom(clazz) ||
               (!clazz.getName().startsWith("java.") && !clazz.isPrimitive() && !clazz.isEnum());
    }

    private boolean isPrimitive(Class<?> type) {
        return type.isPrimitive();
    }

    private boolean isPrimitiveOrWrapper(Class<?> type) {
        return type.isPrimitive() || Number.class.isAssignableFrom(type) || type == Boolean.class || type == Character.class;
    }

    private Object getPrimitiveDefault(Class<?> type) {
        if (type == int.class) return 0;
        if (type == boolean.class) return false;
        if (type == double.class) return 0.0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0.0f;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == char.class) return '\u0000';
        return null;
    }

    public void registerDefaultValueConverter(Class<?> key, Function<String, ?> converter) {
        converters.put(key, converter);
    }

    public void registerCollectionConverter(Class<? extends Collection<?>> key, Function<Collection<?>, ?> converter) {
        collections.put(key, converter);
    }

    protected void logError(String message, Exception e) {
        if (e == null) getLogger().error(message);
        else getLogger().error(e, message);
    }

    private EntityStructure getStructure(Class<?> clazz) {
        return structureCache.computeIfAbsent(clazz, EntityStructure::new);
    }

    private static class EntityStructure {
        final boolean isRecord;
        final List<FieldData> fields = new ArrayList<>();

        Constructor<?> annotatedConstructor = null;
        final List<ParamData> constructorParams = new ArrayList<>();

        final List<RecordComponentData> recordComponents = new ArrayList<>();
        final Class<?>[] recordTypes;
        final Map<String, Method> recordAccessors = new HashMap<>();

        public EntityStructure(Class<?> clazz) {
            this.isRecord = clazz.isRecord();

            if (isRecord) {
                scanRecord(clazz);
                this.recordTypes = recordComponents.stream().map(rc -> rc.type).toArray(Class[]::new);
            } else {
                this.recordTypes = new Class[0];
                scanFields(clazz);
                scanConstructors(clazz);
            }
        }

        private void scanRecord(Class<?> clazz) {
            for (RecordComponent rc : clazz.getRecordComponents()) {
                RecordComponentData data = new RecordComponentData(rc);
                recordComponents.add(data);
                try {
                    Method accessor = clazz.getDeclaredMethod(rc.getName());
                    accessor.setAccessible(true);
                    recordAccessors.put(data.key, accessor);
                } catch (NoSuchMethodException ignored) {}
            }
        }

        private void scanFields(Class<?> clazz) {
            if (clazz == null || clazz == Object.class) return;
            for (Field field : clazz.getDeclaredFields()) {
                if (field.isAnnotationPresent(StorageIgnore.class)) continue;
                field.setAccessible(true);
                fields.add(new FieldData(field));
            }
            scanFields(clazz.getSuperclass());
        }

        private void scanConstructors(Class<?> clazz) {
            for (Constructor<?> c : clazz.getConstructors()) {
                if (c.isAnnotationPresent(StorageConstructor.class)) {
                    annotatedConstructor = c;
                    for (Parameter p : c.getParameters()) {
                        constructorParams.add(new ParamData(p));
                    }
                    return;
                }
            }
        }
    }

    private static class FieldData {
        final Field field;
        final String storageKey;
        final boolean hasDefaultValue;
        final String defaultValue;
        final Type genericType;

        FieldData(Field field) {
            this.field = field;
            this.genericType = field.getGenericType();
            String key = field.getName();
            String def = "";
            boolean hasDef = false;
            if (field.isAnnotationPresent(StorageKey.class)) {
                StorageKey ann = field.getAnnotation(StorageKey.class);
                if (!ann.key().isEmpty()) key = ann.key();
                if (!ann.defaultValue().isEmpty()) {
                    def = ann.defaultValue();
                    hasDef = true;
                }
            }
            this.storageKey = key;
            this.defaultValue = def;
            this.hasDefaultValue = hasDef;
        }
    }

    private static class ParamData {
        final String storageKey;
        final Class<?> type;
        final Type genericType;
        final boolean hasDefaultValue;
        final String defaultValue;

        ParamData(Parameter p) {
            this.type = p.getType();
            this.genericType = p.getParameterizedType();
            String k = p.getName();
            String def = "";
            boolean hasDef = false;
            if (p.isAnnotationPresent(StorageKey.class)) {
                StorageKey ann = p.getAnnotation(StorageKey.class);
                if (!ann.key().isEmpty()) k = ann.key();
                if (!ann.defaultValue().isEmpty()) {
                    def = ann.defaultValue();
                    hasDef = true;
                }
            }
            this.storageKey = k;
            this.defaultValue = def;
            this.hasDefaultValue = hasDef;
        }
    }

    private static class RecordComponentData {
        final String key;
        final String storageKey;
        final Class<?> type;
        final Type genericType;
        final boolean hasDefaultValue;
        final String defaultValue;

        RecordComponentData(RecordComponent rc) {
            this.type = rc.getType();
            this.genericType = rc.getGenericType();
            this.key = rc.getName();

            String sKey = rc.getName();
            String def = "";
            boolean hasDef = false;

            if (rc.isAnnotationPresent(StorageKey.class)) {
                StorageKey ann = rc.getAnnotation(StorageKey.class);
                if (!ann.key().isEmpty()) sKey = ann.key();
                if (!ann.defaultValue().isEmpty()) {
                    def = ann.defaultValue();
                    hasDef = true;
                }
            }
            this.storageKey = sKey;
            this.defaultValue = def;
            this.hasDefaultValue = hasDef;
        }
    }
}