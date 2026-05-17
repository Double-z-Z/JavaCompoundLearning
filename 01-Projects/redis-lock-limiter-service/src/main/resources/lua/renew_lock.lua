-- 续期脚本：只能续期自己持有的锁
local key = KEYS[1]
local value = ARGV[1]
local ttl = ARGV[2]

local current = redis.call('GET', key)
if current == value then
    redis.call('EXPIRE', key, ttl)
    return 1
else
    return 0
end