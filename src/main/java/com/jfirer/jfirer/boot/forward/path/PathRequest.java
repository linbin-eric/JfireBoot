package com.jfirer.jfirer.boot.forward.path;

import com.jfirer.baseutil.bytecode.support.AnnotationContext;
import com.jfirer.baseutil.bytecode.util.BytecodeUtil;
import com.jfirer.baseutil.reflect.ReflectUtil;
import com.jfirer.baseutil.reflect.ValueAccessor;
import com.jfirer.dson.Dson;
import com.jfirer.jfire.core.bean.BeanDefinition;
import com.jfirer.jfirer.boot.http.HttpRequestExtend;
import com.jfirer.jnet.common.api.Pipeline;
import com.jfirer.jnet.extend.http.decode.HttpRequest;
import lombok.Data;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Data
public class PathRequest
{
    private String                                path;
    private Function<HttpRequestExtend, Object>[] paramValueGenerators;
    private Method                                method;
    private BeanDefinition                        beanDefinition;

    public PathRequest(Method method, BeanDefinition beanDefinition)
    {
        this.method = method;
        this.beanDefinition = beanDefinition;
        Path annotation = AnnotationContext.getAnnotation(Path.class, method);
        path = annotation.value();
        String[]   paramNames     = BytecodeUtil.parseMethodParamNames(method);
        Class<?>[] parameterTypes = method.getParameterTypes();
        paramValueGenerators = new Function[paramNames.length];
        for (int i = 0; i < paramNames.length; i++)
        {
            ReflectUtil.Primitive primitive = ReflectUtil.ofPrimitive(parameterTypes[i]);
            switch (primitive)
            {
                case INT ->
                        paramValueGenerators[i] = new IntegerParse(paramNames[i]);
                case BOOL ->
                        paramValueGenerators[i] = new BooleanParse(paramNames[i]);
                case BYTE ->
                        paramValueGenerators[i] = new ByteParse(paramNames[i]);
                case SHORT ->
                        paramValueGenerators[i] = new ShortParse(paramNames[i]);
                case LONG ->
                        paramValueGenerators[i] = new LongParse(paramNames[i]);
                case CHAR -> throw new IllegalArgumentException();
                case FLOAT ->
                        paramValueGenerators[i] = new FloatParse(paramNames[i]);
                case DOUBLE ->
                        paramValueGenerators[i] = new DoubleParse(paramNames[i]);
                case STRING ->
                        paramValueGenerators[i] = new StringParse(paramNames[i]);
                case UNKONW ->
                {
                    if (parameterTypes[i] == HttpRequestExtend.class || parameterTypes[i] == HttpRequest.class)
                    {
                        paramValueGenerators[i] = new HttpRequestParse();
                    }
                    else if (parameterTypes[i] == Pipeline.class)
                    {
                        paramValueGenerators[i] = new PipelineParse();
                    }
                    else
                    {
                        paramValueGenerators[i] = new ObjectParse(parameterTypes[i]);
                    }
                }
            }
        }
    }

    public Object invoke(HttpRequestExtend requestExtend) throws InvocationTargetException, IllegalAccessException
    {
        return method.invoke(beanDefinition.getBean(), Arrays.stream(paramValueGenerators).map(gen -> gen.apply(requestExtend)).toArray());
    }

    class PipelineParse implements Function<HttpRequestExtend, Object>
    {
        @Override
        public Object apply(HttpRequestExtend requestExtend)
        {
            return requestExtend.getPipeline();
        }
    }

    class HttpRequestParse implements Function<HttpRequestExtend, Object>
    {
        @Override
        public Object apply(HttpRequestExtend requestExtend)
        {
            return requestExtend;
        }
    }

    class IntegerParse implements Function<HttpRequestExtend, Object>
    {
        private String name;

        public IntegerParse(String name)
        {
            this.name = name;
        }

        @Override
        public Integer apply(HttpRequestExtend requestExtend)
        {
            Object value = requestExtend.getParamMap().get(name);
            if (value instanceof String str)
            {
                return Integer.parseInt(str);
            }
            else if (value instanceof Number n)
            {
                return n.intValue();
            }
            else
            {
                throw new IllegalArgumentException();
            }
        }
    }

    class BooleanParse implements Function<HttpRequestExtend, Object>
    {
        private String name;

        public BooleanParse(String name)
        {
            this.name = name;
        }

