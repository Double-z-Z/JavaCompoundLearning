package com.redis.lock.lock;

import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.core.io.ClassPathResource;
import jakarta.annotation.PostConstruct;
import java.util.List;

/**
 * Lua 脚本加载器
 */
public class LuaScripts {

    private RedisScript<Long> acquireLockScript;
    private RedisScript<Long> releaseLockScript;
    private RedisScript<Long> renewLockScript;

    @PostConstruct
    public void init() {
        acquireLockScript = RedisScript.of(
            new ClassPathResource("lua/acquire_lock.lua"), Long.class);
        releaseLockScript = RedisScript.of(
            new ClassPathResource("lua/release_lock.lua"), Long.class);
        renewLockScript = RedisScript.of(
            new ClassPathResource("lua/renew_lock.lua"), Long.class);
    }

    public RedisScript<Long> getAcquireLockScript() {
        return acquireLockScript;
    }

    public RedisScript<Long> getReleaseLockScript() {
        return releaseLockScript;
    }

    public RedisScript<Long> getRenewLockScript() {
        return renewLockScript;
    }

    public List<String> getAcquireKeys(String key) {
        return List.of(key);
    }

    public List<String> getReleaseKeys(String key) {
        return List.of(key);
    }

    public List<String> getRenewKeys(String key) {
        return List.of(key);
    }
}