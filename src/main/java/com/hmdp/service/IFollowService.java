package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author
 * @since 2021-12-22
 */
public interface IFollowService extends IService<Follow> {

    public Result follow(Long followUserId, Boolean isFollow);

    public Result isFollow(Long followUserId);

    public Result followCommons(Long id);
}
