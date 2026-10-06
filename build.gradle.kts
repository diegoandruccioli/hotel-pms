import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    id("io.spring.dependency-management") version "1.1.7" apply false
}

subprojects {
    // Centralized override for CVEs fixed by newer transitive versions than Spring
    // Boot 4.0.8's BOM pins — set once for every subproject instead of per service.
    // Tomcat 11.0.24 / Netty 4.2.17 come from the Boot 4.0.8 BOM: the old 10.1.x /
    // 4.1.x overrides (migration F14) no longer apply and were dropped; re-check
    // Trivy and re-add a pin here only if a newer fix is needed.
    // Jackson 2 (still used transitively, e.g. jjwt-jackson): jackson-databind
    //         CVE-2026-68497/91776/91777 and jackson-core CVE-2026-89407/89425 fixed
    //         in 2.21.7; Boot 4.0.8's BOM pins 2.21.5 via `jackson-2-bom.version`.
    // Jackson 3 (tools.jackson.*): the same advisories (jackson-core CVE-2026-89407/89425,
    //         jackson-databind CVE-2026-68497/91776/91777, HIGH, found by the Trivy rescan
    //         after the Boot 4 migration) are fixed in 3.1.7; Boot 4.0.8 pins 3.1.5.
    extra["jackson-2-bom.version"] = "2.21.7"
    extra["jackson-bom.version"] = "3.1.7"

    // CVE-2026-54399 / CVE-2026-54428 (security-report.md Finding #7): Apache
    // HttpComponents Core HTTP/1.1 and HTTP/2 parser DoS (unbounded header
    // count/length), fixed in 5.4.3. Transitive on every service via Spring Boot
    // 4.0.8's BOM, which still pins both httpcore5 and its HTTP/2 sibling
    // httpcore5-h2 at 5.3.6 (vulnerable) — no service declares either directly.
    // One override here instead of repeating it in each service's own
    // build.gradle.kts, per the report's remediation (centralized bump, not a
    // per-service fix).
    plugins.withId("io.spring.dependency-management") {
        configure<DependencyManagementExtension> {
            dependencies {
                dependency("org.apache.httpcomponents.core5:httpcore5:5.4.3")
                dependency("org.apache.httpcomponents.core5:httpcore5-h2:5.4.3")
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
