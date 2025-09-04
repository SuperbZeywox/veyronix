-- zidx_seed_and_range.lua
-- KEYS: [1]=zset, [2]=compat set
-- ARGV: [1]=start, [2]=end
local zkey   = KEYS[1]
local skey   = KEYS[2]
local start  = tonumber(ARGV[1])
local finish = tonumber(ARGV[2])

if redis.call("ZCARD", zkey) == 0 then
    local members = redis.call("SMEMBERS", skey)
    if members and #members > 0 then
        for _, m in ipairs(members) do
            redis.call("ZADD", zkey, 0, m)
        end
    end
end

return redis.call("ZRANGE", zkey, start, finish)
