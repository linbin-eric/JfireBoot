package cc.jfire.boot.http;

import cc.jfire.dson.Dson;
import cc.jfire.jnet.common.api.Pipeline;
import cc.jfire.jnet.common.buffer.buffer.IoBuffer;
import cc.jfire.jnet.common.util.HttpCoderUtil;
import cc.jfire.jnet.extend.http.dto.HttpRequest;
import lombok.Getter;
import lombok.Setter;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Getter
public class HttpRequestExtend implements AutoCloseable
{
    // 从 HttpRequest 复制的关键属性
    private             String              method;
    private             String              url;
    private             String              version;
    private             Map<String, String> headers;
    private             long                contentLength;
    private             String              contentType;
    // 解析后的属性
    private             String              utf8StrBody;
    private             Map<String, Object> paramMap;
    private             String              path;
    @Setter
    private             Pipeline            pipeline;
    protected           List<FilePart>      fileParts        = DUMMY_FILE_PARTS;
    public static final List<FilePart>      DUMMY_FILE_PARTS = new LinkedList<>();

    public static HttpRequestExtend from(HttpRequest request, Pipeline pipeline)
    {
        if (request == null)
        {
            return null;
        }
        HttpRequestExtend extend = new HttpRequestExtend();
        extend.pipeline = pipeline;
        // 复制关键属性
        extend.method        = request.getHead().getMethod();
        extend.url           = request.getHead().getPath();
        extend.version       = request.getHead().getVersion();
        extend.headers       = request.getHead().getHeaders();
        extend.contentLength = request.getHead().getContentLength();
        extend.contentType   = extend.headers != null ? extend.headers.get("Content-Type") : null;
        // 解析 path 和 URL 参数
        extend.parsePath();
        // 根据 Content-Type 解析 body
        IoBuffer body = request.getBody();
        if (body != null && body.remainRead() > 0)
        {
            if (extend.contentType != null && extend.contentType.toLowerCase().startsWith("multipart/form-data"))
            {
                extend.parseMultiPart(body);
            }
            else
            {
                // application/json 或其他类型，解析为 utf8 字符串
                extend.utf8StrBody = StandardCharsets.UTF_8.decode(body.readableByteBuffer()).toString();
                body.free();
            }
        }
        // 释放 HttpRequest（head 部分）
        request.getHead().close();
        return extend;
    }

    @Override
    public void close()
    {
        fileParts.forEach(FilePart::close);
    }

    public Map<String, Object> getNotNullParamMap()
    {
        if (paramMap == null)
        {
            paramMap = new HashMap<>();
        }
        return paramMap;
    }

    private void parsePath()
    {
        int index = url.indexOf("?");
        if (index == -1)
        {
            path = url;
        }
        else
        {
            path     = url.substring(0, index);
            paramMap = new HashMap<>();
            Arrays.stream(url.substring(index + 1).split("&")).forEach(v -> {
                int paramValueIndex = v.indexOf("=");
                if (paramValueIndex == -1)
                {
                    paramMap.put(v, "");
                }
                else
                {
                    paramMap.put(v.substring(0, paramValueIndex), v.substring(paramValueIndex + 1));
                }
            });
        }
    }

    public void parseJsonBodyToParamMap()
    {
        if (paramMap == null)
        {
            paramMap = new HashMap<>();
        }
        if (utf8StrBody != null)
        {
            Object o = Dson.fromString(utf8StrBody);
            if (o instanceof Map map)
            {
                paramMap.putAll(map);
            }
        }
    }

    private void parseMultiPart(IoBuffer body)
    {
        byte[] boundary      = ("--" + contentType.substring(contentType.indexOf("boundary=") + 9)).getBytes(StandardCharsets.US_ASCII);
        int    boundaryIndex = body.indexOf(boundary);
        if (boundaryIndex != body.getReadPosi())
        {
            throw new IllegalArgumentException("Invalid multipart data: boundary not found at expected position");
        }
        body.addReadPosi(boundary.length + 2);
        fileParts = new ArrayList<>();
        if (paramMap == null)
        {
            paramMap = new HashMap<>();
        }
        while (true)
        {
            boundaryIndex = body.indexOf(boundary);
            if (boundaryIndex != -1)
            {
                // 数据范围需要将回车换行去掉
                IoBuffer slice = body.slice(boundaryIndex - body.getReadPosi() - 2);
                parseBoundaryPart(slice);
                body.addReadPosi(2 + boundary.length + 2);
            }
            else
            {
                break;
            }
        }
        body.free();
    }

    private void parseBoundaryPart(IoBuffer slice)
    {
        // 解析 headers
        Map<String, String> partHeaders = new HashMap<>();
        HttpCoderUtil.findAllHeaders(slice, partHeaders::put);
        // 解析 Content-Type
        String partContentType = partHeaders.get("Content-Type");
        if (partContentType == null)
        {
            partContentType = "text/plain";
        }
        // 解析 Content-Disposition
        String disposition = partHeaders.get("Content-Disposition");
        String fieldName   = null;
        String fileName    = null;
        int    indexOfName = disposition.indexOf("name=");
        int    endOfIndex  = disposition.indexOf('"', indexOfName + 6);
        fieldName = disposition.substring(indexOfName + 6, endOfIndex);
        int index = disposition.indexOf("filename=");
        if (index != -1)
        {
            fileName = disposition.substring(index + 10, disposition.indexOf('"', index + 11));
        }
        if ((index = disposition.indexOf("filename*=UTF-8''")) != -1)
        {
            fileName = URLDecoder.decode(disposition.substring(index + 17), StandardCharsets.UTF_8);
        }
        // 根据规则决定处理方式：
        // - 有 filename/filename* 或 Content-Type 为 application/octet-stream：按文件/二进制处理
        // - 否则：按 UTF-8 文本字段处理
        boolean fileLike = fileName != null || "application/octet-stream".equalsIgnoreCase(partContentType);
        if (fileLike)
        {
            // 文件：添加到 fileParts
            FilePart filePart = new FilePart();
            filePart.setFileName(fileName);
            filePart.setFieldName(fieldName);
            filePart.setIoBuffer(slice);
            fileParts.add(filePart);
        }
        else
        {
            // 普通文本字段：解析为字符串存入 paramMap
            String value = StandardCharsets.UTF_8.decode(slice.readableByteBuffer()).toString();
            slice.free();
            paramMap.put(fieldName, value);
        }
    }
}
