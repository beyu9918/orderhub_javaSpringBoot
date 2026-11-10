-- KEYS: metadata, stock, claimed users, pending tokens, pending deadlines
-- ARGV: generation, userId, reservationToken, reservationTimeoutMs
-- 0 reserved, 1 duplicate, 2 sold out, 3 inactive, 4 rebuild required
if redis.call('HGET', KEYS[1], 'generation') ~= ARGV[1]
    or redis.call('EXISTS', KEYS[2]) == 0 or redis.call('EXISTS', KEYS[3]) == 0 then return 4 end
if redis.call('SISMEMBER', KEYS[3], ARGV[2]) == 1 then return 1 end
local clock = redis.call('TIME')
local now = tonumber(clock[1]) * 1000 + math.floor(tonumber(clock[2]) / 1000)
if redis.call('HGET', KEYS[1], 'active') ~= '1'
    or now < tonumber(redis.call('HGET', KEYS[1], 'start'))
    or now >= tonumber(redis.call('HGET', KEYS[1], 'expire')) then return 3 end
if tonumber(redis.call('GET', KEYS[2])) <= 0 then return 2 end
redis.call('DECR', KEYS[2])
redis.call('SADD', KEYS[3], ARGV[2])
redis.call('HSET', KEYS[4], ARGV[2], ARGV[3])
redis.call('ZADD', KEYS[5], now + tonumber(ARGV[4]), ARGV[2])
local ttl = redis.call('PTTL', KEYS[1])
redis.call('PEXPIRE', KEYS[4], ttl)
redis.call('PEXPIRE', KEYS[5], ttl)
return 0
