import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoReport

// Root build script — applies JaCoCo to all Java subprojects for coverage reporting.
// Threshold enforcement is intentionally omitted here: run jacocoTestReport first
// to establish a baseline, then add jacocoTestCoverageVerification with thresholds.
subprojects {
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
            reports {
                xml.required.set(true)
                html.required.set(true)
            }
        }
    }
}
