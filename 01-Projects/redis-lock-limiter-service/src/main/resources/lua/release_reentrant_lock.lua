-- 可重入解锁：递减计数，归零时删 key
-- KEYS[1]: 锁 key
-- ARGV[1]: owner (UUID)
-- ARGV[2]: threadId
-- 返回: 2 完全释放, 1 重入计数递减, 0 不是自己的锁

local key = KEYS[1]
local owner = ARGV[1]
local threadId = ARGV[2]

local currentOwner = redis.call('HGET', key, 'owner')
if currentOwner ~= owner then
    return 0
end

local count = redis.call('HINCRBY', key, threadId, -1)
if count <= 0 then
    redis.call('HDEL', key, threadId)
    -- 检查是否还有其他 threadId 持有
    local fields = redis.call('HKEYS', key)
    local hasOtherThread = false
    for _, f in ipairs(fields) do
        if f ~= 'owner' then
            hasOtherThread = true
            break
        end
    end
    if not hasOtherThread then
        redis.call('DEL', key)
        return 2  -- 完全释放
    end
end
return 1  -- 仍被持有（本线程或其他线程）
