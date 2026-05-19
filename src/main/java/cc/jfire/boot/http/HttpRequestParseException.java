package cc.jfire.boot.http;

/**
 * AI 生成：表示请求解析阶段发现的客户端协议错误，并携带可映射到 HTTP 响应的状态信息。
 */
public class HttpRequestParseException extends RuntimeException
{
    private final int    statusCode;
    private final String reasonPhrase;
    private final String clientMessage;

    /**
     * AI 生成：集中保存状态码、原因短语和返回给客户端的错误消息。
     */
    private HttpRequestParseException(int statusCode, String reasonPhrase, String clientMessage)
    {
        super(clientMessage);
        this.statusCode = statusCode;
        this.reasonPhrase = reasonPhrase;
        this.clientMessage = clientMessage;
    }

    /**
     * AI 生成：构造 400 Bad Request，用于普通请求格式错误。
     */
    public static HttpRequestParseException badRequest(String message)
    {
        return new HttpRequestParseException(400, "Bad Request", message);
    }

    /**
     * AI 生成：构造 413 Payload Too Large，用于解析阶段发现的大小限制错误。
     */
    public static HttpRequestParseException payloadTooLarge(String message)
    {
        return new HttpRequestParseException(413, "Payload Too Large", message);
    }

    /**
     * AI 生成：返回异常应映射到 HTTP 响应的状态码。
     */
    public int getStatusCode()
    {
        return statusCode;
    }

    /**
     * AI 生成：返回异常应映射到 HTTP 响应的 reason phrase。
     */
    public String getReasonPhrase()
    {
        return reasonPhrase;
    }

    /**
     * AI 生成：返回可写入响应体的客户端错误消息。
     */
    public String getClientMessage()
    {
        return clientMessage;
    }
}
