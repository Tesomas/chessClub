plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.spring") version "2.2.21"
    id("org.springframework.boot") version "4.0.6"
    id("io.spring.dependency-management") version "1.1.7"
    jacoco
}

group = "org.tesomas"
version = "0.0.1-SNAPSHOT"
description = "ChessClub"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(24)
    }
}

repositories {
    mavenCentral()
}

val mockkVersion = "1.14.12"
val springMockkVersion = "5.0.1"

configurations.all {
    // Log4j2 replaces Spring Boot's default Logback
    exclude(group = "org.springframework.boot", module = "spring-boot-starter-logging")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-log4j2")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Honors Kotlin default values / nullability when deserializing request bodies (Jackson 3)
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")

    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")
    testImplementation("org.springframework.boot:spring-boot-starter-data-mongodb-test")
    testImplementation("org.springframework.boot:spring-boot-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-restclient")
    testImplementation("org.springframework.boot:spring-boot-resttestclient")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-mongodb")
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("com.ninja-squad:springmockk:$springMockkVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    // Keep test-run logs out of the project's logs/ directory
    systemProperty("LOG_DIR", layout.buildDirectory.dir("test-logs").get().asFile.absolutePath)
}

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
}

val isWindows = System.getProperty("os.name").lowercase().contains("windows")

val apiTest by tasks.registering(Exec::class) {
    group = "verification"
    description = "Runs the Bruno API test suite (boots the app with a Testcontainers MongoDB)."
    workingDir = file("api-tests")
    commandLine(if (isWindows) listOf("cmd", "/c", "npm", "run", "test:api") else listOf("npm", "run", "test:api"))
}

tasks.register("testAll") {
    group = "verification"
    description = "Runs unit, component, integration, and Bruno API tests."
    dependsOn(tasks.test, apiTest)
}
apiTest { mustRunAfter(tasks.test) }