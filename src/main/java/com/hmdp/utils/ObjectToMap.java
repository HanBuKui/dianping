package com.hmdp.utils;

import com.hmdp.dto.UserDTO;

import java.util.HashMap;
import java.util.Map;

/**
 * @ClassName: ObjectToMap
 * @Description: 写入redis前 序列化 为Value为String类型的Map
 * @author: zjh
 * @date: 2023/2/14  21:49
 * @Version: 1.0
 */
public class ObjectToMap {

    public static Map<String,String> UserDto2Map(UserDTO userDTO){
        Map<String,String> map = new HashMap<>();
        map.put("id",userDTO.getId().toString());
        map.put("nickName",userDTO.getNickName());
        map.put("icon",userDTO.getIcon());
        return map;
    }

}
