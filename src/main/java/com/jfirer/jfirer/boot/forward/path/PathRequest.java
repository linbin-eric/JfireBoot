package com.jfirer.jfirer.boot.forward.path;

import com.jfirer.baseutil.bytecode.support.AnnotationContext;
import com.jfirer.baseutil.bytecode.util.BytecodeUtil;
import com.jfirer.baseutil.reflect.ReflectUtil;
import com.jfirer.baseutil.reflect.valueaccessor.ValueAccessor;
import com.jfirer.dson.Dson;
import com.jfirer.jfire.core.bean.BeanDefinition;
import com.jfirer.jfireel.expression.impl.operand.ElseOperand;
import com.jfirer.jfirer.boot.http.BinaryPart;
import com.jfirer.jfirer.boot.http.HttpRequestExtend;
import com.jfirer.jnet.common.api.Pipeline;
import com.jfirer.jnet.extend.http.decode.HttpRequest;
import lombok.Data;

import java.lang.reflect.*;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
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
    private RequestType                           requestType;

    enum RequestType
    {
        JsonPost, UrlFormPost, MultiPost
    }

    public PathRequest(Method method, BeanDefinition beanDefinition)
    {
        this.method         = method;
        requestType         = method.isAnnotationPresent(UrlFormPost.class) ? RequestType.UrlFormPost : method.isAnnotationPresent(MultiPartPost.class) ? RequestType.MultiPost : RequestType.JsonPost;
        this.beanDefinition = beanDefinition;
        Path annotation = AnnotationContext.getAnnotation(Path.class, method);
        path = annotation.value();
        if (path.contains("${"))
        {
            restfulMatch = new RestfulMatch(path);
        }
        String[]   paramNames     = BytecodeUtil.parseMethodParamNames(method);
        Class<?>[] parameterTypes = method.getParameterTypes();
        needDeserializateJsonToParamMap = Arrays.stream(parameterTypes).anyMatch(type -> ReflectUtil.getClassId(type) != ReflectUtil.CLASS_OBJECT);
        paramValueGenerators            = new Function[paramNames.length];
        for (int i = 0; i < paramNames.length; i++)
        {
            int classId = ReflectUtil.getClassId(parameterTypes[i]);
            switch (classId)
            {
                case ReflectUtil.CLASS_INT,
                     ReflectUtil.PRIMITIVE_INT -> paramValueGenerators[i] = new SimpleClassParse(paramNames[i], value -> value == null ? null : value instanceof String ? Integer.valueOf((String) value) : value instanceof Number ? ((Number) value).intValue() : new IllegalArgumentException());
                case ReflectUtil.CLASS_BOOL,
                     ReflectUtil.PRIMITIVE_BOOL -> paramValueGenerators[i] = new SimpleClassParse(paramNames[i], value -> value == null ? null : value instanceof String ? Boolean.valueOf((String) value) : value instanceof Boolean ? value : new IllegalArgumentException());
                case ReflectUtil.CLASS_BYTE,
                     ReflectUtil.PRIMITIVE_BYTE -> paramValueGenerators[i] = new SimpleClassParse(paramNames[i], value -> value == null ? null : value instanceof String ? Byte.valueOf((String) value) : value instanceof Number ? ((Number) value).byteValue() : new IllegalArgumentException());
                case ReflectUtil.CLASS_SHORT,
                     ReflectUtil.PRIMITIVE_SHORT -> paramValueGenerators[i] = new SimpleClassParse(paramNames[i], value -> value == null ? null : value instanceof String ? Short.valueOf((String) value) : value instanceof Number ? ((Number) value).shortValue() : new IllegalArgumentException());
                case ReflectUtil.CLASS_LONG,
                     ReflectUtil.PRIMITIVE_LONG -> paramValueGenerators[i] = new SimpleClassParse(paramNames[i], value -> value == null ? null : value instanceof String ? Long.valueOf((String) value) : value instanceof Number ? ((Number) value).longValue() : new IllegalArgumentException());
                case ReflectUtil.CLASS_CHAR, ReflectUtil.PRIMITIVE_CHAR -> throw new IllegalArgumentException();
                case ReflectUtil.CLASS_FLOAT,
                     ReflectUtil.PRIMITIVE_FLOAT -> paramValueGenerators[i] = new SimpleClassParse(paramNames[i], value -> value == null ? null : value instanceof String ? Float.valueOf((String) value) : value instanceof Number ? ((Number) value).floatValue() : new IllegalArgumentException());
                case ReflectUtil.CLASS_DOUBLE,
                     ReflectUtil.PRIMITIVE_DOUBLE -> paramValueGenerators[i] = new SimpleClassParse(paramNames[i], value -> value == null ? null : value instanceof String ? Double.valueOf((String) value) : value instanceof Number ? ((Number) value).doubleValue() : new IllegalArgumentException());
                case ReflectUtil.CLASS_STRING -> paramValueGenerators[i] = new SimpleClassParse(paramNames[i], value -> value == null ? null : value instanceof String ? value : String.valueOf(value));
                case ReflectUtil.CLASS_OBJECT ->
                {
                    if (parameterTypes[i] == HttpRequestExtend.class || parameterTypes[i] == HttpRequest.class)
                    {
                        paramValueGenerators[i] = new HttpRequestParse();
                    }
                    else if (parameterTypes[i] == Pipeline.class)
                    {
                        paramValueGenerators[i] = new PipelineParse();
                    }
                    else if (Enum.class.isAssignableFrom(parameterTypes[i]))
                    {
                        Class parameterType = parameterTypes[i];
                        paramValueGenerators[i] = new SimpleClassParse(paramNames[i], value -> value instanceof String ? Enum.valueOf(parameterType, (String) value) : new IllegalArgumentException());
                    }
                    else if (List.class.equals(parameterTypes[i]) && method.getGenericParameterTypes()[i] instanceof ParameterizedType && ((ParameterizedType) method.getGenericParameterTypes()[i]).getActualTypeArguments()[0] == BinaryPart.class)
                    {
                        paramValueGenerators[i] = new BinaryPartParse();
                    }
                    else
                    {
                        switch (requestType)
                        {
                            case JsonPost -> paramValueGenerators[i] = new JsonParse(method.getGenericParameterTypes()[i]);
                            case UrlFormPost -> {}
                            case MultiPost -> paramValueGenerators[i] = new FormObjectParse(parameterTypes[i]);
                        }
                    }
                }
            }
        }
    }

    public Object invoke(HttpRequestExtend requestExtend) throws InvocationTargetException, IllegalAccessException
    {
        switch (requestType)
        {
            case JsonPost ->
            {
                requestExtend.parseUtf8Value();
                if (needDeserializateJsonToParamMap && requestExtend.getUtf8StrBody() != null)
                {
                    requestExtend.parseJsonBodyToParamMap();
                }
            }
            case UrlFormPost ->
            {
                throw new UnsupportedOperationException("还没有支持application/x-www-form-urlencoded形式");
            }
            case MultiPost ->
            {
                requestExtend.parseMultiPartToParamMap();
            }
        }
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

    class JsonParse implements Function<HttpRequestExtend, Object>
    {
        private Type type;

        public JsonParse(Type type)
        {
            this.type = type;
        }

        @Override
        public Object apply(HttpRequestExtend requestExtend)
        {
            return Dson.fromString(type, requestExtend.getUtf8StrBody());
        }
    }

    class BinaryPartParse implements Function<HttpRequestExtend, Object>
    {
        @Override
        public Object apply(HttpRequestExtend httpRequestExtend)
        {
            return httpRequestExtend.getParamMap().values().stream()//
                                    .filter(o -> {
                                        if (o instanceof HttpRequestExtend.BoundaryPart part)
                                        {
                                            return part.isBinary();
                                        }
                                        else
                                        {
                                            return false;
                                        }
                                    })//
                                    .map(p -> new BinaryPart().setData(((HttpRequestExtend.BoundaryPart) p).getData()).setFileName(((HttpRequestExtend.BoundaryPart) p).getFileName())//
                                                              .setFieldName(((HttpRequestExtend.BoundaryPart) p).getFieldName())//
                                    ).toList();
        }
    }

    class FormObjectParse implements Function<HttpRequestExtend, Object>
    {
        private Constructor                           constructor;
        private ValueAccessor[]                       valueAccessors;
        private Function<HttpRequestExtend, Object>[] valueGenerators;

        public FormObjectParse(Class ckass)
        {
            try
            {
                constructor = ckass.getDeclaredConstructor();
            }
            catch (NoSuchMethodException e)
            {
                ReflectUtil.throwException(e);
            }
            List<ValueAccessor>                       list = new LinkedList<>();
            List<Function<HttpRequestExtend, Object>> gen  = new LinkedList<>();
            while (ckass != Object.class)
            {
                try
                {
                    Arrays.stream(ckass.getDeclaredFields()).forEach(f -> list.add(ValueAccessor.standard(f)));
                }
                catch (Throwable e)
                {
                    e.printStackTrace();
                }
                Arrays.stream(ckass.getDeclaredFields()).forEach(field -> {
                    switch (ReflectUtil.getClassId(field.getType()))
                    {
                        case ReflectUtil.CLASS_INT,
                             ReflectUtil.PRIMITIVE_INT -> gen.add(new SimpleClassParse(field.getName(), value -> value instanceof String ? Integer.valueOf((String) value) : value instanceof Number ? ((Number) value).intValue() : new IllegalArgumentException()));
                        case ReflectUtil.CLASS_BOOL,
                             ReflectUtil.PRIMITIVE_BOOL -> gen.add(new SimpleClassParse(field.getName(), value -> value instanceof String ? Boolean.valueOf((String) value) : value instanceof Boolean ? value : new IllegalArgumentException()));
                        case ReflectUtil.CLASS_BYTE,
                             ReflectUtil.PRIMITIVE_BYTE -> gen.add(new SimpleClassParse(field.getName(), value -> value instanceof String ? Byte.valueOf((String) value) : value instanceof Number ? ((Number) value).byteValue() : new IllegalArgumentException()));
                        case ReflectUtil.CLASS_SHORT,
                             ReflectUtil.PRIMITIVE_SHORT -> gen.add(new SimpleClassParse(field.getName(), value -> value instanceof String ? Short.valueOf((String) value) : value instanceof Number ? ((Number) value).shortValue() : new IllegalArgumentException()));
                        case ReflectUtil.CLASS_LONG,
                             ReflectUtil.PRIMITIVE_LONG -> gen.add(new SimpleClassParse(field.getName(), value -> value instanceof String ? Long.valueOf((String) value) : value instanceof Number ? ((Number) value).longValue() : new IllegalArgumentException()));
                        case ReflectUtil.CLASS_FLOAT,
                             ReflectUtil.PRIMITIVE_FLOAT -> gen.add(new SimpleClassParse(field.getName(), value -> value instanceof String ? Float.valueOf((String) value) : value instanceof Number ? ((Number) value).floatValue() : new IllegalArgumentException()));
                        case ReflectUtil.CLASS_DOUBLE,
                             ReflectUtil.PRIMITIVE_DOUBLE -> gen.add(new SimpleClassParse(field.getName(), value -> value instanceof String ? Double.valueOf((String) value) : value instanceof Number ? ((Number) value).doubleValue() : new IllegalArgumentException()));
                        case ReflectUtil.CLASS_STRING -> gen.add(new SimpleClassParse(field.getName(), value -> value instanceof String ? value : new IllegalArgumentException()));
                        case ReflectUtil.CLASS_CHAR, ReflectUtil.PRIMITIVE_CHAR, ReflectUtil.CLASS_OBJECT -> gen.add(new UnSupportValueType(field));
                        default -> throw new IllegalStateException("Unexpected value: " + ReflectUtil.getClassId(field.getType()));
                    }
                });
                ckass = ckass.getSuperclass();
            }
            valueAccessors  = list.toArray(ValueAccessor[]::new);
            valueGenerators = gen.toArray(Function[]::new);
        }

        @Override
        public Object apply(HttpRequestExtend requestExtend)
        {
            try
            {
                Object instance = constructor.newInstance();
                for (int i = 0; i < valueAccessors.length; i++)
                {
                    valueAccessors[i].setObject(instance, valueGenerators[i].apply(requestExtend));
                }
                return instance;
            }
            catch (Throwable e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    class UnSupportValueType implements Function<HttpRequestExtend, Object>
    {
        private Field field;

        public UnSupportValueType(Field field)
        {
            this.field = field;
        }

        @Override
        public Object apply(HttpRequestExtend extend)
        {
            throw new IllegalArgumentException("如果采用非Json方法体，不支持属性的类型。属性为:" + field.getDeclaringClass().getName() + "." + field.getName());
        }
    }
}
