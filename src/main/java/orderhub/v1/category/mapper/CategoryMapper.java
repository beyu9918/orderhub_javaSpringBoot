package orderhub.v1.category.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import orderhub.v1.category.entity.Category;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CategoryMapper extends BaseMapper<Category> {}
