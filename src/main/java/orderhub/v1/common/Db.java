package orderhub.v1.common;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import orderhub.api.ApiException;

public final class Db {
    private Db() {}
    public static OffsetDateTime now() { return OffsetDateTime.now(ZoneOffset.UTC); }
    public static <T> T require(T value) {
        if (value == null) throw ApiException.missing("资源不存在");
        return value;
    }
    public static void check(boolean condition, String message) {
        if (!condition) throw ApiException.conflict(message);
    }
    public static <T> T lock(BaseMapper<T> mapper, long id) {
        return require(mapper.selectOne(new QueryWrapper<T>().eq("id",id).last("FOR UPDATE")));
    }
}
