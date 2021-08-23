# [Resilience4j](https://resilience4j.readme.io/) demo with Kotlin

[![CI](https://github.com/brunohenriquepj/resilience4j-demo/actions/workflows/ci.yml/badge.svg)](https://github.com/brunohenriquepj/resilience4j-demo/actions/workflows/ci.yml)

---

# In progress... 🏗️

## TODO

- [x] Circuit Breaker
- [ ] Bulkhead
- [ ] Rate Limiter
- [ ] Retry
- [ ] Time Limiter
- [ ] Cache

---

## Setup Java with [SDKMAN!](https://github.com/sdkman/sdkman-cli)

```console
sdk env install
```

---

## Build

```console
./gradlew build --exclude-task test
```

---

## Test

```console
./gradlew test --stacktrace
```
