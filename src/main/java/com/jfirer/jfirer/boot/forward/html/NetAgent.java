package com.jfirer.jfirer.boot.forward.html;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface NetAgent
{

    Location[] LOCATIONS();

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface Location
    {
        String url();

        String proxyPass() default "";

        String alias() default "";
    }
}
