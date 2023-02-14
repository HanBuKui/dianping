package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @ClassName: ReFreshTokenInterception
 * @Description: 拦截所有操作，目的是为token刷新存活时间； 至于验证是否登录由下一个拦截器进行判断
 * @author: zjh
 * @date: 2023/2/14  22:07
 * @Version: 1.0
 */
public class RefreshTokenInterception implements HandlerInterceptor {

    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterception(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 获取请求头中的token
        String token = request.getHeader("authorization");

        if(StrUtil.isBlank(token)){
            return true;
        }
        // 2. 基于token获取redis中的用户
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(RedisConstants.LOGIN_USER_KEY + token);
        // 3. 判断用户是否存在
        if(userMap.isEmpty()) {
            // 4. 不存在
            return true;
        }
        // 5. 将查询到的Hash数据转为UserDto对象
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        // 6. 存在，用户信息保存到ThreadLocal
        UserHolder.saveUser(userDTO);
        // 7. 刷新token有效期
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY+token,RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        return true;
    }

    @Override
    /**
     * 调用前提：preHandle返回true
     * DispatcherServlet进行视图的渲染之后
     * 多用于清理资源
     */
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //移除用户
        UserHolder.removeUser();
    }
}
