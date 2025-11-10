package com.github.senocak.skdl

import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Lock
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.runApplication
import org.springframework.context.event.EventListener
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.integration.jdbc.lock.JdbcLockRegistry
import org.springframework.integration.jdbc.lock.LockRepository
import org.springframework.integration.redis.util.RedisLockRegistry
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

fun main(args: Array<String>) {
    runApplication<SpringKotlinDistributedLockApplication>(*args)
}

@SpringBootApplication
@RestController
@RequestMapping(value = ["/lock"])
class SpringKotlinDistributedLockApplication(
    private val lockTableRepository: LockTableRepository,
    private val lockRepository: LockRepository,
    private val redisConnectionFactory: RedisConnectionFactory,
) {
    private val log: Logger = LoggerFactory.getLogger(javaClass)
    private val jdbcLockRegistry: JdbcLockRegistry = JdbcLockRegistry(lockRepository)
    private val redisLockRegistry: RedisLockRegistry = RedisLockRegistry(redisConnectionFactory, "myLockKey")

    @EventListener(value = [ApplicationReadyEvent::class])
    fun init(event: ApplicationReadyEvent) {
        lockTableRepository.deleteAll()
        lockTableRepository.saveAll(listOf(
            LockTable().also { it.name = "anil1"; it.amount = 0L },
            LockTable().also { it.name = "anil2"; it.amount = 0L }
        ))
        log.info("DB populating is completed...${event.timeTaken.seconds} seconds")
    }

    @GetMapping(value = ["jdbc"])
    fun getJDBC(@RequestParam name: String): String {
        val obtain: Lock = jdbcLockRegistry.obtain("myLockKey")
        val tryLock: Boolean = obtain.tryLock(5, TimeUnit.SECONDS) // Try to get lock for 5 seconds
        if (tryLock) {
            try {
                inc(name = name)
                Thread.sleep(10 * 1_000)
                log.info("Waited 10sec")
            } finally {
                obtain.unlock()
            }
            return "Lock is released after operation"
        }
        return "Lock is not available"
    }

    @GetMapping(value = ["redis"])
    fun getRedis(@RequestParam name: String): String {
        val obtain: Lock = redisLockRegistry.obtain("myLockKey")
        val tryLock: Boolean = obtain.tryLock()
        if (tryLock) {
            try {
                inc(name = name)
                Thread.sleep(10 * 1_000)
                log.info("Waited 10sec")
            } finally {
                obtain.unlock()
            }
            return "Lock is released after operation"
        }
        return "Lock is not available"
    }

    //@Transactional
    fun inc(name: String) {
        var findByName: LockTable? = lockTableRepository.findByName(name = name)
        if (findByName != null) {
            findByName.amount = findByName.amount?.plus(1)
            findByName = lockTableRepository.save(findByName)
            log.info("[${Thread.currentThread().name}] Amount is incremented for ${findByName.name}")
        }
    }
}
