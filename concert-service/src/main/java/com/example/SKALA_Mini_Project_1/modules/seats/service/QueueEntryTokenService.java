package com.example.SKALA_Mini_Project_1.modules.seats.service;

import com.example.SKALA_Mini_Project_1.global.redis.RedisKeyGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class QueueEntryTokenService {

    private static final String CONSUME_ENTRY_TOKEN_SCRIPT = """
            local value = redis.call('GET', KEYS[1])
            if not value then
                return nil
            end
            redis.call('DEL', KEYS[1])
            return value
            """;

    private final RedisTemplate<String, String> redisTemplate;

    public String consumeEntryToken(String entryToken) {
        if (entryToken == null || entryToken.isBlank()) {
            return null;
        }

        DefaultRedisScript<String> script = new DefaultRedisScript<>();
        script.setScriptText(CONSUME_ENTRY_TOKEN_SCRIPT);
        script.setResultType(String.class);

        return redisTemplate.execute(script, List.of(RedisKeyGenerator.seatEntryKey(entryToken)));
    }
}
