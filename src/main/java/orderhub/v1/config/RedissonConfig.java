package orderhub.v1.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;

@Configuration
public class RedissonConfig {
    // Keep Lettuce/StringRedisTemplate for data. Redisson is used only for distributed mutexes.
    @Bean(destroyMethod="shutdown") @Lazy
    @ConditionalOnProperty(name="orderhub.coupon.redis-enabled",havingValue="true")
    RedissonClient couponRedisson(
            @Value("${spring.data.redis.host}") String host,
            @Value("${spring.data.redis.port}") int port,
            @Value("${spring.data.redis.database:0}") int database,
            @Value("${spring.data.redis.username:}") String username,
            @Value("${spring.data.redis.password:}") String password,
            @Value("${spring.data.redis.ssl.enabled:false}") boolean ssl) {
        Config config = new Config();
        config.setCodec(StringCodec.INSTANCE).setThreads(2).setNettyThreads(4).setLockWatchdogTimeout(30000);
        var server = config.useSingleServer().setAddress((ssl?"rediss://":"redis://")+host+":"+port)
            .setDatabase(database).setConnectTimeout(1000).setTimeout(1000).setRetryAttempts(0)
            .setConnectionMinimumIdleSize(1).setConnectionPoolSize(8)
            .setSubscriptionConnectionMinimumIdleSize(1).setSubscriptionConnectionPoolSize(4);
        if(!username.isBlank())server.setUsername(username);
        if(!password.isBlank())server.setPassword(password);
        return Redisson.create(config);
    }
}
