plugins {
    java
    id("org.springframework.boot") version "4.0.8"
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

springBoot {
    mainClass.set("com.hotelpms.notification.NotificationApplication")
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

ext {
    // tomcat.version / netty.version: centralized in the root build.gradle.kts's
    // subprojects{} block — see that file for the CVE history.
}

dependencies {
    implementation(project(":internal-auth-lib"))
    implementation(project(":common-web-lib"))

    // Core web (REST endpoints to receive notification requests from other services)
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Email sending — Jakarta Mail via Spring Boot Starter Mail
    implementation("org.springframework.boot:spring-boot-starter-mail")

    // HTML email template rendering
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")

    // Security — InternalAuthFilter + HMAC anti-replay (T-GW-08)
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // Config Server
    implementation("org.springframework.cloud:spring-cloud-starter-config")

    // Observability
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-tracing-bridge-brave")
    implementation("io.zipkin.reporter2:zipkin-reporter-brave")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    // GAP-4: Log aggregation SIEM (Loki via logback appender)
    implementation("com.github.loki4j:loki-logback-appender:1.5.2")

    // OpenAPI / Swagger UI
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1")

    compileOnly("org.projectlombok:lombok:1.18.38")
    annotationProcessor("org.projectlombok:lombok:1.18.38")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    // GreenMail: fake SMTP server for unit/integration tests — no external SMTP required
    testImplementation("com.icegreen:greenmail-spring:2.1.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:${property("springCloudVersion")}")
    }
    dependencies {
        dependency("commons-fileupload:commons-fileupload:1.6.0")
        dependency("commons-io:commons-io:2.16.1")
        // CVE-2026-5598: fixed in BouncyCastle 1.84. CVE-2026-8763 (name
        // constraints bypass via trailing dot, CRITICAL): fixed in 1.85.
        dependency("org.bouncycastle:bcprov-jdk18on:1.85")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    systemProperty("net.bytebuddy.experimental", "true")
}

tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
    extraArgs.addAll(
        listOf("-exclude", "${project.projectDir}/config/spotbugs/exclude.xml")
    )
}
