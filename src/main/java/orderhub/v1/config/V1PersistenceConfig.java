package orderhub.v1.config;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan(basePackages="orderhub.v1", annotationClass=Mapper.class,nameGenerator=org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator.class)
public class V1PersistenceConfig {}
