plugins {
    `java-library`
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

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

dependencies {
    // Standalone Thymeleaf (no Spring) — HTML/CSS template rendering.
    api("org.thymeleaf:thymeleaf:3.1.5.RELEASE")
    // HTML/CSS -> PDF, built on PDFBox 3.x. Apache-2.0/LGPL-2.1+, actively maintained.
    api("io.github.openhtmltopdf:openhtmltopdf-pdfbox:1.1.40")

    implementation("org.slf4j:slf4j-api:2.0.16")

    compileOnly("org.projectlombok:lombok:1.18.38")
    annotationProcessor("org.projectlombok:lombok:1.18.38")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
