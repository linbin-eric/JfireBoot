package cc.jfire.boot.forward.path;

import cc.jfire.baseutil.StringUtil;
import lombok.Data;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RestfulMatch
{
    private AnalysisNode[] analysisNodes;

    public RestfulMatch(String path)
    {
        List<AnalysisNode> list = new ArrayList<>();
        int                index;
        while ((index = path.indexOf("${")) != -1)
        {
            if (index == 0)
            {
                int end = path.indexOf("}");
                if (end == -1)
                {
                    throw new IllegalStateException("地址没有完全使用${}包围，无法解析");
                }
                String       name         = path.substring(index + 2, end);
                AnalysisNode analysisNode = new AnalysisNode();
                analysisNode.setFragment(name);
                analysisNode.setParameter(true);
                if (!list.isEmpty())
                {
                    AnalysisNode last = list.get(list.size() - 1);
                    last.setNext(analysisNode);
                    analysisNode.setPrev(last);
                }
                list.add(analysisNode);
                path = path.substring(end + 1);
            }
            else
            {
                String       pathFragment = path.substring(0, index);
                AnalysisNode node         = new AnalysisNode();
                node.setFragment(pathFragment);
                node.setParameter(false);
                if (!list.isEmpty())
                {
                    AnalysisNode last = list.get(list.size() - 1);
                    last.setNext(node);
                    node.setPrev(last);
                }
                list.add(node);
                path = path.substring(index);
            }
        }
        if (StringUtil.isNotBlank(path))
        {
            AnalysisNode node = new AnalysisNode();
            node.setFragment(path);
            node.setParameter(false);
            if (!list.isEmpty())
            {
                AnalysisNode last = list.get(list.size() - 1);
                last.setNext(node);
                node.setPrev(last);
            }
            list.add(node);
        }
        analysisNodes = list.toArray(AnalysisNode[]::new);
    }

    public boolean match(String path, Map<String, Object> params)
    {
        int start = 0;
        for (AnalysisNode analysisNode : analysisNodes)
        {
            if (analysisNode.parameter)
            {
                continue;
            }
            else
            {
                int index = path.indexOf(analysisNode.getFragment(), start);
                if (index == -1)
                {
                    return false;
                }
                start = index + analysisNode.getFragment().length();
            }
        }
        int index = start = 0;
        for (int i = 0; i < analysisNodes.length; i++)
        {
            AnalysisNode analysisNode = analysisNodes[i];
            if (analysisNode.isParameter())
            {
                continue;
            }
            else
            {
                index = path.indexOf(analysisNode.fragment, start);
                if (start == index)
                {
                    ;
                }
                else
                {
                    params.put(analysisNode.getPrev().getFragment(), path.substring(start, index));
                }
                start = index + analysisNode.fragment.length();
            }
        }
        if (start != path.length())
        {
            params.put(analysisNodes[analysisNodes.length - 1].getFragment(), path.substring(start));
        }
        return true;
    }

    @Data
    class AnalysisNode
    {
        @ToString.Exclude
        AnalysisNode prev;
        @ToString.Exclude
        AnalysisNode next;
        String  fragment;
        boolean parameter;
    }
}
