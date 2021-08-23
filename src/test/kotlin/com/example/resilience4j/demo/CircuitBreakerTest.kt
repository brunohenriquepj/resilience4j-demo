package com.example.resilience4j.demo

import com.example.resilience4j.demo.util.MockService
import io.github.resilience4j.circuitbreaker.CircuitBreaker.State
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.vavr.control.Try
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.FileNotFoundException
import java.time.Duration
import java.util.*

class CircuitBreakerTest {
    private val slidingWindowSize = 7
    private val durationInOpenState = Duration.ofMillis(1000)
    private val durationInHalfOpenState = Duration.ofMillis(1500)
    private val numberOfCallsInHalfOpenState = 4

    private val config: CircuitBreakerConfig = CircuitBreakerConfig.custom()
        .failureRateThreshold(50F)
        .waitDurationInOpenState(durationInOpenState)
        .maxWaitDurationInHalfOpenState(durationInHalfOpenState)
        .permittedNumberOfCallsInHalfOpenState(numberOfCallsInHalfOpenState)
        .slidingWindowSize(slidingWindowSize)
        .recordExceptions(RuntimeException::class.java)
        .build()

    private val registry: CircuitBreakerRegistry = CircuitBreakerRegistry.of(config)

    @Test
    fun `circuit breaker should switch between open, half open and closed`() {
        val mockService = MockService()
        val circuitBreaker = registry.circuitBreaker(UUID.randomUUID().toString())

        val supplierSuccess = circuitBreaker.decorateCheckedSupplier(mockService::doSuccess)
        val supplierError = circuitBreaker.decorateCheckedSupplier(mockService::doError)

        (1..7).forEach {
            Try.of(supplierSuccess)

            assertEquals(State.CLOSED, circuitBreaker.state)
            assertEquals(it, circuitBreaker.metrics.numberOfBufferedCalls)
            assertEquals(0, circuitBreaker.metrics.numberOfFailedCalls)
            assertEquals(0, circuitBreaker.metrics.numberOfNotPermittedCalls)
            assertEquals(it, circuitBreaker.metrics.numberOfSuccessfulCalls)
            assertEquals(0, circuitBreaker.metrics.numberOfSlowFailedCalls)
        }

        repeat(15) {
            Try.of(supplierSuccess)

            assertEquals(State.CLOSED, circuitBreaker.state)
            assertEquals(7, circuitBreaker.metrics.numberOfBufferedCalls)
            assertEquals(0, circuitBreaker.metrics.numberOfFailedCalls)
            assertEquals(0, circuitBreaker.metrics.numberOfNotPermittedCalls)
            assertEquals(7, circuitBreaker.metrics.numberOfSuccessfulCalls)
            assertEquals(0, circuitBreaker.metrics.numberOfSlowFailedCalls)
        }

        var numberOfSuccessfulCalls = 6
        (1..3).forEach {
            Try.of(supplierError)

            assertEquals(State.CLOSED, circuitBreaker.state)
            assertEquals(7, circuitBreaker.metrics.numberOfBufferedCalls)
            assertEquals(it, circuitBreaker.metrics.numberOfFailedCalls)
            assertEquals(0, circuitBreaker.metrics.numberOfNotPermittedCalls)
            assertEquals(numberOfSuccessfulCalls--, circuitBreaker.metrics.numberOfSuccessfulCalls)
            assertEquals(0, circuitBreaker.metrics.numberOfSlowFailedCalls)
        }

        Try.of(supplierError)

        assertEquals(State.OPEN, circuitBreaker.state)
        assertEquals(7, circuitBreaker.metrics.numberOfBufferedCalls)
        assertEquals(4, circuitBreaker.metrics.numberOfFailedCalls)
        assertEquals(0, circuitBreaker.metrics.numberOfNotPermittedCalls)
        assertEquals(numberOfSuccessfulCalls, circuitBreaker.metrics.numberOfSuccessfulCalls)
        assertEquals(0, circuitBreaker.metrics.numberOfSlowFailedCalls)

        (1..15L).forEach {
            Try.of(supplierError)

            assertEquals(State.OPEN, circuitBreaker.state)
            assertEquals(7, circuitBreaker.metrics.numberOfBufferedCalls)
            assertEquals(4, circuitBreaker.metrics.numberOfFailedCalls)
            assertEquals(it, circuitBreaker.metrics.numberOfNotPermittedCalls)
            assertEquals(numberOfSuccessfulCalls, circuitBreaker.metrics.numberOfSuccessfulCalls)
            assertEquals(0, circuitBreaker.metrics.numberOfSlowFailedCalls)
        }

        var numberOfNotPermittedCalls = 16L
        repeat(15) {
            Try.of(supplierSuccess)

            assertEquals(State.OPEN, circuitBreaker.state)
            assertEquals(7, circuitBreaker.metrics.numberOfBufferedCalls)
            assertEquals(4, circuitBreaker.metrics.numberOfFailedCalls)
            assertEquals(numberOfNotPermittedCalls++, circuitBreaker.metrics.numberOfNotPermittedCalls)
            assertEquals(numberOfSuccessfulCalls, circuitBreaker.metrics.numberOfSuccessfulCalls)
            assertEquals(0, circuitBreaker.metrics.numberOfSlowFailedCalls)
        }

        Thread.sleep(durationInOpenState.toMillis())
        (1..3).forEach {
            Try.of(supplierSuccess)

            assertEquals(State.HALF_OPEN, circuitBreaker.state)
            assertEquals(it, circuitBreaker.metrics.numberOfBufferedCalls)
            assertEquals(0, circuitBreaker.metrics.numberOfFailedCalls)
            assertEquals(0, circuitBreaker.metrics.numberOfNotPermittedCalls)
            assertEquals(it, circuitBreaker.metrics.numberOfSuccessfulCalls)
            assertEquals(0, circuitBreaker.metrics.numberOfSlowFailedCalls)
        }

        (0..7).forEach {
            Try.of(supplierSuccess)

            assertEquals(State.CLOSED, circuitBreaker.state)
            assertEquals(it, circuitBreaker.metrics.numberOfBufferedCalls)
            assertEquals(0, circuitBreaker.metrics.numberOfFailedCalls)
            assertEquals(0, circuitBreaker.metrics.numberOfNotPermittedCalls)
            assertEquals(it, circuitBreaker.metrics.numberOfSuccessfulCalls)
            assertEquals(0, circuitBreaker.metrics.numberOfSlowFailedCalls)
        }

        repeat(50) {
            Try.of(supplierSuccess)

            assertEquals(State.CLOSED, circuitBreaker.state)
            assertEquals(7, circuitBreaker.metrics.numberOfBufferedCalls)
            assertEquals(0, circuitBreaker.metrics.numberOfFailedCalls)
            assertEquals(0, circuitBreaker.metrics.numberOfNotPermittedCalls)
            assertEquals(7, circuitBreaker.metrics.numberOfSuccessfulCalls)
            assertEquals(0, circuitBreaker.metrics.numberOfSlowFailedCalls)
        }
    }

