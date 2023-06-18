package com.jfirer.jfirer.boot.common;

import com.jfirer.baseutil.bytecode.support.AnnotationContext;
import com.jfirer.jfire.core.aop.notated.support.MatchTargetMethod;

import java.lang.reflect.Method;

public class EnhanceTraceId implements MatchTargetMethod
{
    @Override
    public boolean match(Method method)
    {
        return AnnotationContext.isAnnotationPresent(TraceId.class, method);
    }
}
