-- Counts one sign-in attempt for an email, atomically.
--
-- KEYS[1] attempt counter   login:attempts:{email}
--
-- ARGV[1] attempts allowed per window
-- ARGV[2] window length in seconds
--
-- Returns 0 if the attempt may go ahead, otherwise the seconds until the window ends.

local attempts = redis.call('INCR', KEYS[1])
if attempts == 1 then
    redis.call('EXPIRE', KEYS[1], ARGV[2])
end
if attempts > tonumber(ARGV[1]) then
    return math.max(redis.call('TTL', KEYS[1]), 1)
end
return 0