    @Test
    fun `circuit breaker should change from closed to open when error calls count is less than sliding window size`() {
        val mockService = MockService()
        val circuitBreaker = registry.circuitBreaker(UUID.randomUUID().toString())

        val supplierError = circuitBreaker.decorateCheckedSupplier(mockService::doError)
        val supplierSuccess = circuitBreaker.decorateCheckedSupplier(mockService::doSuccess)

        Try.of(supplierSuccess)

        assertEquals(State.CLOSED, circuitBreaker.state)
        assertEquals(1, circuitBreaker.metrics.numberOfBufferedCalls)
        assertEquals(0, circuitBreaker.metrics.numberOfFailedCalls)
        assertEquals(1, circuitBreaker.metrics.numberOfSuccessfulCalls)
        assertEquals(0, circuitBreaker.metrics.numberOfNotPermittedCalls)
        assertEquals(0, circuitBreaker.metrics.numberOfSlowFailedCalls)

        (2..6).forEach {
            Try.of(supplierError)

            assertEquals(State.CLOSED, circuitBreaker.state)
            assertEquals(it, circuitBreaker.metrics.numberOfBufferedCalls)
            assertEquals(it - 1, circuitBreaker.metrics.numberOfFailedCalls)
            assertEquals(1, circuitBreaker.metrics.numberOfSuccessfulCalls)
            assertEquals(0, circuitBreaker.metrics.numberOfNotPermittedCalls)
            assertEquals(0, circuitBreaker.metrics.numberOfSlowFailedCalls)
        }

        var numberOfNotPermittedCalls = 0L
        repeat(50) {
            Try.of(supplierError)

            assertEquals(State.OPEN, circuitBreaker.state)
            assertEquals(slidingWindowSize, circuitBreaker.metrics.numberOfBufferedCalls)
            assertEquals(6, circuitBreaker.metrics.numberOfFailedCalls)
            assertEquals(1, circuitBreaker.metrics.numberOfSuccessfulCalls)
            assertEquals(numberOfNotPermittedCalls++, circuitBreaker.metrics.numberOfNotPermittedCalls)
            assertEquals(0, circuitBreaker.metrics.numberOfSlowFailedCalls)
        }
    }

