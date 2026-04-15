plugins {
    id("org.owasp.dependencycheck") version "12.2.1" apply false
}

subprojects {
    apply(plugin = "org.owasp.dependencycheck")

    configure<org.owasp.dependencycheck.gradle.extension.DependencyCheckExtension> {
        // Fail the build on CVSS >= 9.0 (CRITICAL only); report HIGH but don't block
        failBuildOnCVSS = 9.0f

        // HTML + JSON reports for CI artifact upload
        formats.addAll("HTML", "JSON")

        // Suppress known false positives — add entries as needed
        suppressionFiles.addAll("${rootDir}/config/dependency-check/suppressions.xml")

        analyzers.apply {
            // Disable .NET assembly analyzer — not relevant to this Java project
            assemblyEnabled = false
        }
    }
}
