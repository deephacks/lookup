package deephacks.lookup;

import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;

@Target(TYPE)
public @interface ServiceProvider {
  Class<?> value() default Object.class;
}