        @Override
        public Boolean apply(HttpRequestExtend requestExtend)
        {
            Object value = requestExtend.getParamMap().get(name);
            if (value instanceof String str)
            {
                return Boolean.parseBoolean(str);
            }
            else if (value instanceof Boolean b)
            {
                return b;
            }
            else
            {
                throw new IllegalArgumentException();
            }
        }
    }

    class ByteParse implements Function<HttpRequestExtend, Object>
    {
        private String name;

        public ByteParse(String name)
        {
            this.name = name;
        }

        @Override
        public Byte apply(HttpRequestExtend requestExtend)
        {
            Object value = requestExtend.getParamMap().get(name);
            if (value instanceof String str)
            {
                return Byte.parseByte(str);
            }
            else if (value instanceof Number n)
            {
                return n.byteValue();
            }
            else
            {
                throw new IllegalArgumentException();
            }
        }
    }

    class ShortParse implements Function<HttpRequestExtend, Object>
    {
        private String name;

        public ShortParse(String name)
        {
            this.name = name;
        }

        @Override
        public Short apply(HttpRequestExtend requestExtend)
        {
            Object value = requestExtend.getParamMap().get(name);
            if (value instanceof String str)
            {
                return Short.parseShort(str);
            }
            else if (value instanceof Number n)
            {
                return n.shortValue();
            }
            else
            {
                throw new IllegalArgumentException();
            }
        }
    }

    class LongParse implements Function<HttpRequestExtend, Object>
    {
        private String name;

        public LongParse(String name)
        {
            this.name = name;
        }

        @Override
        public Long apply(HttpRequestExtend requestExtend)
        {
            Object value = requestExtend.getParamMap().get(name);
            if (value instanceof String str)
            {
                return Long.parseLong(str);
            }
            else if (value instanceof Number n)
            {
                return n.longValue();
            }
            else
            {
                throw new IllegalArgumentException();
            }
        }
    }

    class FloatParse implements Function<HttpRequestExtend, Object>
    {
        private String name;

        public FloatParse(String name)
        {
            this.name = name;
        }

        @Override
        public Float apply(HttpRequestExtend requestExtend)
        {
            Object value = requestExtend.getParamMap().get(name);
            if (value instanceof String str)
            {
                return Float.parseFloat(str);
            }
            else if (value instanceof Number n)
            {
                return n.floatValue();
            }
            else
            {
                throw new IllegalArgumentException();
            }
        }
    }

    class DoubleParse implements Function<HttpRequestExtend, Object>
    {
        private String name;

        public DoubleParse(String name)
        {
            this.name = name;
        }

        @Override
        public Double apply(HttpRequestExtend requestExtend)
        {
            Object value = requestExtend.getParamMap().get(name);
            if (value instanceof String str)
            {
                return Double.parseDouble(str);
            }
            else if (value instanceof Number n)
            {
                return n.doubleValue();
            }
            else
            {
                throw new IllegalArgumentException();
            }
        }
    }

    class StringParse implements Function<HttpRequestExtend, Object>
    {
        private String name;

        public StringParse(String name)
        {
            this.name = name;
        }

        @Override
        public Object apply(HttpRequestExtend requestExtend)
        {
            Object value = requestExtend.getParamMap().get(name);
            if (value instanceof String)
            {
                return value;
            }
            else
            {
                throw new IllegalArgumentException();
            }
        }
    }

    class ObjectParse implements Function<HttpRequestExtend, Object>
    {
        private Class                                   type;
        private ValueAccessor[]                         valueAccessors;
        private Function<Map<String, Object>, Object>[] valueGenerators;

        public ObjectParse(Class type)
        {
            this.type = type;
            List<ValueAccessor>                         list = new LinkedList<>();
            List<Function<Map<String, Object>, Object>> gen  = new LinkedList<>();
            while (type != Object.class)
            {
                Arrays.stream(type.getDeclaredFields()).forEach(f -> list.add(new ValueAccessor(f)));
                Arrays.stream(type.getDeclaredFields()).forEach(field -> {
                    switch (ReflectUtil.ofPrimitive(field.getType()))
                    {
                        case INT ->
                                gen.add(new ObjectIntValue(field.getName()));
                        case BOOL ->
                                gen.add(new ObjectBooleanValue(field.getName()));
                        case BYTE, SHORT, CHAR, UNKONW ->
                                gen.add(new UnSupportValueType(field));
                        case LONG ->
                                gen.add(new ObjectLongValue(field.getName()));
                        case FLOAT ->
                                gen.add(new ObjectFloatValue(field.getName()));
                        case DOUBLE ->
                                gen.add(new ObjectDoubleValue(field.getName()));
                        case STRING ->
                                gen.add(new ObjectStringValue(field.getName()));
                        default ->
                                throw new IllegalStateException("Unexpected value: " + ReflectUtil.ofPrimitive(field.getType()));
                    }
                });
                type = type.getSuperclass();
            }
            valueAccessors = list.toArray(ValueAccessor[]::new);
            valueGenerators = gen.toArray(Function[]::new);
        }

