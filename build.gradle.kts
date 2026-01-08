import com.diffplug.gradle.spotless.SpotlessExtension
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.jvm.tasks.Jar

plugins {
    base
    id("com.diffplug.spotless") version "8.11.0" apply false
}

allprojects {
    group = "hu.balazsfalvi.pqclab"
    version = "0.1.0"
    repositories { mavenCentral() }
}

val catalog = libs

subprojects {
    // Spring Boot's Gradle plugin transitively requests Commons Lang 3.16.0.
    // Keep CVE-2025-48924's fix on the build classpath without changing application dependencies.
    buildscript {
        dependencies {
            constraints {
                add("classpath", "org.apache.commons:commons-lang3:${catalog.versions.commons.lang3.get()}") {
                    because("CVE-2025-48924 is fixed in Commons Lang 3.18.0 and later")
                }
            }
        }
    }
    apply(plugin = "java-library")
    apply(plugin = "jacoco")
    apply(plugin = "com.diffplug.spotless")

    // Keep project licensing and fixture attribution with every distributable Java archive.
    tasks.withType<Jar>().configureEach {
        from(rootProject.layout.projectDirectory) {
            include("LICENSE", "NOTICE", "THIRD_PARTY_NOTICES.md", "THIRD_PARTY_LICENSES/**")
            into("META-INF")
        }
    }

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(25))
        withSourcesJar()
        withJavadocJar()
    }
    extensions.configure<JacocoPluginExtension> { toolVersion = "0.8.16" }
    extensions.configure<SpotlessExtension> {
        java {
            palantirJavaFormat("2.103.0")
            removeUnusedImports()
            trimTrailingWhitespace()
            endWithNewline()
        }
    }
    dependencyLocking { lockAllConfigurations() }
    dependencies {
        // Google Java Format 2.0.0 requests Guava affected by CVE-2026-102554.
        // Scope the correction to that release; future formatter versions retain their own requirements.
        // Component metadata rules also cover Spotless's detached unused-import configuration.
        components {
            withModule("com.google.googlejavaformat:google-java-format") {
                if (id.version == "2.0.0") {
                    allVariants {
                        withDependencies {
                            filter { it.group == "com.google.guava" && it.name == "guava" }.forEach {
                                it.version { require(catalog.versions.guava.get()) }
                            }
                        }
                    }
                }
            }
        }
        "testImplementation"(platform(catalog.junit.bom))
        "testImplementation"(catalog.junit.jupiter)
        "testRuntimeOnly"(catalog.junit.launcher)
    }
    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(25)
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
    }
    tasks.withType<Javadoc>().configureEach {
        options.encoding = "UTF-8"
        (options as StandardJavadocDocletOptions).addBooleanOption("Xdoclint:all", true)
        (options as StandardJavadocDocletOptions).addBooleanOption("Werror", true)
    }
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging { events("failed", "skipped"); showStandardStreams = false }
    }
    tasks.named<JacocoReport>("jacocoTestReport") {
        dependsOn("test")
        reports { xml.required.set(true); html.required.set(true) }
    }
    tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
        dependsOn("test")
        violationRules {
            rule {
                limit { counter = "LINE"; minimum = "0.90".toBigDecimal() }
            }
        }
    }
    // Formatting is checked, never silently rewritten by compilation.
    tasks.named("check") {
        dependsOn("spotlessCheck", "javadoc", "jacocoTestReport", "jacocoTestCoverageVerification")
    }
}

// Explicit root tasks also aggregate the modules when invoked with a fully qualified task path.
tasks.named("check") { dependsOn(subprojects.map { "${it.path}:check" }) }
tasks.named("assemble") { dependsOn(subprojects.map { "${it.path}:assemble" }) }

tasks.register("probe") {
    group = "verification"
    description = "Runs the complete milestone-one cryptography and DSSE technical probe."
    dependsOn(":technical-probe:run")
}

tasks.register("demo") {
    group = "verification"
    description = "Runs the milestone-two migration policy demonstration over real loopback HTTP."
    dependsOn(":integration-app:demo")
}

tasks.register("migrationDemo") {
    group = "verification"
    description = "Runs the isolated three-phase key migration story and writes public evidence."
    dependsOn(":integration-app:migrationDemo")
}

tasks.register("benchmark") {
    group = "verification"
    description = "Runs the complete standard JMH measurement matrix with independent JVM forks."
    dependsOn(":benchmarks:standard")
}

tasks.register("benchmarkQuick") {
    group = "verification"
    description = "Runs a reduced exploratory JMH measurement matrix."
    dependsOn(":benchmarks:quick")
}

tasks.register("benchmarkSmoke") {
    group = "verification"
    description = "Checks the forked JMH harness; timings are not comparative performance evidence."
    dependsOn(":benchmarks:smoke")
}

// Node is a build-time prerequisite only. The packaged workbench runs entirely from Spring Boot.
val npmCommand = if (System.getProperty("os.name").startsWith("Windows")) "npm.cmd" else "npm"
val workbenchDirectory = layout.projectDirectory.dir("workbench")
val workbenchBuild = tasks.register<Exec>("workbenchBuild") {
    group = "build"
    description = "Type-checks and bundles the English React workbench."
    workingDir(workbenchDirectory)
    commandLine(npmCommand, "run", "build")
    inputs.files(fileTree(workbenchDirectory) {
        exclude("node_modules/**", "dist/**", "test-results/**", "playwright-report/**")
    })
    outputs.dir(workbenchDirectory.dir("dist"))
}
val workbenchTest = tasks.register<Exec>("workbenchTest") {
    group = "verification"
    description = "Runs the workbench's focused unit tests."
    workingDir(workbenchDirectory)
    commandLine(npmCommand, "run", "check")
}
tasks.named("check") { dependsOn(workbenchTest) }
