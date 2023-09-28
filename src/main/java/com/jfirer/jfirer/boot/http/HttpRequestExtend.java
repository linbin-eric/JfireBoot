package com.jfirer.jfirer.boot.http;

import com.jfirer.baseutil.StringUtil;
import com.jfirer.dson.Dson;
import com.jfirer.jnet.common.api.Pipeline;
import com.jfirer.jnet.common.buffer.buffer.IoBuffer;
import com.jfirer.jnet.common.util.HttpDecodeUtil;
import com.jfirer.jnet.extend.http.decode.ContentType;
import com.jfirer.jnet.extend.http.decode.HttpRequest;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Getter
public class HttpRequestExtend extends HttpRequest
{
    private             String              utf8StrBody;
    private             Map<String, Object> paramMap;
    @Getter(AccessLevel.NONE)
    private             Map<String, String> queryParamMap;
    private             String              path;
    @Setter
    private             Pipeline            pipeline;
    protected           List<BoundaryPart>  parts       = DUMMY_PARTS;
    public static final List<BoundaryPart>  DUMMY_PARTS = new LinkedList<>();

    public static HttpRequestExtend from(HttpRequest request, Pipeline pipeline)
    {
        if (request == null)
        {
            return null;
        }
        HttpRequestExtend httpRequestExtend = new HttpRequestExtend();
        httpRequestExtend.setMethod(request.getMethod());
        httpRequestExtend.setPipeline(pipeline);
        httpRequestExtend.setUrl(request.getUrl());
        httpRequestExtend.setVersion(request.getVersion());
        httpRequestExtend.setHeaders(request.getHeaders());
        httpRequestExtend.setContentLength(request.getContentLength());
        httpRequestExtend.setContentType(request.getContentType());
        httpRequestExtend.setBody(request.getBody());
        httpRequestExtend.parseMaybeMutliparts();
        httpRequestExtend.parsePath();
        httpRequestExtend.parseUtf8Value();
        httpRequestExtend.parseParamMap();
        return httpRequestExtend;
    }

    @Override
    public void close()
    {
        super.close();
        parts.forEach(part -> part.close());
    }

    public void parseMaybeMutliparts()
    {
        if (contentType != null && contentType.toLowerCase().startsWith("multipart/form-data"))
        {
            byte[] boundary      = ("--" + contentType.substring(contentType.indexOf("boundary=") + 9)).getBytes(StandardCharsets.US_ASCII);
            int[]  prefix        = HttpDecodeUtil.computePrefix(boundary);
            int    boundaryIndex = HttpDecodeUtil.findSubArray(body, boundary, prefix);
            if (boundaryIndex != body.getReadPosi())
            {
                throw new IllegalArgumentException();
            }
            body.addReadPosi(boundary.length + 2);
            parts = new ArrayList<>();
            while (true)
            {
                boundaryIndex = HttpDecodeUtil.findSubArray(body, boundary, prefix);
                if (boundaryIndex != -1)
                {
                    //数据范围需要将回车换行去掉
                    IoBuffer slice = body.slice(boundaryIndex - body.getReadPosi() - 2);
                    parts.add(new BoundaryPart(slice));
                    body.addReadPosi(2 + boundary.length + 2);
                }
                else
                {
                    break;
                }
            }
            body.free();
            body = null;
        }
    }

    @Data
    public static class BoundaryPart
    {
        @Setter(AccessLevel.NONE)
        private Map<String, String> headers  = new HashMap<>();
        private String              contentType;
        private String              fileName;
        private String              fieldName;
        private IoBuffer            data;
        private boolean             isBinary = false;
        private String              utf8Value;

        public BoundaryPart(IoBuffer slice)
        {
            HttpDecodeUtil.findAllHeaders(slice, this::putHeader);
            this.data = slice;
            analysisHeaders();
            mayBeUtf8Value();
        }

        public void putHeader(String header, String value)
        {
            headers.put(header, value);
        }

        public void close()
        {
            if (data != null)
            {
                data.free();
            }
        }

        private void mayBeUtf8Value()
        {
            if (!isBinary)
            {
                utf8Value = StandardCharsets.UTF_8.decode(data.readableByteBuffer()).toString();
                data.free();
                data = null;
            }
        }

        private void analysisHeaders()
        {
            HttpDecodeUtil.findContentType(headers, this::setContentType);
            if (contentType == null)
            {
                contentType = "text/plain";
            }
            switch (contentType)
            {
                case "application/java-archive", "application/zip", ContentType.STREAM -> isBinary = true;
            }
            String value       = headers.get("Content-Disposition");
            int    indexOfName = value.indexOf("name=");
            int    endOfIndex  = value.indexOf('"', indexOfName + 6);
            fieldName = value.substring(indexOfName + 6, endOfIndex);
            int index = value.indexOf("filename=");
            if (index != -1)
            {
                fileName = value.substring(index + 10, value.indexOf('"', index + 11));
                isBinary = true;
            }
            if ((index = value.indexOf("filename*=UTF-8''")) != -1)
            {
                fileName = URLDecoder.decode(value.substring(index + 17), StandardCharsets.UTF_8);
                isBinary = true;
            }
        }
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
            path          = url;
            queryParamMap = new HashMap<>();
        }
        else
        {
            path          = url.substring(0, index);
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
        if (getMethod().equalsIgnoreCase("post") && getContentType().toLowerCase().startsWith("application/json") && StringUtil.isNotBlank(getUtf8StrBody()))
        {
            Object o = Dson.fromString(getUtf8StrBody());
            if (o instanceof Map)
            {
                paramMap = (Map<String, Object>) o;
            }
            else
            {
                paramMap = new HashMap<>();
            }
        }
        else
        {
            paramMap = new HashMap<>();
        }
        paramMap.putAll(queryParamMap);
        if (parts.isEmpty() == false)
        {
            parts.stream()//
                 .filter(v -> !v.isBinary())//
                 .filter(v -> StringUtil.isNotBlank(v.getFieldName()))//
                 .forEach(v -> paramMap.put(v.getFieldName(), v.getUtf8Value()));
        }
    }
}
