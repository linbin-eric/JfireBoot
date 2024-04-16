package com.jfirer.jfirer.boot.forward.openapi;

import com.jfirer.baseutil.StringUtil;
import com.jfirer.baseutil.bytecode.support.AnnotationContext;
import com.jfirer.baseutil.bytecode.util.BytecodeUtil;
import com.jfirer.baseutil.reflect.ReflectUtil;
import com.jfirer.dson.Dson;
import com.jfirer.jfire.core.bean.BeanDefinition;
import com.jfirer.jfirer.boot.http.HttpRequestExtend;
import com.jfirer.jnet.common.api.Pipeline;
import lombok.Getter;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

@Getter
public class ServiceRequest
{
    private String                                     serviceId;
    private BeanDefinition                             beanDefinition;
    private Method                                     method;
    private Function<HttpRequestExtend, String>        dataExtracter;
    private BiFunction<HttpRequestExtend, String, ?>[] paramValueParse;

    public ServiceRequest(BeanDefinition beanDefinition, Method method, Function<HttpRequestExtend, String> dataExtracter)
    {
        this.beanDefinition = beanDefinition;
        this.method         = method;
        serviceId           = AnnotationContext.getAnnotation(ServiceId.class, method).value();
        this.dataExtracter  = dataExtracter;
        String[] paramNames = BytecodeUtil.parseMethodParamNames(method);
        paramValueParse = new BiFunction[paramNames.length];
        for (int i = 0; i < method.getParameterTypes().length; i++)
        {
            Class<?> parameterType = method.getParameterTypes()[i];
            switch (ReflectUtil.getClassId(parameterType))
            {
                case ReflectUtil.CLASS_INT, ReflectUtil.PRIMITIVE_INT -> paramValueParse[i] = new IntegerParse(paramNames[i]);
                case ReflectUtil.CLASS_BOOL, ReflectUtil.PRIMITIVE_BOOL ->
                {
                    paramValueParse[i] = new BooleanParse(paramNames[i]);
                }
                case ReflectUtil.CLASS_BYTE, ReflectUtil.PRIMITIVE_BYTE ->
                {
                    paramValueParse[i] = new ByteParse(paramNames[i]);
                }
                case ReflectUtil.CLASS_SHORT, ReflectUtil.PRIMITIVE_SHORT ->
                {
                    paramValueParse[i] = new ShortParse(paramNames[i]);
                }
                case ReflectUtil.CLASS_LONG, ReflectUtil.PRIMITIVE_LONG ->
                {
                    paramValueParse[i] = new LongParse(paramNames[i]);
                }
                case ReflectUtil.CLASS_CHAR, ReflectUtil.PRIMITIVE_CHAR ->
                {
                    paramValueParse[i] = new CharParse(paramNames[i]);
                }
                case ReflectUtil.CLASS_FLOAT, ReflectUtil.PRIMITIVE_FLOAT ->
                {
                    paramValueParse[i] = new FloatParse(paramNames[i]);
                }
                case ReflectUtil.CLASS_DOUBLE, ReflectUtil.PRIMITIVE_DOUBLE ->
                {
                    paramValueParse[i] = new DoubleParse(paramNames[i]);
                }
                case ReflectUtil.CLASS_STRING ->
                {
                    paramValueParse[i] = new StringParse(paramNames[i]);
                }
                case ReflectUtil.CLASS_OBJECT ->
                {
                    if (parameterType == Pipeline.class)
                    {
                        paramValueParse[i] = new PipelineParse();
                    }
                    else if (parameterType == HttpRequestExtend.class)
                    {
                        paramValueParse[i] = new HttpRequestParse();
                    }
                    else
                    {
                        Annotation[] parameterAnnotation = method.getParameterAnnotations()[i];
                        if (Arrays.stream(parameterAnnotation).anyMatch(v -> v instanceof JsonAttribute))
                        {
                            JsonAttribute annotation = (JsonAttribute) Arrays.stream(parameterAnnotation).filter(v -> v instanceof JsonAttribute).findAny().get();
                            String        name       = StringUtil.isNotBlank(annotation.value()) ? annotation.value() : paramNames[i];
                            paramValueParse[i] = new AttributeParse(method.getGenericParameterTypes()[i], name);
                        }
                        else
                        {
                            paramValueParse[i] = new ObjectParse(method.getGenericParameterTypes()[i]);
                        }
                    }
                }
            }
        }
    }

