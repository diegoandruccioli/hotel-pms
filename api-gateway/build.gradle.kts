plugins {
    java
    id("org.springframework.boot") version "4.0.8"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.hotelpms"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

springBoot {
    mainClass.set("com.hotelpms.gateway.ApiGatewayApplication")
}

repositories {
    mavenCentral()
}

ext {
    set("jjwtVersion", "0.11.5")
    // tomcat.version / netty.version: centralized in the root build.gradle.kts's
    // subprojects{} block — see that file for the CVE history.
}

dependencies {
    implementation("org.springframework.cloud:spring-cloud-starter-gateway-server-webflux")
    implementation("org.springframework.cloud:spring-cloud-starter-config")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // --- Redis-backed Rate Limiting (Spring Cloud Gateway RequestRateLimiter) ---
    // Provides the RedisRateLimiter bean consumed by the RequestRateLimiter filter.
    implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")

    // --- Observability: Micrometer Tracing (Zipkin/Brave) ---
    implementation("org.springframework.boot:spring-boot-starter-zipkin")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    // --- OpenAPI / Swagger UI (WebFlux / Reactive Gateway) ---
    implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:3.1.1")

    implementation("io.jsonwebtoken:jjwt-api:${property("jjwtVersion")}")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:${property("jjwtVersion")}")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:${property("jjwtVersion")}")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:${property("springCloudVersion")}")
    }
    dependencies {
        // CVE-2026-5598: fixed in BouncyCastle 1.84. CVE-2026-8763 (name
        // constraints bypass via trailing dot, CRITICAL): fixed in 1.85.
        dependency("org.bouncycastle:bcprov-jdk18on:1.85")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    systemProperty("net.bytebuddy.experimental", "true")
}