    @Test
    fun `circuit breaker should change from half open to closed when only success call occurred after duration in open state`() {
        val mockService = MockService()
        val circuitBreaker = registry.circuitBreaker(UUID.randomUUID().toString())

        val supplierError = circuitBreaker.decorateCheckedSupplier(mockService::doError)
        val supplierSuccess = circuitBreaker.decorateCheckedSupplier(mockService::doSuccess)

        val errorCount = 41
        repeat(errorCount) { Try.of(supplierError) }

        var numberOfNotPermittedCalls = errorCount - slidingWindowSize + 1L
        repeat(2) {
            Try.of(supplierSuccess)
            assertEquals(State.OPEN, circuitBreaker.state)
            assertEquals(slidingWindowSize, circuitBreaker.metrics.numberOfBufferedCalls)
            assertEquals(slidingWindowSize, circuitBreaker.metrics.numberOfFailedCalls)
            assertEquals(numberOfNotPermittedCalls++, circuitBreaker.metrics.numberOfNotPermittedCalls)
            assertEquals(0, circuitBreaker.metrics.numberOfSuccessfulCalls)
            assertEquals(0, circuitBreaker.metrics.numberOfSlowFailedCalls)
        }

        Thread.sleep(durationInOpenState.toMillis())
        (1 until numberOfCallsInHalfOpenState).forEach {
            Try.of(supplierSuccess)

            assertEquals(State.HALF_OPEN, circuitBreaker.state)
            assertEquals(it, circuitBreaker.metrics.numberOfBufferedCalls)
            assertEquals(0, circuitBreaker.metrics.numberOfFailedCalls)
            assertEquals(0, circuitBreaker.metrics.numberOfNotPermittedCalls)
            assertEquals(it, circuitBreaker.metrics.numberOfSuccessfulCalls)
            assertEquals(0, circuitBreaker.metrics.numberOfSlowFailedCalls)
        }

        (0..6).forEach {
            Try.of(supplierSuccess)

            assertEquals(State.CLOSED, circuitBreaker.state)
            assertEquals(it, circuitBreaker.metrics.numberOfBufferedCalls)
            assertEquals(0, circuitBreaker.metrics.numberOfFailedCalls)
            assertEquals(0, circuitBreaker.metrics.numberOfNotPermittedCalls)
            assertEquals(it, circuitBreaker.metrics.numberOfSuccessfulCalls)
            assertEquals(0, circuitBreaker.metrics.numberOfSlowFailedCalls)
        }

        repeat(20) {
            Try.of(supplierSuccess)

            assertEquals(State.CLOSED, circuitBreaker.state)
            assertEquals(7, circuitBreaker.metrics.numberOfBufferedCalls)
            assertEquals(0, circuitBreaker.metrics.numberOfFailedCalls)
            assertEquals(0, circuitBreaker.metrics.numberOfNotPermittedCalls)
            assertEquals(7, circuitBreaker.metrics.numberOfSuccessfulCalls)
            assertEquals(0, circuitBreaker.metrics.numberOfSlowFailedCalls)
        }
    }

