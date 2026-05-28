-- 可重入锁续期：仅续期自己持有的锁
-- KEYS[1]: 锁 key
-- ARGV[1]: owner (UUID)
-- ARGV[2]: ttl (秒)
-- 返回: 1 成功, 0 锁不属于当前实例

local key = KEYS[1]
local owner = ARGV[1]
local ttl = tonumber(ARGV[2])

local currentOwner = redis.call('HGET', key, 'owner')
if currentOwner == owner then
    redis.call('EXPIRE', key, ttl)
    return 1
else
    return 0
end