        @Override
        public Object apply(HttpRequestExtend requestExtend)
        {
            if (requestExtend.getMethod().equalsIgnoreCase("post") && requestExtend.getContentType().startsWith("application/json"))
            {
                return Dson.fromString(type, requestExtend.getUtf8StrBody());
            }
            else
            {
                try
                {
                    Object instance = type.getDeclaredConstructor().newInstance();
                    for (int i = 0; i < valueAccessors.length; i++)
                    {
                        valueAccessors[i].setObject(instance, valueGenerators[i].apply(requestExtend.getParamMap()));
                    }
                    return instance;
                }
                catch (Throwable e)
                {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    class UnSupportValueType implements Function<Map<String, Object>, Object>
    {
        private Field field;

        public UnSupportValueType(Field field)
        {
            this.field = field;
        }

        @Override
        public Object apply(Map<String, Object> map)
        {
            throw new IllegalArgumentException("如果采用非Json方法体，不支持属性的类型。属性为:" + field.getDeclaringClass().getName() + "." + field.getName());
        }
    }

    class ObjectIntValue implements Function<Map<String, Object>, Object>
    {
        private String fieldName;

        public ObjectIntValue(String fieldName)
        {
            this.fieldName = fieldName;
        }

        @Override
        public Object apply(Map<String, Object> map)
        {
            Object value = map.get(fieldName);
            if (value instanceof Number num)
            {
                return num.intValue();
            }
            else if (value instanceof String str)
            {
                return Integer.valueOf(str);
            }
            else
            {
                throw new IllegalArgumentException();
            }
        }
    }

    class ObjectLongValue implements Function<Map<String, Object>, Object>
    {
        private String fieldName;

        public ObjectLongValue(String fieldName)
        {
            this.fieldName = fieldName;
        }

        @Override
        public Object apply(Map<String, Object> map)
        {
            Object value = map.get(fieldName);
            if (value instanceof Number num)
            {
                return num.longValue();
            }
            else if (value instanceof String str)
            {
                return Long.valueOf(str);
            }
            else
            {
                throw new IllegalArgumentException();
            }
        }
    }

    class ObjectFloatValue implements Function<Map<String, Object>, Object>
    {
        private String fieldName;

        public ObjectFloatValue(String fieldName)
        {
            this.fieldName = fieldName;
        }

        @Override
        public Object apply(Map<String, Object> map)
        {
            Object value = map.get(fieldName);
            if (value instanceof Number num)
            {
                return num.floatValue();
            }
            else if (value instanceof String str)
            {
                return Float.valueOf(str);
            }
            else
            {
                throw new IllegalArgumentException();
            }
        }
    }

    class ObjectDoubleValue implements Function<Map<String, Object>, Object>
    {
        private String fieldName;

        public ObjectDoubleValue(String fieldName)
        {
            this.fieldName = fieldName;
        }

        @Override
        public Object apply(Map<String, Object> map)
        {
            Object value = map.get(fieldName);
            if (value instanceof Number num)
            {
                return num.doubleValue();
            }
            else if (value instanceof String str)
            {
                return Double.valueOf(str);
            }
            else
            {
                throw new IllegalArgumentException();
            }
        }
    }

    class ObjectBooleanValue implements Function<Map<String, Object>, Object>
    {
        private String fieldName;

        public ObjectBooleanValue(String fieldName)
        {
            this.fieldName = fieldName;
        }

        @Override
        public Object apply(Map<String, Object> map)
        {
            Object value = map.get(fieldName);
            if (value instanceof Boolean bool)
            {
                return bool.booleanValue();
            }
            else if (value instanceof String str)
            {
                return Boolean.valueOf(str);
            }
            else
            {
                throw new IllegalArgumentException();
            }
        }
    }

    class ObjectStringValue implements Function<Map<String, Object>, Object>
    {
        private String fieldName;

        public ObjectStringValue(String fieldName)
        {
            this.fieldName = fieldName;
        }

        @Override
        public Object apply(Map<String, Object> map)
        {
            Object value = map.get(fieldName);
            if (value instanceof String str)
            {
                return str;
            }
            else
            {
                throw new IllegalArgumentException();
            }
        }
    }
}
