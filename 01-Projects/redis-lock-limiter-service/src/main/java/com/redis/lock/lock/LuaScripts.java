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
    private RedisScript<List> acquireReentrantScript;
    private RedisScript<Long> releaseReentrantScript;
    private RedisScript<Long> renewReentrantScript;

    @PostConstruct
    public void init() {
        acquireLockScript = RedisScript.of(
            new ClassPathResource("lua/acquire_lock.lua"), Long.class);
        releaseLockScript = RedisScript.of(
            new ClassPathResource("lua/release_lock.lua"), Long.class);
        renewLockScript = RedisScript.of(
            new ClassPathResource("lua/renew_lock.lua"), Long.class);
        acquireReentrantScript = RedisScript.of(
            new ClassPathResource("lua/acquire_reentrant_lock.lua"), List.class);
        releaseReentrantScript = RedisScript.of(
            new ClassPathResource("lua/release_reentrant_lock.lua"), Long.class);
        renewReentrantScript = RedisScript.of(
            new ClassPathResource("lua/renew_reentrant_lock.lua"), Long.class);
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

    public RedisScript<List> getAcquireReentrantScript() {
        return acquireReentrantScript;
    }

    public RedisScript<Long> getReleaseReentrantScript() {
        return releaseReentrantScript;
    }

    public RedisScript<Long> getRenewReentrantScript() {
        return renewReentrantScript;
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