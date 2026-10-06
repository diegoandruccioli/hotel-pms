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
    mainClass.set("com.hotelpms.config.ConfigServiceApplication")
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
    implementation("org.springframework.cloud:spring-cloud-config-server")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-security")
    compileOnly("org.projectlombok:lombok:1.18.38")
    annotationProcessor("org.projectlombok:lombok:1.18.38")
    
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-resttestclient")
    testImplementation("org.springframework.boot:spring-boot-restclient")
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
        // CVE-2026-5598: fixed in BouncyCastle 1.84. CVE-2026-8763 (name
        // constraints bypass via trailing dot, CRITICAL): fixed in 1.85.
        dependency("org.bouncycastle:bcprov-jdk18on:1.85")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
