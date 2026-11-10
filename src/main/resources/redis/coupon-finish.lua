-- ARGV: generation, userId, reservationToken, committed(1/0)
-- A retry or stale owner cannot compensate another request's reservation.
if redis.call('HGET', KEYS[1], 'generation') ~= ARGV[1] then return -1 end
if redis.call('HGET', KEYS[4], ARGV[2]) ~= ARGV[3] then return 0 end
if ARGV[4] == '0' then
    if redis.call('EXISTS', KEYS[2]) == 0 or redis.call('EXISTS', KEYS[3]) == 0 then
        redis.call('DEL', unpack(KEYS))
        return -1
    end
    redis.call('SREM', KEYS[3], ARGV[2])
    redis.call('INCR', KEYS[2])
end
redis.call('HDEL', KEYS[4], ARGV[2])
redis.call('ZREM', KEYS[5], ARGV[2])
return 1
