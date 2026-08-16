import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    id("io.spring.dependency-management") version "1.1.7" apply false
}

subprojects {
    // CVE-2026-54399 (security-report.md Finding #7): Apache HttpComponents Core
    // HTTP/1.1 parser DoS (unbounded header count/length), fixed in httpcore5 5.4.3.
    // Transitive on every service via Spring Boot 3.5.16's BOM, which still pins
    // httpcore5.version=5.3.6 (vulnerable) — no service declares it directly. One
    // override here instead of repeating it in each service's own build.gradle.kts,
    // per the report's remediation (centralized bump, not a per-service fix).
    plugins.withId("io.spring.dependency-management") {
        configure<DependencyManagementExtension> {
            dependencies {
                dependency("org.apache.httpcomponents.core5:httpcore5:5.4.3")
            }
        }
    }

    plugins.withId("java") {
        apply(plugin = "jacoco")

        configure<JacocoPluginExtension> {
            toolVersion = "0.8.12"
        }

        tasks.withType<Test>().configureEach {
            finalizedBy(tasks.withType<JacocoReport>())
        }

        tasks.withType<JacocoReport>().configureEach {
            dependsOn(tasks.withType<Test>())
            finalizedBy(tasks.withType<JacocoCoverageVerification>())
            reports {
                xml.required.set(true)
                html.required.set(true)
            }
        }

        tasks.withType<JacocoCoverageVerification>().configureEach {
            violationRules {
                rule {
                    limit {
                        counter = "INSTRUCTION"
                        value = "COVEREDRATIO"
                        minimum = "0.40".toBigDecimal()
                    }
                }
            }
        }
    }
}
