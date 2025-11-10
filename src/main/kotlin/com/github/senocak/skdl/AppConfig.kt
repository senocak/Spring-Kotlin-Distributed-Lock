package com.github.senocak.skdl

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.data.redis.RedisProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisPassword
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.integration.jdbc.lock.DefaultLockRepository
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import javax.sql.DataSource

@Configuration
class AppConfig(
    private val redisProperties: RedisProperties
){
    private val log: Logger = LoggerFactory.getLogger(javaClass)

    @Bean
    fun jedisPool(): JedisPool {
        log.info("RedisConfig: host=${redisProperties.host}, port=${redisProperties.port}, password=${redisProperties.password}, timeout=${redisProperties.timeout}")
        return JedisPool(
            JedisPoolConfig(),
            redisProperties.host,
            redisProperties.port,
            redisProperties.timeout.seconds.toInt(),
            if(!redisProperties.password.isNullOrEmpty()) redisProperties.password else null,
            redisProperties.database
        )
    }

    @Bean
    fun jedisConnectionFactory(): LettuceConnectionFactory {
        val redisStandaloneConfiguration = RedisStandaloneConfiguration()
        redisStandaloneConfiguration.hostName = redisProperties.host
        if (!redisProperties.password.isNullOrEmpty())
            redisStandaloneConfiguration.password = RedisPassword.of(redisProperties.password)
        redisStandaloneConfiguration.port = redisProperties.port
        return LettuceConnectionFactory(redisStandaloneConfiguration)
    }

    @Bean
    fun redisTemplate(): RedisTemplate<String, Any> =
        RedisTemplate<String, Any>()
            .apply { this.connectionFactory = jedisConnectionFactory() }

    @Bean
    fun defaultLockRepository(dataSource: DataSource): DefaultLockRepository =
        DefaultLockRepository(dataSource).also { it.setTimeToLive(1 * 1_000) }
}