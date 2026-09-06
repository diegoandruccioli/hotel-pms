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
    // NoResourceFoundException lives in org.springframework.web.servlet.resource
    // (spring-webmvc), not spring-web itself.
    api("org.springframework:spring-webmvc")
    api("org.springframework.security:spring-security-core")
    // PropertyReferenceException (invalid Pageable/Sort property) lives here — every JPA
    // consumer already has this on the classpath transitively via spring-boot-starter-data-jpa;
    // notification-service (no DB) gets only the compile-time class, no runtime weight added.
    api("org.springframework.data:spring-data-commons")
    api("io.github.openfeign:feign-core")
    // Shared CsvWriter (server-side CSV export, replaces each service's own
    // hand-rolled escaping). PINNED at 1.9.0, matching frontdesk-service's
    // existing pin — commons-csv 1.10.0+ requires commons-io >= 2.15.0,
    // incompatible with frontdesk-service's own commons-io 2.14.0 pin
    // (CVE-2024-47554 fix). See frontdesk-service/build.gradle.kts and
    // .github/dependabot.yml for the full explanation; this version has no
    // commons-io dependency of its own, so it's safe regardless of what a
    // given consumer pins there.
    api("org.apache.commons:commons-csv:1.9.0")
    implementation("org.slf4j:slf4j-api")
    // MissingServletRequestParameterException extends jakarta.servlet.ServletException;
    // every consuming service already has jakarta.servlet-api on its runtime classpath
    // via spring-boot-starter-web, so compileOnly is enough here.
    compileOnly("jakarta.servlet:jakarta.servlet-api")

    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
    testImplementation("jakarta.servlet:jakarta.servlet-api")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
