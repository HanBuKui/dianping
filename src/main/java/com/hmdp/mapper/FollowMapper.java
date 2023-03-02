package com.hmdp.mapper;

import com.hmdp.entity.Follow;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author
 * @since 2021-12-22
 */
public interface FollowMapper extends BaseMapper<Follow> {

    void cancelFollow(@Param("userId") Long userId,@Param("followUserId") Long followUserId);
}
