package cc.jfire.boot.forward.path;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface Path
{
    String value();

    /**
     * 允许的 HTTP 方法，默认为 ALL（匹配所有方法）
     */
    HttpMethod[] method() default HttpMethod.ALL;
}
