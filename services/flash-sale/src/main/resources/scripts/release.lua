-- Gives back what a purchase held, at most once per order.
--
-- KEYS[1] remaining stock            fs:{dealId}:stock
-- KEYS[2] units held per customer    fs:{dealId}:held
-- KEYS[3] release marker             fs:{dealId}:released:{orderId}
--
-- ARGV[1] userId
-- ARGV[2] quantity
-- ARGV[3] '1' to return the stock, '0' to only return the customer's allowance
-- ARGV[4] lifetime of the marker in seconds
--
-- Returns 1 if released, 0 if this order was already released.

if not redis.call('SET', KEYS[3], '1', 'NX', 'EX', ARGV[4]) then
    return 0
end

local quantity = tonumber(ARGV[2])
local held = tonumber(redis.call('HGET', KEYS[2], ARGV[1]) or '0')
if held > 0 then
    redis.call('HINCRBY', KEYS[2], ARGV[1], -math.min(held, quantity))
end

-- If the stock key has expired the sale is over; recreating it would only resurrect stale stock.
if ARGV[3] == '1' and redis.call('EXISTS', KEYS[1]) == 1 then
    redis.call('INCRBY', KEYS[1], quantity)
end
return 1
