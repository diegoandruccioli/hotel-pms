plugins {
    `java-library`
    id("io.spring.dependency-management") version "1.1.7"
    id("org.danilopianini.gradle-java-qa") version "1.165.0"
}

group = "com.hotelpms"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

// Version alignment only — this is a plain java-library, not a Spring Boot
// app (no plugin, no auto-configuration). Pinned to the same Spring Boot /
// Spring Cloud versions every consuming service builds against (see
// */build.gradle.kts), so the shared classes resolve to identical Spring
// and Feign artifact versions on every service's classpath.
dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.5.16")
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2025.0.0")
    }
}

dependencies {
    api("org.springframework:spring-web")
    api("org.springframework.security:spring-security-core")
    api("io.github.openfeign:feign-core")
    implementation("org.slf4j:slf4j-api")

    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
