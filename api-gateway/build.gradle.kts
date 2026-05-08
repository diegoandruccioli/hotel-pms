plugins {
    java
    id("org.springframework.boot") version "3.4.13"
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
    set("springCloudVersion", "2024.0.0")
    set("jjwtVersion", "0.11.5")
    set("tomcat.version", "10.1.54")
    // CVE-2026-33870 (netty-codec-http) + CVE-2026-33871 (netty-codec-http2):
    // fixed in Netty 4.1.132.Final — override Spring Boot BOM pin.
    set("netty.version", "4.1.132.Final")
}

dependencies {
    implementation("org.springframework.cloud:spring-cloud-starter-gateway")
    implementation("org.springframework.cloud:spring-cloud-starter-config")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // --- Redis-backed Rate Limiting (Spring Cloud Gateway RequestRateLimiter) ---
    // Provides the RedisRateLimiter bean consumed by the RequestRateLimiter filter.
    implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")

    // --- Observability: Micrometer Tracing (Zipkin/Brave) ---
    implementation("io.micrometer:micrometer-tracing-bridge-brave")
    implementation("io.zipkin.reporter2:zipkin-reporter-brave")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    // --- OpenAPI / Swagger UI (WebFlux / Reactive Gateway) ---
    implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:2.8.4")

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
        // CVE-2025-41235 (header forwarding from untrusted proxies) +
        // CVE-2025-41253 (EL injection): both fixed in Spring Cloud Gateway 4.2.6.
        // Overrides the 4.2.0 pinned by spring-cloud-dependencies:2024.0.0 BOM.
        dependency("org.springframework.cloud:spring-cloud-gateway-server:4.2.6")
        dependency("org.springframework.cloud:spring-cloud-starter-gateway:4.2.6")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    systemProperty("net.bytebuddy.experimental", "true")
}
