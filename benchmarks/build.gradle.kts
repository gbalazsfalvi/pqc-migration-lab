import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins { application }

dependencies {
    implementation(project(":lab-core"))
    implementation(project(":crypto-jca"))
    implementation(project(":dsse-json"))
    implementation(libs.jmh.core)
    annotationProcessor(libs.jmh.processor)
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)
    testImplementation(libs.archunit)
}

application { mainClass.set("hu.balazsfalvi.pqclab.benchmarks.BenchmarkCli") }

// Only JMH-generated timing loops are excluded; every handwritten benchmark and report class keeps the 90% gate.
val measuredClasses = sourceSets.main.get().output.asFileTree.matching { exclude("**/jmh_generated/**") }
tasks.withType<JacocoReport>().configureEach { classDirectories.setFrom(measuredClasses) }
tasks.withType<JacocoCoverageVerification>().configureEach { classDirectories.setFrom(measuredClasses) }
tasks.withType<Javadoc>().configureEach { exclude("**/jmh_generated/**") }

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
    args("--profile", "standard", "--output",
        rootProject.layout.buildDirectory.dir("reports/benchmarks").get().asFile.absolutePath)
}

// Timings are never Gradle-cacheable outputs: each invocation creates a new uniquely identified evidence run.
fun registerMeasurementTask(taskName: String, profile: String) = tasks.register<JavaExec>(taskName) {
    group = "verification"
    description = "Runs the " + profile + " JMH profile and writes local benchmark evidence."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set(application.mainClass)
    workingDir = rootProject.projectDir
    args("--profile", profile, "--output",
        rootProject.layout.buildDirectory.dir("reports/benchmarks").get().asFile.absolutePath)
}

registerMeasurementTask("smoke", "smoke")
registerMeasurementTask("quick", "quick")
registerMeasurementTask("standard", "standard")
