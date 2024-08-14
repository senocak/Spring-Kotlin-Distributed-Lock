package com.github.senocak.skdl

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Lock
import javax.sql.DataSource
import org.hibernate.annotations.UuidGenerator
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.EventListener
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.repository.query.Param
import org.springframework.integration.jdbc.lock.DefaultLockRepository
import org.springframework.integration.jdbc.lock.JdbcLockRegistry
import org.springframework.integration.jdbc.lock.LockRepository
import org.springframework.integration.redis.util.RedisLockRegistry
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig

fun main(args: Array<String>) {
    runApplication<SpringKotlinDistributedLockApplication>(*args)
}

@Entity
@Table(name = "locks")
class LockTable {
    @Id
    @UuidGenerator(style = UuidGenerator.Style.RANDOM)
    @Column(name = "id", updatable = false, nullable = false)
    var id: String? = null

    var name: String? = null

    var amount: Long? = null
}

interface LockTableRepository: JpaRepository<LockTable, String> {
    fun findByName(@Param("name") name: String): LockTable?
}

@Configuration
class RedisConfig {
    private val log: Logger = LoggerFactory.getLogger(javaClass)

    @Value("\${app.redis.host}")
    private var host: String? = null

    @Value("\${app.redis.port}")
    private var port: Int = 0

    @Value("\${app.redis.password}")
    private var password: String? = null

    @Value("\${app.redis.timeout}")
    private var timeout: Int = 0

    @Value("\${app.redis.database}")
    private var database: Int = 0

    @Bean
    fun jedisPool(): JedisPool {
        log.debug("RedisConfig: host=$host, port=$port, password=$password, timeout=$timeout")
        return JedisPool(JedisPoolConfig(), host, port, timeout, password, database)
    }

    /**
     * Create JedisPool
     * @return JedisPool
     */
    val jedisPool: JedisPool?
        get() = Companion.jedisPool

    @Bean
    fun redisConnectionFactory(): LettuceConnectionFactory {
        val redisStandaloneConfiguration = RedisStandaloneConfiguration()
        redisStandaloneConfiguration.hostName = host!!
        redisStandaloneConfiguration.setPassword(password)
        redisStandaloneConfiguration.port = port
        return LettuceConnectionFactory(redisStandaloneConfiguration)
    }

    companion object {
        private var jedisPool: JedisPool? = null
    }
}

@SpringBootApplication
class SpringKotlinDistributedLockApplication {
    @Bean
    fun defaultLockRepository(dataSource: DataSource): DefaultLockRepository =
        DefaultLockRepository(dataSource).also { it.setTimeToLive(1 * 1_000) }
}

@RestController
@RequestMapping("/lock")
class LockController(
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
        var lockTable1: LockTable = lockTableRepository.save(LockTable().also { it.name = "anil1"; it.amount = 0L })
        var lockTable2: LockTable = lockTableRepository.save(LockTable().also { it.name = "anil2"; it.amount = 0L })
        log.info("DB populating is completed...")
    }

    @GetMapping("jdbc")
    fun getJDBC(@RequestParam name: String) {
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
        } else {
            log.error("Lock is not available")
        }
    }

    @GetMapping("redis")
    fun getRedis(@RequestParam name: String) {
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
        } else {
            log.error("Lock is not available")
        }
    }

    //@Transactional
    fun inc(name: String) {
        var findByName: LockTable? = lockTableRepository.findByName(name = name)
        if (findByName != null) {
            findByName.amount = findByName.amount?.plus(1)
            findByName = lockTableRepository.save(findByName)
            log.info("[${Thread.currentThread().name}]Amount is incremented for ${findByName.name}")
        }
    }
}
