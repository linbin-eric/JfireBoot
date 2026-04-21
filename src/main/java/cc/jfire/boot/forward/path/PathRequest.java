package cc.jfire.boot.forward.path;

import cc.jfire.baseutil.STR;
import cc.jfire.baseutil.StringUtil;
import cc.jfire.baseutil.bytecode.support.AnnotationContext;
import cc.jfire.baseutil.bytecode.util.BytecodeUtil;
import cc.jfire.baseutil.reflect.ReflectUtil;
import cc.jfire.baseutil.reflect.valueaccessor.ValueAccessor;
import cc.jfire.baseutil.uniqueid.WinterId;
import cc.jfire.boot.forward.openapi.JsonAttribute;
import cc.jfire.boot.http.FilePart;
import cc.jfire.boot.http.HttpRequestExtend;
import cc.jfire.dson.Dson;
import cc.jfire.jnet.common.api.Pipeline;
import lombok.Data;

import java.lang.annotation.Annotation;
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
    private             String                                path;
    private             HttpMethod[]                          httpMethods;
    private             boolean                               allowAll;
    private             Function<HttpRequestExtend, Object>[] paramValueGenerators;
    private             Method                                method;
    private             Object                                host;
    private             RestfulMatch                          restfulMatch;
    private             boolean                               hasSimpleTypeParam = false;
    private             boolean                               ws;
    public static final String                                WS_KEY             = "ws-connection-" + WinterId.instance().generate();

    public PathRequest(Method method, Object host)
    {
        this.method      = method;
        this.host        = host;
        ws               = method.isAnnotationPresent(Ws.class);
        if (ws)
        {
            path          = method.getAnnotation(Ws.class).value();
            this.allowAll = true;
            if (Arrays.stream(method.getParameterTypes()).noneMatch(t -> t == WsConnection.class))
            {
                throw new IllegalArgumentException(STR.format("使用 WS 注解的方法，一定要有 WsConnection 参数。检查方法{}.{}", method.getDeclaringClass().getName(), method.getName()));
            }
        }
        else
        {
            Path annotation = AnnotationContext.getAnnotation(Path.class, method);
            httpMethods = annotation.method();
            allowAll = Arrays.stream(httpMethods).anyMatch(m -> m == HttpMethod.ALL);
            path = annotation.value();
            if (path.contains("${"))
            {
                restfulMatch = new RestfulMatch(path);
            }
        }
        String[]   paramNames     = BytecodeUtil.parseMethodParamNames(method);
        Class<?>[] parameterTypes = method.getParameterTypes();
        hasSimpleTypeParam   = Arrays.stream(parameterTypes).anyMatch(type -> ReflectUtil.getClassId(type) != ReflectUtil.CLASS_OBJECT);
        paramValueGenerators = new Function[paramNames.length];
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
                case ReflectUtil.CLASS_BIGDECIMAL -> paramValueGenerators[i] = new SimpleClassParse(paramNames[i], value -> value == null ? null : value instanceof String ? new java.math.BigDecimal((String) value) : value instanceof Number ? new java.math.BigDecimal(value.toString()) : new IllegalArgumentException());
                case ReflectUtil.CLASS_ENUM ->
                {
                    Class parameterType = parameterTypes[i];
                    paramValueGenerators[i] = new SimpleClassParse(paramNames[i], value -> value == null ? null : value instanceof String ? Enum.valueOf(parameterType, (String) value) : value.getClass() == parameterType ? value : new IllegalArgumentException());
                }
                case ReflectUtil.CLASS_OBJECT ->
                {
                    if (parameterTypes[i] == HttpRequestExtend.class)
                    {
                        paramValueGenerators[i] = new HttpRequestParse();
                    }
                    else if (parameterTypes[i] == Pipeline.class)
                    {
                        paramValueGenerators[i] = new PipelineParse();
                    }
                    else if (parameterTypes[i] == WsConnection.class)
                    {
                        paramValueGenerators[i] = new WsConnectionParse();
                    }
                    else if (List.class.equals(parameterTypes[i]) && method.getGenericParameterTypes()[i] instanceof ParameterizedType && ((ParameterizedType) method.getGenericParameterTypes()[i]).getActualTypeArguments()[0] == FilePart.class)
                    {
                        paramValueGenerators[i] = new FilePartParse();
                    }
                    else
                    {
                        Annotation[] parameterAnnotation = method.getParameterAnnotations()[i];
                        if (Arrays.stream(parameterAnnotation).anyMatch(v -> v instanceof JsonAttribute))
                        {
                            JsonAttribute jsonAttribute = (JsonAttribute) Arrays.stream(parameterAnnotation).filter(v -> v instanceof JsonAttribute).findAny().get();
                            String        name          = StringUtil.isNotBlank(jsonAttribute.value()) ? jsonAttribute.value() : paramNames[i];
                            paramValueGenerators[i] = new AttributeParse(method.getGenericParameterTypes()[i], name);
                        }
                        else
                        {
                            paramValueGenerators[i] = new UnifiedObjectParse(parameterTypes[i], method.getGenericParameterTypes()[i]);
                        }
                    }
                }
            }
        }
    }

    /**
     * 判断是否匹配指定的 HTTP 方法
     *
     * @param requestMethod 请求的 HTTP 方法字符串
     * @return 如果匹配返回 true，否则返回 false
     */
    public boolean matchesMethod(String requestMethod)
    {
        if (allowAll)
        {
            return true;
        }
        else
        {
            for (HttpMethod httpMethod : httpMethods)
            {
                if (httpMethod.matches(requestMethod))
                {
                    return true;
                }
            }
        }
        return false;
    }

    public Object invoke(HttpRequestExtend requestExtend) throws InvocationTargetException, IllegalAccessException
    {
        requestExtend.ensureParamMapReady(hasSimpleTypeParam);
        return method.invoke(host, Arrays.stream(paramValueGenerators).map(gen -> gen.apply(requestExtend)).toArray());
    }

    /**
     *
     * @return
     */
    public String getRouteKey()
    {
        return path;
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

    class WsConnectionParse implements Function<HttpRequestExtend, Object>
    {
        @Override
        public Object apply(HttpRequestExtend httpRequestExtend)
        {
            WsConnection wsConnection = new WsConnection().setPipeline(httpRequestExtend.getPipeline());
            httpRequestExtend.getPipeline().putPersistenceStore(WS_KEY, wsConnection);
            return wsConnection;
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

    class AttributeParse implements Function<HttpRequestExtend, Object>
    {
        private Type   type;
        private String attribute;

        public AttributeParse(Type type, String attribute)
        {
            this.type      = type;
            this.attribute = attribute;
        }

        @Override
        public Object apply(HttpRequestExtend requestExtend)
        {
            return Dson.fromStringByAttribute(attribute, type, requestExtend.getUtf8StrBody());
        }
    }

    class UnifiedObjectParse implements Function<HttpRequestExtend, Object>
    {
        private Constructor                           constructor;
        private ValueAccessor[]                       valueAccessors;
        private Function<HttpRequestExtend, Object>[] valueGenerators;
        private Type                                  type;

        public UnifiedObjectParse(Class ckass, Type type)
        {
            this.type = type;
            if (ckass.isInterface() || ckass.isArray())
            {
            }
            else
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
                    for (Field field : ckass.getDeclaredFields())
                    {
                        if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers()))
                        {
                            continue;
                        }
                        list.add(ValueAccessor.standard(field));
                        switch (ReflectUtil.getClassId(field.getType()))
                        {
                            case ReflectUtil.CLASS_INT,
                                 ReflectUtil.PRIMITIVE_INT -> gen.add(new SimpleClassParse(field.getName(), value -> value == null ? null : value instanceof String ? Integer.valueOf((String) value) : value instanceof Number ? ((Number) value).intValue() : new IllegalArgumentException()));
                            case ReflectUtil.CLASS_BOOL,
                                 ReflectUtil.PRIMITIVE_BOOL -> gen.add(new SimpleClassParse(field.getName(), value -> value == null ? null : value instanceof String ? Boolean.valueOf((String) value) : value instanceof Boolean ? value : new IllegalArgumentException()));
                            case ReflectUtil.CLASS_BYTE,
                                 ReflectUtil.PRIMITIVE_BYTE -> gen.add(new SimpleClassParse(field.getName(), value -> value == null ? null : value instanceof String ? Byte.valueOf((String) value) : value instanceof Number ? ((Number) value).byteValue() : new IllegalArgumentException()));
                            case ReflectUtil.CLASS_SHORT,
                                 ReflectUtil.PRIMITIVE_SHORT -> gen.add(new SimpleClassParse(field.getName(), value -> value == null ? null : value instanceof String ? Short.valueOf((String) value) : value instanceof Number ? ((Number) value).shortValue() : new IllegalArgumentException()));
                            case ReflectUtil.CLASS_LONG,
                                 ReflectUtil.PRIMITIVE_LONG -> gen.add(new SimpleClassParse(field.getName(), value -> value == null ? null : value instanceof String ? Long.valueOf((String) value) : value instanceof Number ? ((Number) value).longValue() : new IllegalArgumentException()));
                            case ReflectUtil.CLASS_FLOAT,
                                 ReflectUtil.PRIMITIVE_FLOAT -> gen.add(new SimpleClassParse(field.getName(), value -> value == null ? null : value instanceof String ? Float.valueOf((String) value) : value instanceof Number ? ((Number) value).floatValue() : new IllegalArgumentException()));
                            case ReflectUtil.CLASS_DOUBLE,
                                 ReflectUtil.PRIMITIVE_DOUBLE -> gen.add(new SimpleClassParse(field.getName(), value -> value == null ? null : value instanceof String ? Double.valueOf((String) value) : value instanceof Number ? ((Number) value).doubleValue() : new IllegalArgumentException()));
                            case ReflectUtil.CLASS_STRING -> gen.add(new SimpleClassParse(field.getName(), value -> value == null ? null : value instanceof String ? value : String.valueOf(value)));
                            case ReflectUtil.CLASS_BIGDECIMAL -> gen.add(new SimpleClassParse(field.getName(), value -> value == null ? null : value instanceof String ? new java.math.BigDecimal((String) value) : value instanceof Number ? new java.math.BigDecimal(value.toString()) : new IllegalArgumentException()));
                            case ReflectUtil.CLASS_ENUM ->
                            {
                                Class enumType = field.getType();
                                gen.add(new SimpleClassParse(field.getName(), value -> value == null ? null : value instanceof String ? Enum.valueOf(enumType, (String) value) : value.getClass() == enumType ? value : new IllegalArgumentException()));
                            }
                            case ReflectUtil.CLASS_CHAR, ReflectUtil.PRIMITIVE_CHAR, ReflectUtil.CLASS_OBJECT -> gen.add(new UnSupportValueType(field));
                            default -> throw new IllegalStateException("Unexpected value: " + ReflectUtil.getClassId(field.getType()));
                        }
                    }
                    ckass = ckass.getSuperclass();
                }
                valueAccessors  = list.toArray(ValueAccessor[]::new);
                valueGenerators = gen.toArray(Function[]::new);
            }
        }

        @Override
        public Object apply(HttpRequestExtend requestExtend)
        {
            String contentType = requestExtend.getContentType();
            if (contentType != null && contentType.toLowerCase().startsWith("application/json"))
            {
                String utf8StrBody = requestExtend.getUtf8StrBody();
                if (utf8StrBody != null)
                {
                    return Dson.fromString(type, utf8StrBody);
                }
                else
                {
                    return null;
                }
            }
            // 其他情况从 paramMap 中获取值填充对象
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

    class FilePartParse implements Function<HttpRequestExtend, Object>
    {
        @Override
        public Object apply(HttpRequestExtend httpRequestExtend)
        {
            return httpRequestExtend.getFileParts();
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
