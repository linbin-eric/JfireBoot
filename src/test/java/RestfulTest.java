import cc.jfire.boot.forward.path.RestfulMatch;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class RestfulTest
{
    @Test
    public void test()
    {
        String              path   = "/js/${name}/other/${last}";
        RestfulMatch        match  = new RestfulMatch(path);
        Map<String, Object> map    = new HashMap<>();
        boolean             result = match.match("/js/name/other/last1", map);
        Assert.assertTrue(result);
        Assert.assertEquals("name", map.get("name"));
        Assert.assertEquals("last1", map.get("last"));
        Assert.assertFalse(match.match("/js/name/last1/other", map));
        match = new RestfulMatch("/${name}/js/${last}/end");
        map.clear();
        Assert.assertTrue(match.match("/name/js/last1/end", map));
        Assert.assertEquals("name", map.get("name"));
        Assert.assertEquals("last1", map.get("last"));
        Assert.assertFalse(match.match("/name/js/last", map));
    }
}
