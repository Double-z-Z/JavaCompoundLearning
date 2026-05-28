-- 可重入加锁：Hash 结构，owner + threadId:count
-- KEYS[1]: 锁 key
-- ARGV[1]: owner (UUID)
-- ARGV[2]: threadId
-- ARGV[3]: ttl (秒)
-- 返回: {1, count} 成功(含重入), {0, 0} 被他人持有

local key = KEYS[1]
local owner = ARGV[1]
local threadId = ARGV[2]
local ttl = tonumber(ARGV[3])

local currentOwner = redis.call('HGET', key, 'owner')

if currentOwner == owner then
    -- 同一实例，重入
    local count = redis.call('HINCRBY', key, threadId, 1)
    redis.call('EXPIRE', key, ttl)
    return {1, count}
elseif currentOwner == false then
    -- 锁空闲，首次获取
    redis.call('HSET', key, 'owner', owner, threadId, 1)
    redis.call('EXPIRE', key, ttl)
    return {1, 1}
else
    -- 被其他实例持有
    return {0, 0}
end
