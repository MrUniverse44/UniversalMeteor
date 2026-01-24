package me.blueslime.meteor.modules.api.api.module;

import me.blueslime.meteor.modules.api.api.module.priority.ModulePriority;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PACKAGE, ElementType.TYPE})
public @interface Module {

    /**
     * Unique name of the module.
     * Must be unique across the entire system.
     */
    String id();

    /**
     * Name of this module.
     */
    String name() default "Unknown Module";

    /**
     * Module version.
     */
    String version() default "0.0";

    /**
     * Module author(s).
     */
    String[] authors() default "";

    /**
     * Module description.
     */
    String description() default "";

    /**
     * Array of modules that this module depends on.
     * Modules will be loaded to respect dependencies.
     */
    String[] dependencies() default "";

    String folderName() default "";

    /**
     * It will load modules from highest to lowest
     * @return priority
     */
    int priority() default ModulePriority.NORMAL;

    /**
     * Target platform(s) for this module.
     * Use "PAPER" for Paper/Spigot/Bukkit servers.
     * Use "VELOCITY" for Velocity proxy servers.
     * Use "ALL" or leave empty for cross-platform modules (default).
     * Note: Since modules are platform-specific by design, this is mainly for documentation.
     * @return target platform
     */
    String platform() default "ALL";

    String[] bukkitDependencies() default "";
}
