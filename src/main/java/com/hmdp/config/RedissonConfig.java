package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @ClassName: RedissonConfig
 * @Description: TODO
 * @author: zjh
 * @date: 2023/2/23  22:07
 * @Version: 1.0
 */

@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient(){
        //配置
        Config config = new Config();
        //单机
        config.useSingleServer().setAddress("redis://120.0.0.1:6379");
        // 创建RedissonClient对象
        return Redisson.create(config);
    }
}
