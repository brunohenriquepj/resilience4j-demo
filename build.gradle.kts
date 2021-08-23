import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.5.10"
    application
    id("com.adarshr.test-logger") version "3.0.0"
}

group = "com.example"
version = "0.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.resilience4j:resilience4j-circuitbreaker:1.7.1")
    implementation("io.vavr:vavr:0.10.4")
    testImplementation("org.junit.jupiter:junit-jupiter:5.7.2")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.7.2")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "16"
}

application {
    mainClass.set("MainKt")
}

testlogger {
    theme = com.adarshr.gradle.testlogger.theme.ThemeType.MOCHA_PARALLEL
    showSimpleNames = true
}