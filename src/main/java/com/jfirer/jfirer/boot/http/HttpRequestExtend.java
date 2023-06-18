package com.jfirer.jfirer.boot.http;

import com.jfirer.baseutil.StringUtil;
import com.jfirer.dson.Dson;
import com.jfirer.jnet.common.api.Pipeline;
import com.jfirer.jnet.extend.http.decode.HttpRequest;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Getter
public class HttpRequestExtend extends HttpRequest
{
    private String              utf8StrBody;
    private Map<String, Object> paramMap;
    @Getter(AccessLevel.NONE)
    private Map<String, String> queryParamMap;
    private String              path;
    @Setter
    private Pipeline            pipeline;

    public static HttpRequestExtend from(HttpRequest request)
    {
        if (request == null)
        {
            return null;
        }
        HttpRequestExtend httpRequestExtend = new HttpRequestExtend();
        httpRequestExtend.setMethod(request.getMethod());
        httpRequestExtend.setUrl(request.getUrl());
        httpRequestExtend.setVersion(request.getVersion());
        httpRequestExtend.setHeaders(request.getHeaders());
        httpRequestExtend.setContentLength(request.getContentLength());
        httpRequestExtend.setContentType(request.getContentType());
        httpRequestExtend.setParts(request.getParts());
        httpRequestExtend.setBody(request.getBody());
        httpRequestExtend.parsePath();
        httpRequestExtend.parseUtf8Value();
        httpRequestExtend.parseParamMap();
        return httpRequestExtend;
    }

    public void parseUtf8Value()
    {
        if (utf8StrBody == null && body != null)
        {
            utf8StrBody = StandardCharsets.UTF_8.decode(body.readableByteBuffer()).toString();
            body.free();
            body = null;
        }
    }

    public void parsePath()
    {
        int index = url.indexOf("?");
        if (index == -1)
        {
            path = url;
            queryParamMap = new HashMap<>();
        }
        else
        {
            path = url.substring(0, index);
            queryParamMap = new HashMap<>();
            Arrays.stream(url.substring(index + 1).split("&")).forEach(v -> {
                int paramValueIndex = v.indexOf("=");
                if (paramValueIndex == -1)
                {
                    queryParamMap.put(v, "");
                }
                else
                {
                    queryParamMap.put(v.substring(0, paramValueIndex), v.substring(paramValueIndex + 1));
                }
            });
        }
    }

    public void parseParamMap()
    {
        if (getMethod().equalsIgnoreCase("post") && StringUtil.isNotBlank(getUtf8StrBody()))
        {
            paramMap = (Map<String, Object>) Dson.fromString(getUtf8StrBody());
            paramMap.putAll(queryParamMap);
        }
        else
        {
            paramMap = new HashMap<>();
            paramMap.putAll(queryParamMap);
        }
        if (paramMap == null)
        {
            paramMap = new HashMap<>();
        }
        parts.stream().filter(v -> !v.isBinary()).filter(v -> StringUtil.isNotBlank(v.getFieldName())).forEach(v -> paramMap.put(v.getFieldName(), v.getUtf8Value()));
    }
}
