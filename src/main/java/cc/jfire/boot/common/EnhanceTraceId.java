package cc.jfire.boot.common;

import cc.jfire.baseutil.bytecode.support.AnnotationContext;
import cc.jfire.jfire.core.aop.notated.support.MatchTargetMethod;

import java.lang.reflect.Method;

public class EnhanceTraceId implements MatchTargetMethod
{
    @Override
    public boolean match(Method method)
    {
        return AnnotationContext.isAnnotationPresent(TraceId.class, method);
    }
}
