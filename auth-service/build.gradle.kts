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
    mainClass.set("com.hotelpms.auth.AuthApplication")
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
    // CVE-2026-42583/42584/42579/42587: fixed in Netty 4.1.133.Final — override Spring Boot BOM pin.
    set("netty.version", "4.1.133.Final")
    set("mapStructVersion", "1.6.3")
    set("jjwtVersion", "0.11.5")
    set("tomcat.version", "10.1.54")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
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

    // --- GAP-4: Log aggregation SIEM (Loki via logback appender) ---
    implementation("com.github.loki4j:loki-logback-appender:1.5.2")

    implementation("io.jsonwebtoken:jjwt-api:${property("jjwtVersion")}")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:${property("jjwtVersion")}")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:${property("jjwtVersion")}")
    
    compileOnly("org.projectlombok:lombok:1.18.38")
    annotationProcessor("org.projectlombok:lombok:1.18.38")
    
    implementation("org.mapstruct:mapstruct:${property("mapStructVersion")}")
    annotationProcessor("org.mapstruct:mapstruct-processor:${property("mapStructVersion")}")
    
    // --- OpenAPI / Swagger UI ---
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.4")

    // Argon2id password hashing (A04:2025 — Cryptographic Failures, T-AUTH-03)
    implementation("org.bouncycastle:bcprov-jdk18on")

    runtimeOnly("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("net.bytebuddy:byte-buddy:1.15.11")
    testImplementation("net.bytebuddy:byte-buddy-agent:1.15.11")
    testImplementation("org.mockito:mockito-core:5.15.2")
    testImplementation("org.mockito:mockito-junit-jupiter:5.15.2")
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
        // CVE-2026-42198: fixed in PostgreSQL JDBC 42.7.11.
        dependency("org.postgresql:postgresql:42.7.11")
        // CVE-2026-5598: fixed in BouncyCastle 1.84.
        dependency("org.bouncycastle:bcprov-jdk18on:1.84")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    systemProperty("net.bytebuddy.experimental", "true")
}

// SpotBugs false-positive in JUnit tests: @BeforeEach-initialized fields are not seen
// by SpotBugs as constructor-initialized, triggering NP_NONNULL_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR.
tasks.named("populateDefaultSpotBugsExcludes") {
    doLast {
        val f = file("${layout.buildDirectory.get()}/javaqa/spotbugs-excludes.xml")
        f.writeText(
            f.readText().replace(
                "</FindBugsFilter>",
                "    <Match>\n        <Bug pattern=\"NP_NONNULL_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR\"/>\n    </Match>\n</FindBugsFilter>"
            )
        )
    }
}