    @Test
    fun `circuit breaker should change be always closed when only success calls occurred`() {
        val mockService = MockService()
        val circuitBreaker = registry.circuitBreaker(UUID.randomUUID().toString())

        val supplierSuccess = circuitBreaker.decorateCheckedSupplier(mockService::doSuccess)

        (1..7).forEach {
            Try.of(supplierSuccess)
            assertEquals(State.CLOSED, circuitBreaker.state)
            assertEquals(it, circuitBreaker.metrics.numberOfBufferedCalls)
            assertEquals(0, circuitBreaker.metrics.numberOfFailedCalls)
            assertEquals(0, circuitBreaker.metrics.numberOfNotPermittedCalls)
            assertEquals(it, circuitBreaker.metrics.numberOfSuccessfulCalls)
            assertEquals(0, circuitBreaker.metrics.numberOfSlowFailedCalls)
        }

        repeat(50) {
            Try.of(supplierSuccess)
            assertEquals(State.CLOSED, circuitBreaker.state)
            assertEquals(7, circuitBreaker.metrics.numberOfBufferedCalls)
            assertEquals(0, circuitBreaker.metrics.numberOfFailedCalls)
            assertEquals(0, circuitBreaker.metrics.numberOfNotPermittedCalls)
            assertEquals(7, circuitBreaker.metrics.numberOfSuccessfulCalls)
            assertEquals(0, circuitBreaker.metrics.numberOfSlowFailedCalls)
        }
    }

    @Test
    fun `circuit breaker should change from half open to open when an error call occurred after duration in open state and duration in half open state`() {
        val mockService = MockService()
        val circuitBreaker = registry.circuitBreaker(UUID.randomUUID().toString())

        val supplierError = circuitBreaker.decorateCheckedSupplier(mockService::doError)
        val supplierSuccess = circuitBreaker.decorateCheckedSupplier(mockService::doSuccess)

        val errorCount = 41
        repeat(errorCount) { Try.of(supplierError) }

        var numberOfNotPermittedCalls = errorCount - slidingWindowSize + 1L
        repeat(2) {
            Try.of(supplierSuccess)
            assertEquals(State.OPEN, circuitBreaker.state)
            assertEquals(slidingWindowSize, circuitBreaker.metrics.numberOfBufferedCalls)
            assertEquals(slidingWindowSize, circuitBreaker.metrics.numberOfFailedCalls)
            assertEquals(numberOfNotPermittedCalls++, circuitBreaker.metrics.numberOfNotPermittedCalls)
            assertEquals(0, circuitBreaker.metrics.numberOfSuccessfulCalls)
            assertEquals(0, circuitBreaker.metrics.numberOfSlowFailedCalls)
        }

        Thread.sleep(durationInOpenState.toMillis())

        Try.of(supplierSuccess)

        assertEquals(State.HALF_OPEN, circuitBreaker.state)
        assertEquals(1, circuitBreaker.metrics.numberOfBufferedCalls)
        assertEquals(0, circuitBreaker.metrics.numberOfFailedCalls)
        assertEquals(0, circuitBreaker.metrics.numberOfNotPermittedCalls)
        assertEquals(1, circuitBreaker.metrics.numberOfSuccessfulCalls)
        assertEquals(0, circuitBreaker.metrics.numberOfSlowFailedCalls)

        Thread.sleep(durationInHalfOpenState.toMillis())

        (1..20L).forEach {
            Try.of(supplierError)

            assertEquals(State.OPEN, circuitBreaker.state)
            assertEquals(1, circuitBreaker.metrics.numberOfBufferedCalls)
            assertEquals(0, circuitBreaker.metrics.numberOfFailedCalls)
            assertEquals(it, circuitBreaker.metrics.numberOfNotPermittedCalls)
            assertEquals(1, circuitBreaker.metrics.numberOfSuccessfulCalls)
            assertEquals(0, circuitBreaker.metrics.numberOfSlowFailedCalls)
        }
    }

