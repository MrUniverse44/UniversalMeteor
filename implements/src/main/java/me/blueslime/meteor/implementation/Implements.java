package me.blueslime.meteor.implementation;

import me.blueslime.meteor.implementation.abstracts.AbstractImplementer;
import me.blueslime.meteor.implementation.data.*;
import me.blueslime.meteor.implementation.error.IllegalMethodRegistration;
import me.blueslime.meteor.implementation.factory.ImplementFactory;
import me.blueslime.meteor.implementation.registration.*;
import me.blueslime.meteor.implementation.service.Service;
import me.blueslime.meteor.utilities.consumer.PluginConsumer;

import java.lang.reflect.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@SuppressWarnings({"unused"})
public class Implements extends AbstractImplementer {

    private final Map<RegistrationData, Object> CLASS_MAP = new ConcurrentHashMap<>();

    public Map<RegistrationData, Object> getRegistrationMap() {
        return CLASS_MAP;
    }

    // Public Methods for Class Operations
    public void unregisterAll(RegistrationData... all) {
        for (RegistrationData datum : all) {
            CLASS_MAP.remove(datum);
        }
    }

    public void unregisterAll(Service service) {
        if (service.isPersistent()) {
            return;
        }

        if (service instanceof RegisteredServiceInstance) {
            return;
        }

        List<RegistrationData> dataList = new CopyOnWriteArrayList<>(CLASS_MAP.keySet());
        dataList.removeIf(data -> data.getParentService() != null && data.getParentService().equals(service));
        dataList.forEach(CLASS_MAP::remove);
    }

    /**
     * Creates an instance of the given class by finding a suitable constructor and injecting dependencies.
     *
     * @param clazz The class to instantiate.
     * @param <T>   The type of the class.
     * @return A new instance of the class.
     * @throws RuntimeException If no suitable constructor is found or if not, all parameters are annotated.
     */
    public <T> T create(Class<T> clazz) {
        Constructor<?>[] constructors = clazz.getDeclaredConstructors();

        for (Constructor<?> constructor : constructors) {
            T value = processConstructor(clazz, constructor);
            if (value != null) {
                registerAll(value);
                return value;
            }
        }

        throw new RuntimeException("No suitable constructor found or not all parameters are annotated. Class: " + clazz.getSimpleName());
    }

    /**
     * Processes the given constructor by injecting dependencies into its parameters.
     *
     * @param clazz       The class to instantiate.
     * @param constructor The constructor to process.
     * @param <T>         The type of the class.
     * @return A new instance of the class if successful; otherwise, returns null.
     */
    private <T> T processConstructor(Class<T> clazz, Constructor<?> constructor) {
        int paramCount = constructor.getParameterCount();
        Object[] parameters = new Object[paramCount];
        boolean allAnnotated = true;
        Parameter[] params = constructor.getParameters();

        if (paramCount == 0) {
            try {
                constructor.setAccessible(true);
                return clazz.cast(constructor.newInstance());
            } catch (Exception ignored) {
                return null;
            }
        }

        // Process each parameter of the constructor.
        for (int i = 0; i < paramCount; i++) {
            Parameter parameter = params[i];
            if (parameter.isAnnotationPresent(Implement.class)) {
                Implement annotation = parameter.getAnnotation(Implement.class);
                try {
                    if (annotation.identifier().isEmpty()) {
                        parameters[i] = Implements.fetch(parameter.getType());
                    } else {
                        parameters[i] = Implements.fetch(parameter.getType(), annotation.identifier());
                    }
                } catch (Exception e) {
                    return null;
                }
            } else {
                allAnnotated = false;
            }
        }

        if (!allAnnotated) {
            return null;
        }

        try {
            constructor.setAccessible(true);
            return clazz.cast(constructor.newInstance(parameters));
        } catch (Exception ignored) { }
        return null;
    }

    /**
     * Registers all provided objects by invoking the registerClass method for each.
     *
     * @param classes The objects to register.
     */
    public void registerAll(Object... classes) {
        for (Object clazz : classes) {
            registerClass(clazz);
        }
    }

