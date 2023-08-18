package com.jfirer.jfirer.boot.forward.path;

import com.jfirer.baseutil.StringUtil;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RestfulMatch
{
    private final String         path;
    private       AnalysisNode[] analysisNodes;

    public RestfulMatch(String path)
    {
        List<AnalysisNode> list = new ArrayList<>();
        this.path = path;
        int index;
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
                path.substring(end + 1);
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
                path.substring(index);
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
            path.substring(index);
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
        start = 0;
        for (int i = 0; i < analysisNodes.length; i++)
        {
            AnalysisNode analysisNode = analysisNodes[i];
            if (analysisNode.isParameter())
            {
                continue;
            }
            else
            {
                int index = path.indexOf(analysisNode.fragment, start);
                if (start == index)
                {
                    start += analysisNode.fragment.length();
                }
                else{
                    params.put(analysisNode.getPrev().getFragment(), path.substring(start, index));
                }
            }
        }
    }

    @Data
    class AnalysisNode
    {
        AnalysisNode prev;
        AnalysisNode next;
        String       fragment;
        boolean      parameter;
    }
}