    @Test
    fun `circuit breaker should be closed when error calls is ignored`() {
        val mockService = MockService()
        val circuitBreaker = registry.circuitBreaker(UUID.randomUUID().toString())

        val supplierSuccess = circuitBreaker.decorateCheckedSupplier(mockService::doSuccess)
        val supplierError = circuitBreaker.decorateCheckedSupplier(mockService::doError)
        val supplierUnexpectedError = circuitBreaker.decorateCheckedSupplier { throw FileNotFoundException() }

        (1..7).forEach {
            Try.of(supplierUnexpectedError)

            assertEquals(State.CLOSED, circuitBreaker.state)
            assertEquals(it, circuitBreaker.metrics.numberOfBufferedCalls)
            assertEquals(0, circuitBreaker.metrics.numberOfFailedCalls)
            assertEquals(0, circuitBreaker.metrics.numberOfNotPermittedCalls)
            assertEquals(it, circuitBreaker.metrics.numberOfSuccessfulCalls)
            assertEquals(0, circuitBreaker.metrics.numberOfSlowFailedCalls)
        }

        repeat(50) {
            Try.of(supplierUnexpectedError)

            assertEquals(State.CLOSED, circuitBreaker.state)
            assertEquals(7, circuitBreaker.metrics.numberOfBufferedCalls)
            assertEquals(0, circuitBreaker.metrics.numberOfFailedCalls)
            assertEquals(0, circuitBreaker.metrics.numberOfNotPermittedCalls)
            assertEquals(7, circuitBreaker.metrics.numberOfSuccessfulCalls)
            assertEquals(0, circuitBreaker.metrics.numberOfSlowFailedCalls)
        }

        Try.of(supplierSuccess)

        assertEquals(State.CLOSED, circuitBreaker.state)
        assertEquals(7, circuitBreaker.metrics.numberOfBufferedCalls)
        assertEquals(0, circuitBreaker.metrics.numberOfFailedCalls)
        assertEquals(0, circuitBreaker.metrics.numberOfNotPermittedCalls)
        assertEquals(7, circuitBreaker.metrics.numberOfSuccessfulCalls)
        assertEquals(0, circuitBreaker.metrics.numberOfSlowFailedCalls)

        Try.of(supplierError)

        assertEquals(State.CLOSED, circuitBreaker.state)
        assertEquals(7, circuitBreaker.metrics.numberOfBufferedCalls)
        assertEquals(1, circuitBreaker.metrics.numberOfFailedCalls)
        assertEquals(0, circuitBreaker.metrics.numberOfNotPermittedCalls)
        assertEquals(6, circuitBreaker.metrics.numberOfSuccessfulCalls)
        assertEquals(0, circuitBreaker.metrics.numberOfSlowFailedCalls)
    }

    @Test
    fun `circuit breaker should be open when error class inherits from the record error`() {
        val circuitBreaker = registry.circuitBreaker(UUID.randomUUID().toString())

        val supplierError = circuitBreaker.decorateCheckedSupplier { throw IllegalArgumentException() }

        Try.of(supplierError)
        Try.of(supplierError)

        assertEquals(State.CLOSED, circuitBreaker.state)
        assertEquals(2, circuitBreaker.metrics.numberOfBufferedCalls)
        assertEquals(2, circuitBreaker.metrics.numberOfFailedCalls)
        assertEquals(0, circuitBreaker.metrics.numberOfNotPermittedCalls)
        assertEquals(0, circuitBreaker.metrics.numberOfSuccessfulCalls)
        assertEquals(0, circuitBreaker.metrics.numberOfSlowFailedCalls)
    }
}
