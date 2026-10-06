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
    mainClass.set("com.hotelpms.fb.FbApplication")
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
    set("mapStructVersion", "1.6.3")
    // tomcat.version / netty.version: centralized in the root build.gradle.kts's
    // subprojects{} block — see that file for the CVE history.
}

dependencies {
    implementation(project(":internal-auth-lib"))
    implementation(project(":common-web-lib"))
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-security")
    // Redis-backed nonce store for internal HMAC anti-replay (T-GW-08)
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.cloud:spring-cloud-starter-config")
    implementation("org.springframework.cloud:spring-cloud-starter-openfeign") {
        exclude(group = "org.springframework.cloud", module = "spring-cloud-starter-netflix-eureka-client")
    }
    implementation("org.springframework.cloud:spring-cloud-starter-loadbalancer")
    implementation("org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j")
    // --- Observability: Actuator + Micrometer Tracing (Zipkin/Brave) ---
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-tracing-bridge-brave")
    implementation("io.zipkin.reporter2:zipkin-reporter-brave")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    
    compileOnly("org.projectlombok:lombok:1.18.38")
    annotationProcessor("org.projectlombok:lombok:1.18.38")
    
    implementation("org.mapstruct:mapstruct:${property("mapStructVersion")}")
    annotationProcessor("org.mapstruct:mapstruct-processor:${property("mapStructVersion")}")
    
    // --- OpenAPI / Swagger UI ---
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1")

    runtimeOnly("org.postgresql:postgresql")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    // ADR-004: enforces hotel_id scoping on multi-tenant repositories (T-BILL-04 class of bug) —
    // TenantIsolationRules + archunit-junit5 come transitively via the testFixtures below.
    testImplementation(testFixtures(project(":internal-auth-lib")))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:${property("springCloudVersion")}")
    }
    dependencies {
        // CVE-2025-48976 (commons-fileupload 1.5→1.6.0) + CVE-2024-47554 (commons-io 2.11.0→2.14.0)
        // commons-fileupload is not managed by Spring Boot 3.5.x BOM (removed with CommonsMultipartResolver
        // in Spring 6.1); dependencyManagement.dependencies forces the version regardless of BOM properties.
        dependency("commons-fileupload:commons-fileupload:1.6.0")
        dependency("commons-io:commons-io:2.14.0")
        // CVE-2026-42198: fixed in PostgreSQL JDBC 42.7.11; CVE-2026-54291 (SCRAM-SHA-256-PLUS channel-binding downgrade): fixed in 42.7.12.
        dependency("org.postgresql:postgresql:42.7.12")
        // CVE-2026-5598: fixed in BouncyCastle 1.84. CVE-2026-8763 (name
        // constraints bypass via trailing dot, CRITICAL): fixed in 1.85.
        dependency("org.bouncycastle:bcprov-jdk18on:1.85")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    systemProperty("net.bytebuddy.experimental", "true")
}
