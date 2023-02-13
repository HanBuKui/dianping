package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexPatterns;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import jdk.nashorn.internal.runtime.regexp.joni.Regex;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author
 * @since 2021-12-22
 */
@Slf4j
@Service("userService")
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1. 校验手机号
        if(RegexUtils.isPhoneInvalid(phone)){
            //2. 不符合，返回错误信息
            return Result.fail("手机号码格式错误！");
        }
        //3. 符合，生成验证码
        String code = RandomUtil.randomNumbers(6);

        //4. 保存验证码到session
        session.setAttribute("code",code);

        //5. 发送验证码
        log.debug("发送短信验证码成功,接收手机号：{} => 验证码：{}",phone,code);

        //返回ok
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {

        String phone = loginForm.getPhone();

        //1. 校验手机号和验证码
        if(RegexUtils.isPhoneInvalid(phone))  return Result.fail("手机号码格式错误！");

        Object cacheCode = session.getAttribute("code");
        String code = loginForm.getCode();

        log.debug("code:{},cacheCode:{}",code,cacheCode.toString());

        if(cacheCode == null || !cacheCode.toString().equals(code)){
            return Result.fail("验证码错误！");
        }

        //2. 一致 根据手机号查用户
        User user = query().eq("phone", phone).one();

        //3. 判断用户是否存在
        if(user == null){
            //不存在，创建新的用户
            user = createUserWithPhone(phone);
        }
        //4. 保存用户到session
        session.setAttribute("user",user);
        return Result.ok();
    }

    private User createUserWithPhone(String phone) {
        // 1. 创建用户
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        // 2. 保存用户
        save(user);
        return user;
    }
}
