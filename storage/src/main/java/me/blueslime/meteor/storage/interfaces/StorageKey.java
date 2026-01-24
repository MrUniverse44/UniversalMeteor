package me.blueslime.meteor.storage.interfaces;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Field or parameter annotated with this identifier will be saved/loaded using a specified identifier in the database.
 **/
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER, ElementType.FIELD})
public @interface StorageKey {

    String key() default "";

    boolean optional() default false;

    String defaultValue() default "";

}
