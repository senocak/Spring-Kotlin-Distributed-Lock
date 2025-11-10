package com.github.senocak.skdl

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.query.Param

interface LockTableRepository: JpaRepository<LockTable, String> {
    fun findByName(@Param(value = "name") name: String): LockTable?
}