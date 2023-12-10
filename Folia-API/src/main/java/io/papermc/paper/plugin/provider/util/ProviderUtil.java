package io.papermc.paper.plugin.provider.util;

import com.destroystokyo.paper.util.SneakyThrow;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An <b>internal</b> utility type that holds logic for loading a provider-like type from a classloaders.
 * Provides, at least in the context of this utility, define themselves as implementations of a specific parent
 * interface/type, e.g. {@link org.bukkit.plugin.java.JavaPlugin} and implement a no-args constructor.
 */
@ApiStatus.Internal
public class ProviderUtil {

    /**
     * Loads the class found at the provided fully qualified class name from the passed classloader, creates a new
     * instance of it using the no-args constructor, that should exist as per this method contract, and casts it to the
     * provided parent type.
     *
     * @param clazz     the fully qualified name of the class to load
     * @param classType the parent type that the created object found at the {@code clazz} name should be cast to
     * @param loader    the loader from which the class should be loaded
     * @param <T>       the generic type of the parent class the created object will be cast to
     * @return the object instantiated from the class found at the provided FQN, cast to the parent type
     */
    @NotNull
    public static <T> T loadClass(@NotNull String clazz, @NotNull Class<T> classType, @NotNull ClassLoader loader) {
        return loadClass(clazz, classType, loader, null);
    }

    /**
     * Loads the class found at the provided fully qualified class name from the passed classloader, creates a new
     * instance of it using the no-args constructor, that should exist as per this method contract, and casts it to the
     * provided parent type.
     *
     * @param clazz     the fully qualified name of the class to load
     * @param classType the parent type that the created object found at the {@code clazz} name should be cast to
     * @param loader    the loader from which the class should be loaded
     * @param onError   a runnable that is executed before any unknown exception is raised through a sneaky throw.
     * @param <T>       the generic type of the parent class the created object will be cast to
     * @return the object instantiated from the class found at the provided fully qualified class name, cast to the
     * parent type
     */
    @NotNull
    public static <T> T loadClass(@NotNull String clazz, @NotNull Class<T> classType, @NotNull ClassLoader loader, @Nullable Runnable onError) {
        try {
            T clazzInstance;

            try {
                Class<?> jarClass = Class.forName(clazz, true, loader);

                Class<? extends T> pluginClass;
                try {
                    pluginClass = jarClass.asSubclass(classType);
                } catch (ClassCastException ex) {
                    throw new ClassCastException("class '%s' does not extend '%s'".formatted(clazz, classType));
                }

                clazzInstance = pluginClass.getDeclaredConstructor().newInstance();
            } catch (IllegalAccessException exception) {
                throw new RuntimeException("No public constructor");
            } catch (InstantiationException exception) {
                throw new RuntimeException("Abnormal class instantiation", exception);
            }

            return clazzInstance;
        } catch (Throwable e) {
            if (onError != null) {
                onError.run();
            }
            SneakyThrow.sneaky(e);
        }

        throw new AssertionError(); // Shouldn't happen
    }

}
