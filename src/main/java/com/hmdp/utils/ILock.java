package com.hmdp.utils;

/**
 * @ClassName: ILock
 * @Description: 分布式锁  接口
 * @author: zjh
 * @date: 2023/2/21  22:00
 * @Version: 1.0
 */
public interface ILock {

    /**
    *@Description: 尝试获取锁
    *@Param: timeoutSec 锁持有的超时时间，过期后自动释放
    *@return: boolean true代表获取锁成功；false代表获取锁失败
    */
    public boolean tryLock(long timeoutSec);

    /**
    *@Description: 
    *@Param: []
    *@return: void
    */
    public void unlock();

}
