plugins {
    kotlin("jvm") version "2.0.0"
    id("org.jetbrains.dokka") version "1.9.20"
    id("io.gitlab.arturbosch.detekt") version "1.23.4"
    id("me.champeau.jmh") version "0.7.2"
    application
}

group = "org.kotlin.concurrency"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:1.8.0")
    
    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    
    // HTTP Client for examples
    implementation("io.ktor:ktor-client-core:2.3.7")
    implementation("io.ktor:ktor-client-cio:2.3.7")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.7")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.7")
    
    // Benchmarking
    jmh("org.openjdk.jmh:jmh-core:1.37")
    jmh("org.openjdk.jmh:jmh-generator-annprocess:1.37")
    
    // Database for examples
    implementation("org.jetbrains.exposed:exposed-core:0.46.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.46.0")
    implementation("org.jetbrains.exposed:exposed-dao:0.46.0")
    implementation("com.h2database:h2:2.2.224")
    
    // JSON
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    
    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.14")
    implementation("io.github.microutils:kotlin-logging:3.0.5")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(18)
}

detekt {
    config.setFrom("$projectDir/config/detekt.yml")
    buildUponDefaultConfig = true
}

application {
    mainClass.set("MainKt")
}

jmh {
    iterations.set(3)
    benchmarkMode.set(listOf("avgt"))
    warmupIterations.set(2)
    fork.set(1)
    timeUnit.set("ms")
}