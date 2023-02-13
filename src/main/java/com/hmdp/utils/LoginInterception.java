package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * @ClassName: LoginInterception
 * @Description: 登录校验拦截器
 * @author: zjh
 * @date: 2023/2/13  22:17
 * @Version: 1.0
 */

public class LoginInterception implements HandlerInterceptor {
    @Override
    /*
    这个是请求预处理的方法，只有当这个方法返回值为 true 的时候，后面的方法才会执行
    SpringMVC 中的拦截器，相当于 Jsp/Servlet 中的过滤器，只不过拦截器的功能更为强大。
    */
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 获取session
        HttpSession session = request.getSession();
        // 2. 获取session中的用户
        Object user = session.getAttribute("user");
        // 3. 判断用户是否存在
        if(user == null) {
            // 4. 不存在，拦截
            response.setStatus(401);   //401状态码：用户未授权
            return false;
        }
        // 5. 存在，用户信息保存到ThreadLocal
        UserHolder.saveUser( new UserDTO((User) user));
        //6. 放行
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