    public Object invoke(HttpRequestExtend extend) throws InvocationTargetException, IllegalAccessException
    {
        String   apply  = dataExtracter.apply(extend);
        Object[] values = new Object[paramValueParse.length];
        for (int i = 0; i < values.length; i++)
        {
            values[i] = paramValueParse[i].apply(extend, apply);
        }
        return method.invoke(beanDefinition.getBean(), values);
    }

    class IntegerParse implements BiFunction<HttpRequestExtend, String, Integer>
    {
        public IntegerParse(String paramName)
        {
            this.paramName = paramName;
        }

        private String paramName;

        @Override
        public Integer apply(HttpRequestExtend requestExtend, String s)
        {
            Map<String, Object> map = (Map<String, Object>) Dson.fromString(s);
            Object              o   = map.get(paramName);
            if (o == null)
            {
                return null;
            }
            if (o instanceof String str)
            {
                return Integer.valueOf(str);
            }
            else if (o instanceof Number num)
            {
                return num.intValue();
            }
            else
            {
                throw new IllegalArgumentException();
            }
        }
    }

    class BooleanParse implements BiFunction<HttpRequestExtend, String, Boolean>
    {
        public BooleanParse(String paramName)
        {
            this.paramName = paramName;
        }

        private String paramName;

        @Override
        public Boolean apply(HttpRequestExtend requestExtend, String s)
        {
            Map<String, Object> map = (Map<String, Object>) Dson.fromString(s);
            Object              o   = map.get(paramName);
            if (o == null)
            {
                return null;
            }
            if (o instanceof String str)
            {
                return Boolean.valueOf(str);
            }
            else if (o instanceof Boolean bool)
            {
                return bool;
            }
            else
            {
                throw new IllegalArgumentException();
            }
        }
    }

    class ByteParse implements BiFunction<HttpRequestExtend, String, Byte>
    {
        public ByteParse(String paramName)
        {
            this.paramName = paramName;
        }

        private String paramName;

        @Override
        public Byte apply(HttpRequestExtend requestExtend, String s)
        {
            Map<String, Object> map = (Map<String, Object>) Dson.fromString(s);
            Object              o   = map.get(paramName);
            if (o == null)
            {
                return null;
            }
            if (o instanceof String str)
            {
                return Byte.valueOf(str);
            }
            else if (o instanceof Number num)
            {
                return num.byteValue();
            }
            else
            {
                throw new IllegalArgumentException();
            }
        }
    }

    class ShortParse implements BiFunction<HttpRequestExtend, String, Short>
    {
        public ShortParse(String paramName)
        {
            this.paramName = paramName;
        }

        private String paramName;

        @Override
        public Short apply(HttpRequestExtend requestExtend, String s)
        {
            Map<String, Object> map = (Map<String, Object>) Dson.fromString(s);
            Object              o   = map.get(paramName);
            if (o == null)
            {
                return null;
            }
            if (o instanceof String str)
            {
                return Short.valueOf(str);
            }
            else if (o instanceof Number num)
            {
                return num.shortValue();
            }
            else
            {
                throw new IllegalArgumentException();
            }
        }
    }

    class LongParse implements BiFunction<HttpRequestExtend, String, Long>
    {
        public LongParse(String paramName)
        {
            this.paramName = paramName;
        }

        private String paramName;

        @Override
        public Long apply(HttpRequestExtend requestExtend, String s)
        {
            Map<String, Object> map = (Map<String, Object>) Dson.fromString(s);
            Object              o   = map.get(paramName);
            if (o == null)
            {
                return null;
            }
            if (o instanceof String str)
            {
                return Long.valueOf(str);
            }
            else if (o instanceof Number num)
            {
                return num.longValue();
            }
            else
            {
                throw new IllegalArgumentException();
            }
        }
    }