    /**
     * Registers an instance by processing its fields and methods, including those in its superclass hierarchy.
     *
     * @param instancedClass The instance to register.
     */
    public void registerClass(Object instancedClass) {
        Class<?> clazz = instancedClass.getClass();

        // Support for superclasses.
        while (clazz != null) {
            handleFields(clazz, instancedClass);
            handleMethods(clazz, instancedClass);
            clazz = clazz.getSuperclass();
        }
    }

    /**
     * Processes all declared fields of the given class and registers or injects dependencies as needed.
     *
     * @param clazz          The class whose fields will be processed.
     * @param instancedClass The instance containing the fields.
     */
    private void handleFields(Class<?> clazz, Object instancedClass) {
        Field[] fields = clazz.getDeclaredFields();
        Service service = instancedClass instanceof Service ? (Service) instancedClass : null;

        for (Field field : fields) {
            if (field.isAnnotationPresent(Implement.class)) {
                processImplementField(field, instancedClass);
            }
            if (field.isAnnotationPresent(Register.class)) {
                processRegisterField(field, instancedClass, service);
            }
        }
    }

    /**
     * Processes all declared methods of the given class and registers them if annotated.
     *
     * @param clazz          The class whose methods will be processed.
     * @param instancedClass The instance containing the methods.
     */
    private void handleMethods(Class<?> clazz, Object instancedClass) {
        Method[] methods = clazz.getDeclaredMethods();
        Service service = instancedClass instanceof Service ? (Service) instancedClass : null;

        for (Method method : methods) {
            if (method.isAnnotationPresent(Register.class)) {
                processRegisterMethod(method, instancedClass, service);
            }
        }
    }

    /**
     * Injects a dependency into the annotated field.
     *
     * @param field          The field to process.
     * @param instancedClass The instance containing the field.
     */
    private void processImplementField(Field field, Object instancedClass) {
        field.setAccessible(true);
        Implement implement = field.getAnnotation(Implement.class);
        Class<?> fieldClazz = field.getType();

        PluginConsumer.process(() -> {
            if (implement.identifier().isEmpty()) {
                field.set(instancedClass, Implements.fetch(fieldClazz));
            } else {
                field.set(instancedClass, Implements.fetch(fieldClazz, implement.identifier()));
            }
        }, Throwable::printStackTrace);
    }

    /**
     * Processes a field annotated with @Register and registers its value.
     *
     * @param field          The field to process.
     * @param instancedClass The instance containing the field.
     * @param service         The service instance, if available.
     */
    private void processRegisterField(Field field, Object instancedClass, Service service) {
        field.setAccessible(true);
        Register data = field.getAnnotation(Register.class);

        PluginConsumer.process(() -> {
            Object value = field.get(instancedClass);
            if (value != null) {
                if (data.identifier().isEmpty()) {
                    CLASS_MAP.put(RegistrationData.fromData(service, field.getType()), value);
                } else {
                    CLASS_MAP.put(RegistrationData.fromData(service, field.getType(), data.identifier()), value);
                }
            }
        }, Throwable::printStackTrace);
    }

    /**
     * Processes a method annotated with @Register and registers its return value.
     *
     * @param method         The method to process.
     * @param instancedClass The instance containing the method.
     * @param service         The service instance, if available.
     */
    private void processRegisterMethod(Method method, Object instancedClass, Service service) {
        if (method.getReturnType().equals(Void.TYPE)) {
            return;
        }

        method.setAccessible(true);
        Register data = method.getAnnotation(Register.class);

        PluginConsumer.process(() -> {
            try {
                Object value = method.invoke(instancedClass);
                if (value != null) {
                    if (data.identifier().isEmpty()) {
                        CLASS_MAP.put(RegistrationData.fromData(service, method.getReturnType()), value);
                    } else {
                        CLASS_MAP.put(RegistrationData.fromData(service, method.getReturnType(), data.identifier()), value);
                    }
                } else {
                    String msg = "Cannot register a null result from method: " + method.getName() + " of type " + method.getReturnType().getSimpleName();
                    throw new IllegalMethodRegistration(msg);
                }
            } catch (Exception ex) {
                throw new RuntimeException(new IllegalMethodRegistration(ex));
            }
        });
    }


