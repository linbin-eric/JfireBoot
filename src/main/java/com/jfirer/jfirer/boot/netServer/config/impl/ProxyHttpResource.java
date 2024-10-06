package com.jfirer.jfirer.boot.netServer.config.impl;

import com.jfirer.baseutil.StringUtil;
import com.jfirer.jfirer.boot.netServer.config.ResourceConfig;
import com.jfirer.jfirer.boot.netServer.config.ResourceHandler;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ProxyHttpResource implements ResourceConfig
{
    private String prefixMatch;
    private String proxy;
    private String exactMatch;

    @Override
    public ResourceHandler parse()
    {
        if (StringUtil.isNotBlank(prefixMatch) && prefixMatch.contains("*") == false)
        {
            return new PrefixMatchProxyHttpHandler(prefixMatch, proxy);
        }
        else
        {
            throw new IllegalArgumentException();
        }
    }
}
