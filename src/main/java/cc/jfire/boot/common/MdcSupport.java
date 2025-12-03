package cc.jfire.boot.common;

import cc.jfire.baseutil.TRACEID;
import cc.jfire.jfire.core.aop.ProceedPoint;
import cc.jfire.jfire.core.aop.notated.After;
import cc.jfire.jfire.core.aop.notated.Before;
import cc.jfire.jfire.core.aop.notated.EnhanceClass;
import org.slf4j.MDC;

@EnhanceClass("com.jfirer.jfirer.boot.http.*")
public abstract class MdcSupport
{
    @Before(custom = EnhanceTraceId.class)
    public void before(ProceedPoint point)
    {
        if (MDC.get("traceId") == null)
        {
            MDC.put("traceId", TRACEID.newTraceId());
            MDC.put("traceIdRoot", point.getMethod().methodName());
        }
    }

    @After(custom = EnhanceTraceId.class)
    public void after(ProceedPoint point)
    {
        String traceIdRoot = MDC.get("traceIdRoot");
        if (traceIdRoot != null && traceIdRoot.equals(point.getMethod().methodName()))
        {
            MDC.remove("traceId");
        }
    }
}
