plugins {
    java
    id("org.springframework.boot") version "3.5.14"
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
    set("springCloudVersion", "2025.0.0")
    // CVE-2026-42583/42584/42579/42587: fixed in 4.1.133.Final; CVE-2026-47691/45674/45416/44249: fixed in 4.1.135.Final
    set("netty.version", "4.1.135.Final")
    // CVE-2026-43512/43513/43515/41284/41293/42498: fixed in Tomcat 10.1.55
    set("tomcat.version", "10.1.55")
}

dependencies {
    // Core web (REST endpoints to receive notification requests from other services)
    implementation("org.springframework.boot:spring-boot-starter-web")
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
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.4")

    compileOnly("org.projectlombok:lombok:1.18.38")
    annotationProcessor("org.projectlombok:lombok:1.18.38")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("net.bytebuddy:byte-buddy:1.15.11")
    testImplementation("net.bytebuddy:byte-buddy-agent:1.15.11")
    testImplementation("org.mockito:mockito-core:5.15.2")
    testImplementation("org.mockito:mockito-junit-jupiter:5.15.2")
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
        // CVE-2026-5598: fixed in BouncyCastle 1.84
        dependency("org.bouncycastle:bcprov-jdk18on:1.84")
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
