-- Admits one purchase attempt for a deal, atomically.
--
-- KEYS[1] remaining stock            fs:{dealId}:stock
-- KEYS[2] units held per customer    fs:{dealId}:held   (hash: userId -> quantity)
-- KEYS[3] customer's attempt counter fs:{dealId}:attempts:{userId}
-- KEYS[4] withdrawn marker           fs:{dealId}:withdrawn
--
-- ARGV[1] the deal's total stock, used to seed KEYS[1] the first time
-- ARGV[2] userId
-- ARGV[3] quantity requested
-- ARGV[4] per-customer limit
-- ARGV[5] attempts allowed per window
-- ARGV[6] window length in seconds
-- ARGV[7] lifetime of the deal's keys in seconds
--
-- Returns the stock remaining after the reservation, or
--   -1 sold out, -2 customer limit reached, -3 too many attempts, -4 deal withdrawn.

if redis.call('EXISTS', KEYS[4]) == 1 then
    return -4
end

local attempts = redis.call('INCR', KEYS[3])
if attempts == 1 then
    redis.call('EXPIRE', KEYS[3], ARGV[6])
end
if attempts > tonumber(ARGV[5]) then
    return -3
end

redis.call('SET', KEYS[1], ARGV[1], 'NX', 'EX', ARGV[7])

local quantity = tonumber(ARGV[3])
local held = tonumber(redis.call('HGET', KEYS[2], ARGV[2]) or '0')
if held + quantity > tonumber(ARGV[4]) then
    return -2
end

local stock = tonumber(redis.call('GET', KEYS[1]))
if stock < quantity then
    return -1
end

redis.call('DECRBY', KEYS[1], quantity)
redis.call('HINCRBY', KEYS[2], ARGV[2], quantity)
redis.call('EXPIRE', KEYS[2], ARGV[7])
return stock - quantity
