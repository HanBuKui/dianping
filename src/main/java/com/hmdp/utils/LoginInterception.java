package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @ClassName: LoginInterception
 * @Description: 登录校验拦截器
 * @author: zjh
 * @date: 2023/2/13  22:17
 * @Version: 1.0
 */

@Slf4j
public class LoginInterception implements HandlerInterceptor {

    @Override
    /*
    这个是请求预处理的方法，只有当这个方法返回值为 true 的时候，后面的方法才会执行
    SpringMVC 中的拦截器，相当于 Jsp/Servlet 中的过滤器，只不过拦截器的功能更为强大。
    */
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
       // 1. 判断是否需要拦截：ThreadLocal中是否有用户
        if (UserHolder.getUser() == null){
            response.setStatus(401);
            return false;  //拦截
        }
        //有用户，则放行
        return true;
    }
}