    class FloatParse implements BiFunction<HttpRequestExtend, String, Float>
    {
        public FloatParse(String paramName)
        {
            this.paramName = paramName;
        }

        private String paramName;

        @Override
        public Float apply(HttpRequestExtend requestExtend, String s)
        {
            Map<String, Object> map = (Map<String, Object>) Dson.fromString(s);
            Object              o   = map.get(paramName);
            if (o == null)
            {
                return null;
            }
            if (o instanceof String str)
            {
                return Float.valueOf(str);
            }
            else if (o instanceof Number num)
            {
                return num.floatValue();
            }
            else
            {
                throw new IllegalArgumentException();
            }
        }
    }

    class DoubleParse implements BiFunction<HttpRequestExtend, String, Double>
    {
        public DoubleParse(String paramName)
        {
            this.paramName = paramName;
        }

        private String paramName;

        @Override
        public Double apply(HttpRequestExtend requestExtend, String s)
        {
            Map<String, Object> map = (Map<String, Object>) Dson.fromString(s);
            Object              o   = map.get(paramName);
            if (o == null)
            {
                return null;
            }
            if (o instanceof String str)
            {
                return Double.valueOf(str);
            }
            else if (o instanceof Number num)
            {
                return num.doubleValue();
            }
            else
            {
                throw new IllegalArgumentException();
            }
        }
    }

    class CharParse implements BiFunction<HttpRequestExtend, String, Character>
    {
        public CharParse(String paramName)
        {
            this.paramName = paramName;
        }

        private String paramName;

        @Override
        public Character apply(HttpRequestExtend requestExtend, String s)
        {
            Map<String, Object> map = (Map<String, Object>) Dson.fromString(s);
            Object              o   = map.get(paramName);
            if (o == null)
            {
                return null;
            }
            if (o instanceof String str && str.length() == 1)
            {
                return str.charAt(0);
            }
            else
            {
                throw new IllegalArgumentException();
            }
        }
    }

    class StringParse implements BiFunction<HttpRequestExtend, String, String>
    {
        public StringParse(String paramName)
        {
            this.paramName = paramName;
        }

        private String paramName;

        @Override
        public String apply(HttpRequestExtend requestExtend, String s)
        {
            Map<String, Object> map = (Map<String, Object>) Dson.fromString(s);
            Object              o   = map.get(paramName);
            if (o == null)
            {
                return null;
            }
            if (o instanceof String str)
            {
                return str;
            }
            else
            {
                throw new IllegalArgumentException();
            }
        }
    }

    class HttpRequestParse implements BiFunction<HttpRequestExtend, String, HttpRequestExtend>
    {
        @Override
        public HttpRequestExtend apply(HttpRequestExtend requestExtend, String s)
        {
            return requestExtend;
        }
    }

    class PipelineParse implements BiFunction<HttpRequestExtend, String, Pipeline>
    {
        @Override
        public Pipeline apply(HttpRequestExtend requestExtend, String s)
        {
            return requestExtend.getPipeline();
        }
    }

    class ObjectParse implements BiFunction<HttpRequestExtend, String, Object>
    {
        private Type type;

        public ObjectParse(Type type)
        {
            this.type = type;
        }

        @Override
        public Object apply(HttpRequestExtend requestExtend, String s)
        {
            return Dson.fromString(type, s);
        }
    }

    class AttributeParse implements BiFunction<HttpRequestExtend, String, Object>
    {
        private Type   type;
        private String attribute;

        public AttributeParse(Type type, String attribute)
        {
            this.type      = type;
            this.attribute = attribute;
        }

        @Override
        public Object apply(HttpRequestExtend requestExtend, String s)
        {
            return Dson.fromStringByAttribute(attribute, type, s);
        }
    }
}
