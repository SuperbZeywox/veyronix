-- ATOMICITY:
--   This whole script runs atomically on a single Redis node.
--   (Note: On Redis Cluster, scripts must only touch keys in one slot.)
--
-- KEYS:
--   1) product:<id>               (HASH)   -- product hash
--   2) ver:product:<id>           (STRING) -- product version counter
--
-- ARGV:
--   1) id                         (STRING) -- product id (also used as set/zset member)
--   2) newStock                   (STRING) -- new stock value (stringified integer)
--
-- RETURNS:
--   number (the new stock value)
--
-- FIELDS USED IN HASH:
--   - category (raw string, may be blank)
--   - stock    (stored as string; parsed when needed)
--
-- INDEX/VERSION KEY NAMING (constructed here to avoid drift with Java):
--   - idx:category:<catNorm>          (SET)
--   - zidx:category:<catNorm>         (ZSET)
--   - idx:category:in:<catNorm>       (SET)
--   - zidx:category:in:<catNorm>      (ZSET)
--   - idx:category:out:<catNorm>      (SET)
--   - zidx:category:out:<catNorm>     (ZSET)
--   - ver:category:<catNorm>          (STRING)
--   - ver:category:in:<catNorm>       (STRING)
--   - ver:category:out:<catNorm>      (STRING)
--
-- NORMALIZATION RULES:
--   - trim whitespace
--   - empty => "uncategorized"
--   - lowercase
--   - collapse spaces to single '-' (dash)
--   NOTE: Keep this in strict lockstep with Java Keys.normalize(...).

local function trim(s) if not s then return "" end return (s:gsub("^%s*(.-)%s*$","%1")) end
local function isBlank(s) return trim(s) == "" end
local function normalize(cat)
    cat = trim(cat)
    if cat == "" then return "uncategorized" end
    cat = string.lower(cat)
    cat = (cat:gsub("%s+","-"))
    return cat
end

local id       = ARGV[1]
local newStock = tonumber(ARGV[2] or "0") or 0

-- 1) Existence check
if redis.call("EXISTS", KEYS[1]) == 0 then
    return {err="NOT_FOUND"}
end

-- 2) Read current state
local catRaw   = redis.call("HGET", KEYS[1], "category")
local catNorm  = normalize(catRaw)
local oldStock = tonumber(redis.call("HGET", KEYS[1], "stock") or "0") or 0

-- 3) Update stock
redis.call("HSET", KEYS[1], "stock", tostring(newStock))

-- 4) Derive category-scoped keys *from current state* (race-safe)
local idxIn      = "idx:category:in:"   .. catNorm
local zidxIn     = "zidx:category:in:"  .. catNorm
local idxOut     = "idx:category:out:"  .. catNorm
local zidxOut    = "zidx:category:out:" .. catNorm

-- 5) Adjust membership
if newStock > 0 then
    redis.call("SADD", idxIn,  id); redis.call("ZADD", zidxIn,  0, id)
    redis.call("SREM", idxOut, id); redis.call("ZREM", zidxOut,    id)
else
    redis.call("SADD", idxOut, id); redis.call("ZADD", zidxOut, 0, id)
    redis.call("SREM", idxIn,  id); redis.call("ZREM", zidxIn,    id)
end

-- 6) Version bumps
--    - Always bump product version
--    - If category not blank:
--        - bump category version
--        - if stock changed: bump BOTH bucket versions
--        - else: bump the ACTIVE bucket version
redis.call("INCR", KEYS[2])  -- ver:product:<id>

if not isBlank(catRaw) then
    local vCat = "ver:category:"      .. catNorm
    local vIn  = "ver:category:in:"   .. catNorm
    local vOut = "ver:category:out:"  .. catNorm

    redis.call("INCR", vCat)
    if oldStock ~= newStock then
        redis.call("INCR", vIn); redis.call("INCR", vOut)
    else
        if newStock > 0 then redis.call("INCR", vIn) else redis.call("INCR", vOut) end
    end
end

return newStock
