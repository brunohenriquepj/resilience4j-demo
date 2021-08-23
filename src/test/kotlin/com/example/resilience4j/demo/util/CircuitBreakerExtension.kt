package com.example.resilience4j.demo.util

import io.github.resilience4j.circuitbreaker.CircuitBreaker

fun CircuitBreaker.log() {
    println(this.state)
    println(this.metrics.customToString())
}

private fun CircuitBreaker.Metrics.customToString(): String {
    val stringBuilder = StringBuilder()

    stringBuilder.appendLine("\ncircuitBreaker1.metrics: $this")
    stringBuilder.appendLine("failureRate: ${this.failureRate}")
    stringBuilder.appendLine("numberOfSuccessfulCalls: ${this.numberOfSuccessfulCalls}")
    stringBuilder.appendLine("numberOfFailedCalls: ${this.numberOfFailedCalls}")
    stringBuilder.appendLine("numberOfNotPermittedCalls: ${this.numberOfNotPermittedCalls}")
    stringBuilder.appendLine("numberOfBufferedCalls: ${this.numberOfBufferedCalls}")
    stringBuilder.appendLine("numberOfSlowCalls: ${this.numberOfSlowCalls}")
    stringBuilder.appendLine("numberOfSlowFailedCalls: ${this.numberOfSlowFailedCalls}")
    stringBuilder.appendLine("numberOfSlowSuccessfulCalls: ${this.numberOfSlowSuccessfulCalls}")
    stringBuilder.appendLine("slowCallRate: ${this.slowCallRate}")

    return stringBuilder.toString()
}
