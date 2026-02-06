package cc.jfire.boot.forward.path;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RestfulMatch
{
    private Segment[] segments;

    public RestfulMatch(String path)
    {
        String[]      parts = path.split("/", -1);
        List<Segment> list  = new ArrayList<>();
        for (String part : parts)
        {
            if (part.isEmpty())
            {
                continue;
            }
            if (part.startsWith("${") && part.endsWith("}"))
            {
                String name = part.substring(2, part.length() - 1);
                list.add(new Segment(name, true));
            }
            else
            {
                list.add(new Segment(part, false));
            }
        }
        this.segments = list.toArray(new Segment[0]);
    }

    public boolean match(String path, Map<String, Object> params)
    {
        String[]     parts           = path.split("/", -1);
        List<String> requestSegments = new ArrayList<>();
        for (String part : parts)
        {
            if (!part.isEmpty())
            {
                requestSegments.add(part);
            }
        }
        if (requestSegments.size() != segments.length)
        {
            return false;
        }
        Map<String, Object> tempParams = new HashMap<>();
        for (int i = 0; i < segments.length; i++)
        {
            Segment segment = segments[i];
            String  actual  = requestSegments.get(i);
            if (segment.parameter)
            {
                tempParams.put(segment.value, actual);
            }
            else
            {
                if (!segment.value.equals(actual))
                {
                    return false;
                }
            }
        }
        params.putAll(tempParams);
        return true;
    }

    record Segment(String value, boolean parameter)
    {
    }
}
