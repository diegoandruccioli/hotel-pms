import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    id("io.spring.dependency-management") version "1.1.7" apply false
}

subprojects {
    // Centralized override for CVEs fixed by newer transitive versions than Spring
    // Boot 3.5.16's BOM pins — set once for every subproject instead of the
    // per-service `ext { set(...) }` block each of the 8 Boot services used to
    // carry. That duplication is exactly what let Tomcat 10.1.55 go stale in all
    // 8 places at once (Trivy alerts #597/598/599, CRITICAL).
    // Tomcat: CVE-2026-43512/43513/43515/41284/41293/42498 fixed in 10.1.55 (2026-05-05).
    //         Trivy alerts #597/598/599 (CRITICAL) claim a fix in 10.1.58, but that
    //         version is NOT published on Maven Central as of 2026-09-10 (verified:
    //         GET .../tomcat-embed-core/10.1.58/... -> 404; ./gradlew :auth-service:dependencies
    //         fails to resolve it). Left pinned at 10.1.55 — the highest version that
    //         actually exists — until Apache publishes the fix. Re-check on release
    //         and bump both this value and dismiss the three alerts if still open.
    // Netty:  CVE-2026-42583/42584/42579/42587 fixed in 4.1.133.Final;
    //         CVE-2026-47691/45674/45416/44249 fixed in 4.1.135.Final;
    //         CVE-2026-56745/55833/55831/59901 fixed in 4.1.136.Final;
    //         CVE-2026-75595 (CRITICAL) fixed in 4.1.137.Final (Trivy alert #617) —
    //         published and resolves cleanly.
    extra["tomcat.version"] = "10.1.55"
    extra["netty.version"] = "4.1.137.Final"

    // CVE-2026-54399 / CVE-2026-54428 (security-report.md Finding #7): Apache
    // HttpComponents Core HTTP/1.1 and HTTP/2 parser DoS (unbounded header
    // count/length), fixed in 5.4.3. Transitive on every service via Spring Boot
    // 3.5.16's BOM, which still pins both httpcore5 and its HTTP/2 sibling
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
