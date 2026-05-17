-- 解锁脚本：只能删除自己持有的锁
local key = KEYS[1]
local value = ARGV[1]

local current = redis.call('GET', key)
if current == value then
    redis.call('DEL', key)
    return 1
else
    return 0
end