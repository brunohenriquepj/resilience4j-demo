package com.example.resilience4j.demo.util

class MockService {
    fun doError(): String {
        throw RuntimeException("ERROR!")
    }

    fun doSuccess(): String {
        return "SUCCESS!"
    }
}