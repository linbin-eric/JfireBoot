package cc.jfire.boot.forward.path;

/**
 * HTTP 请求方法枚举
 */
public enum HttpMethod
{
    GET,
    POST,
    PUT,
    DELETE,
    PATCH,
    HEAD,
    OPTIONS,
    /**
     * 匹配所有 HTTP 方法（默认值）
     */
    ALL;

    /**
     * 判断当前枚举值是否匹配指定的请求方法
     *
     * @param requestMethod 请求的 HTTP 方法字符串（如 "GET"、"POST"）
     * @return 如果匹配返回 true，否则返回 false
     */
    public boolean matches(String requestMethod)
    {
        if (this == ALL)
        {
            return true;
        }
        return this.name().equalsIgnoreCase(requestMethod);
    }
}