    @SuppressWarnings("unchecked")
    public <T> T fetchClass(RegistrationData data) {
        Object result = CLASS_MAP.get(data);
        if (result == null) {
            if (data.getInstance() == EmptyImplementAction.class) {
                CLASS_MAP.put(RegistrationData.fromData(EmptyImplementAction.class), EmptyImplementAction.INVOKE);
                return (T) EmptyImplementAction.INVOKE;
            }
            return null;
        }
        return (T) result;
    }

    @SuppressWarnings("unchecked")
    public <T> T fetchClass(RegistrationData data, EmptyImplementAction implement) {
        Object result = CLASS_MAP.get(data);
        if (result == null) {
            if (data.getInstance() == EmptyImplementAction.class) {
                CLASS_MAP.put(RegistrationData.fromData(EmptyImplementAction.class), EmptyImplementAction.INVOKE);
            }

            if (implement == EmptyImplementAction.INVOKE) {
                result = createInstance(data.getInstance());

                CLASS_MAP.put(data, result);

                return (T) result;
            }
            return null;
        }
        return (T) result;
    }

    public <T> T fetchClass(Class<T> clazz) {
        return fetchClass(RegistrationData.fromData(clazz));
    }

    public <T> T fetchClass(Class<T> clazz, EmptyImplementAction implement) {
        return fetchClass(RegistrationData.fromData(clazz), implement);
    }

    public <T> T fetchClass(Class<T> clazz, String identifier, EmptyImplementAction implement) {
        return fetchClass(RegistrationData.fromData(clazz, identifier), implement);
    }

    @SuppressWarnings("unchecked")
    public <T> T update(RegistrationData data, T newValue) {
        return (T) CLASS_MAP.put(data, newValue);
    }

    public <T> T update(Class<T> clazz, T newValue) {
        return update(RegistrationData.fromData(clazz), newValue);
    }

    public <T> T update(Class<T> clazz, String identifier, T newValue) {
        return update(RegistrationData.fromData(clazz, identifier), newValue);
    }

    public <T> T update(Class<T> clazz, T newValue, boolean persist) {
        if (!persist) {
            return update(RegistrationData.fromData(clazz), newValue);
        }
        return update(RegistrationData.fromData(RegisteredServiceInstance.getInstance(), clazz), newValue);
    }

    public <T> T update(Class<T> clazz, String identifier, T newValue, boolean persist) {
        if (!persist) {
            return update(RegistrationData.fromData(clazz, identifier), newValue);
        }
        return update(RegistrationData.fromData(RegisteredServiceInstance.getInstance(), clazz, identifier), newValue);
    }

    public static void addRegistrationData(RegistrationData data, Object value) {
        inst().CLASS_MAP.put(data, value);
    }

    /**
     * register implement set, collection, map or list
     * @param type of the set parameter
     * @param values for this set
     */
    @SafeVarargs
    public static <T, V extends T> ImplementFactory<T, V> registerImpls(Class<T> type, V... values) {
        return new ImplementFactory<>(values);
    }

    /**
     * register implement set, collection, map or list
     * @param type of the set parameter
     * @param values for this set
     */
    @SafeVarargs
    public static <T, V extends T> ImplementFactory<T, V> registerImpls(Class<T> type, Class<V>... values) {
        return new ImplementFactory<>(createInstances(values));
    }

    @SuppressWarnings("unchecked")
    public static <T> T[] createInstances(Class<T>[] values) {
        T[] instances = (T[]) Array.newInstance(values[0], values.length);
        for (int i = 0; i < values.length; i++) {
            instances[i] = createInstance(values[i]);
        }
        return instances;
    }
}
