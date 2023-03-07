package com.hmdp.config;

import com.hmdp.utils.interceptor.LoginInterception;
import com.hmdp.utils.interceptor.RefreshTokenInterception;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @ClassName: MvcConfig
 * @Description: 注册拦截器
 * @author: zjh
 * @date: 2023/2/13  22:38
 * @Version: 1.0
 */

@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    /*
    这个类有@Configuration注解，是由Spring帮我们生成的，所以可以在这里注入生成template，再放到拦截器里
     */

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        //刷新拦截器
        registry.addInterceptor(new RefreshTokenInterception(stringRedisTemplate));
        //登录拦截器
        registry.addInterceptor(new LoginInterception())
                .excludePathPatterns(   //这些功能是不拦截的
                        "/user/code",
                        "/user/login",
                        "/blog/hot",
                        "/shop/**",
                        "/shop-type/**",
                        "/voucher/**"
                );
    }
}
