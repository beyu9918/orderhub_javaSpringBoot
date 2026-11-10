-- All keys have the same {couponId} hash tag.
-- ARGV: generation, remaining, active, startMs, expireMs, ttlMs, JSON claimed user IDs, expected epoch
-- Admin edits/invalidations advance the epoch, fencing a stale database snapshot.
if redis.call('GET', KEYS[6]) ~= ARGV[8] then return -1 end
if redis.call('EXISTS', KEYS[1]) == 1 and redis.call('EXISTS', KEYS[2]) == 1
    and redis.call('EXISTS', KEYS[3]) == 1 then return 0 end
for i = 1, 5 do redis.call('DEL', KEYS[i]) end
redis.call('HSET', KEYS[1], 'generation', ARGV[1], 'active', ARGV[3], 'start', ARGV[4], 'expire', ARGV[5])
redis.call('SET', KEYS[2], ARGV[2])
redis.call('SADD', KEYS[3], '__initialized__')
local users = cjson.decode(ARGV[7])
for _, user in ipairs(users) do redis.call('SADD', KEYS[3], tostring(user)) end
for i = 1, 3 do redis.call('PEXPIRE', KEYS[i], ARGV[6]) end
return 1
