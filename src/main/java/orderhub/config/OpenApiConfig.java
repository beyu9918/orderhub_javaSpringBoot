package orderhub.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.*;
import org.springframework.context.annotation.*;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI orderHubApi() {
        return new OpenAPI().info(new Info().title("OrderHub API").version("v1")
                .description("V1 电商订单与履约：账号、地址、SPU/SKU、库存预占、优惠券、模拟支付、物流、退款、审计与报表。金额单位：人民币元。新接口前缀 /api/v1。"))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
