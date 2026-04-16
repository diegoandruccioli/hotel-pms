plugins {
    java
    id("org.springframework.boot") version "3.4.13"
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
    set("springCloudVersion", "2024.0.0")
    set("tomcat.version", "10.1.54")
}

dependencies {
    implementation("org.springframework.cloud:spring-cloud-config-server")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-security")
    compileOnly("org.projectlombok:lombok:1.18.38")
    annotationProcessor("org.projectlombok:lombok:1.18.38")
    
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:${property("springCloudVersion")}")
    }
    dependencies {
        // CVE-2025-48976 (commons-fileupload 1.5→1.6.0) + CVE-2024-47554 (commons-io 2.11.0→2.14.0)
        // commons-fileupload is not managed by Spring Boot 3.4.x BOM (removed with CommonsMultipartResolver
        // in Spring 6.1); dependencyManagement.dependencies forces the version regardless of BOM properties.
        dependency("commons-fileupload:commons-fileupload:1.6.0")
        dependency("commons-io:commons-io:2.14.0")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
