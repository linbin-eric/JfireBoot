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

    @Test
    public void testSegmentCountMismatch()
    {
        // /api/novels/${id} 不应该匹配 /api/novels/1/characters
        RestfulMatch        match = new RestfulMatch("/api/novels/${id}");
        Map<String, Object> map   = new HashMap<>();
        Assert.assertTrue(match.match("/api/novels/1", map));
        Assert.assertEquals("1", map.get("id"));
        map.clear();
        Assert.assertFalse(match.match("/api/novels/1/characters", map));
        Assert.assertFalse(match.match("/api/novels/1/characters/2", map));
        // /api/novels/${novelId}/characters 应该正确匹配
        RestfulMatch match2 = new RestfulMatch("/api/novels/${novelId}/characters");
        map.clear();
        Assert.assertTrue(match2.match("/api/novels/1/characters", map));
        Assert.assertEquals("1", map.get("novelId"));
        map.clear();
        Assert.assertFalse(match2.match("/api/novels/1", map));
    }
}
