package com.meolist.domain.common

/** Runs several repository writes atomically. */
interface TransactionRunner {
    suspend fun <T> run(block: suspend () -> T): T
}
