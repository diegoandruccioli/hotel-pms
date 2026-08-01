plugins {
    `java-library`
    `java-test-fixtures`
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

ext {
    // Matches the version every consuming service imports (see */build.gradle.kts) —
    // manages the feign-core version below.
    set("springCloudVersion", "2025.0.0")
}

// Version alignment only — this is a plain java-library, not a Spring Boot
// app (no plugin, no auto-configuration). Pinned to the same Spring Boot /
// Spring Cloud versions every consuming service builds against (see
// */build.gradle.kts), so the shared classes resolve to identical Spring
// and Feign artifact versions on every service's classpath.
dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.5.16")
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:${property("springCloudVersion")}")
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

dependencies {
    api("jakarta.servlet:jakarta.servlet-api")
    api("org.springframework:spring-web")
    api("org.springframework.security:spring-security-web")
    api("org.springframework.security:spring-security-core")
    api("org.springframework.security:spring-security-config")
    api("io.github.openfeign:feign-core")
    implementation("org.springframework.data:spring-data-redis")
    implementation("org.slf4j:slf4j-api")

    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
    testImplementation("org.springframework:spring-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // ADR-004: the shared ArchUnit rule builder (TenantIsolationRules) consumed
    // by every service's own per-package TenantIsolationArchTest.
    testFixturesApi("com.tngtech.archunit:archunit-junit5:1.3.0")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// SpotBugs: project-specific exclusions (Spring DI beans — EI_EXPOSE_REP2 not applicable)
tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
    extraArgs.addAll(
        listOf("-exclude", "${project.projectDir}/config/spotbugs/exclude.xml")
    )
}
