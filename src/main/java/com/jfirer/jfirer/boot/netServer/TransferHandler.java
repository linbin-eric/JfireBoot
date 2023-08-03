package com.jfirer.jfirer.boot.netServer;

import com.jfirer.jnet.common.api.Pipeline;
import com.jfirer.jnet.extend.http.decode.HttpRequest;

import java.util.function.BiFunction;

public interface TransferHandler extends BiFunction<HttpRequest, Pipeline,Boolean>
{
}
