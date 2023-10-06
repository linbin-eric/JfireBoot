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

import java.lang.reflect.*;
import java.util.*;
import java.util.function.Function;

/**
 * 现在的 Http 传参以 Json 格式居多。因此 PathRequest 默认在 post 模式下传参的格式是 json。
 * 1. 如果接收方法的入参是一个非简单、非内置的类，则直接将请求通过 Json 逆序列化为方法入参对象。
 * 2. 如果接收方法的入参是一个简单类，则将请求逆序列化后放入 paramMap。然后从 paramMap 中提取数据来初始化方法入参。
 * 3. 如果接收方法的入参是 2 个及以上，则将请求逆序列化后放入 paramMap。对于入参中的复杂类，将请求体逆序列化为方法入参，对于入参中的简单类，则从 paramMap 中提取数据来初始化。
 */
@Data
public class PathRequest
{
    private String                                path;
    private Function<HttpRequestExtend, Object>[] paramValueGenerators;
    private Method                                method;
    private BeanDefinition                        beanDefinition;
    private RestfulMatch                          restfulMatch;
    private boolean                               needDeserializateJsonToParamMap = false;

    public PathRequest(Method method, BeanDefinition beanDefinition)
    {
        this.method         = method;
        this.beanDefinition = beanDefinition;
        Path annotation = AnnotationContext.getAnnotation(Path.class, method);
        path = annotation.value();
        if (path.contains("${"))
        {
            restfulMatch = new RestfulMatch(path);
        }
        String[]   paramNames     = BytecodeUtil.parseMethodParamNames(method);
        Class<?>[] parameterTypes = method.getParameterTypes();
        paramValueGenerators = new Function[paramNames.length];
        for (int i = 0; i < paramNames.length; i++)
        {
            ReflectUtil.Primitive primitive = ReflectUtil.ofPrimitive(parameterTypes[i]);
            switch (primitive)
            {
                case INT -> paramValueGenerators[i] = new IntegerParse();
                case BOOL -> paramValueGenerators[i] = new BooleanParse(paramNames[i]);
                case BYTE -> paramValueGenerators[i] = new ByteParse(paramNames[i]);
                case SHORT -> paramValueGenerators[i] = new ShortParse(paramNames[i]);
                case LONG -> paramValueGenerators[i] = new LongParse(paramNames[i]);
                case CHAR -> throw new IllegalArgumentException();
                case FLOAT -> paramValueGenerators[i] = new FloatParse(paramNames[i]);
                case DOUBLE -> paramValueGenerators[i] = new DoubleParse(paramNames[i]);
                case STRING -> paramValueGenerators[i] = new StringParse(paramNames[i]);
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
                        paramValueGenerators[i] = new ObjectParse(method.getGenericParameterTypes()[i]);
                    }
                }
            }
        }
    }

    public Object invoke(HttpRequestExtend requestExtend) throws InvocationTargetException, IllegalAccessException
    {
        return method.invoke(beanDefinition.getBean(), Arrays.stream(paramValueGenerators).map(gen -> gen.apply(requestExtend)).toArray());
    }

    class SimpleClassParse implements Function<HttpRequestExtend, Object>
    {
        private final String                   name;
        private final Function<Object, Object> parse;

        SimpleClassParse(String name, Function<Object, Object> parse)
        {
            this.name  = name;
            this.parse = parse;
        }

        @Override
        public Object apply(HttpRequestExtend httpRequestExtend)
        {
            Object val = httpRequestExtend.getParamMap().get(name);
            return parse.apply(val);
        }
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
        private Type                                    type;
        private Constructor                             constructor;
        private ValueAccessor[]                         valueAccessors;
        private Function<Map<String, Object>, Object>[] valueGenerators;

        public ObjectParse(Type type)
        {
            this.type = type;
            if (type instanceof Class ckass && (ckass.isInterface() == false && Collection.class.isAssignableFrom(ckass) == false))
            {
                try
                {
                    constructor = ckass.getDeclaredConstructor();
                }
                catch (NoSuchMethodException e)
                {
                    ReflectUtil.throwException(e);
                }
                List<ValueAccessor>                         list = new LinkedList<>();
                List<Function<Map<String, Object>, Object>> gen  = new LinkedList<>();
                while (ckass != Object.class)
                {
                    try
                    {
                        Arrays.stream(ckass.getDeclaredFields()).forEach(f -> list.add(new ValueAccessor(f)));
                    }
                    catch (Throwable e)
                    {
                        e.printStackTrace();
                    }
                    Arrays.stream(ckass.getDeclaredFields()).forEach(field -> {
                        switch (ReflectUtil.ofPrimitive(field.getType()))
                        {
                            case INT -> gen.add(new ObjectIntValue(field.getName()));
                            case BOOL -> gen.add(new ObjectBooleanValue(field.getName()));
                            case BYTE, SHORT, CHAR, UNKONW -> gen.add(new UnSupportValueType(field));
                            case LONG -> gen.add(new ObjectLongValue(field.getName()));
                            case FLOAT -> gen.add(new ObjectFloatValue(field.getName()));
                            case DOUBLE -> gen.add(new ObjectDoubleValue(field.getName()));
                            case STRING -> gen.add(new ObjectStringValue(field.getName()));
                            default ->
                                    throw new IllegalStateException("Unexpected value: " + ReflectUtil.ofPrimitive(field.getType()));
                        }
                    });
                    ckass = ckass.getSuperclass();
                }
                valueAccessors  = list.toArray(ValueAccessor[]::new);
                valueGenerators = gen.toArray(Function[]::new);
            }
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
                    Object instance = constructor.newInstance();
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
