package tradingbot.config;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;

/**
 * Bucket4j configuration for distributed inbound rate limiting.
 *
 * <p>Provides a Redis-backed {@link ProxyManager} used by
 * {@link tradingbot.security.filter.AuthRateLimitFilter} to enforce
 * per-IP request quotas cluster-wide.
 *
 * <p>Assumes a standalone Redis instance (Lettuce, not cluster).
 */
@Configuration
public class Bucket4jConfig {

    @Bean
    ProxyManager<String> authRateLimitProxyManager(RedisConnectionFactory redisConnectionFactory) {
        RedisClient redisClient = (RedisClient) ((LettuceConnectionFactory) redisConnectionFactory).getNativeClient();
        var connection = redisClient.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
        return LettuceBasedProxyManager.builderFor(connection)
                .withExpirationStrategy(ExpirationAfterWriteStrategy.fixedTimeToLive(Duration.ofMinutes(2)))
                .build();
    }
}
