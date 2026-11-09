package orderhub.v1.common;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import orderhub.api.ApiException;
import org.springframework.http.HttpStatus;

public record PageResult<T>(long page,long size,long total,List<T> records) {
    public static <T> PageResult<T> of(BaseMapper<T> mapper, int page,int size,QueryWrapper<T> query) {
        if (page < 1 || page > 100000 || size < 1 || size > 100)
            throw new ApiException(HttpStatus.BAD_REQUEST,"page 为 1–100000，size 为 1–100");
        var result = mapper.selectPage(new Page<>(page,size),query);
        return new PageResult<>(page,size,result.getTotal(),result.getRecords());
    }
}
