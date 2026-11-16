plugins {
    application
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(project(":integration-core"))
    implementation(project(":crypto-jca"))
    implementation(project(":dsse-json"))
    implementation(platform(libs.spring.boot.bom))
    implementation(libs.spring.webmvc)
    implementation(libs.spring.jdbc)
    implementation(libs.spring.flyway)
    runtimeOnly(libs.h2)
    // The existing protocol adapters intentionally retain their isolated Jackson 2 wire codecs.
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)
    testImplementation(libs.archunit)
}

application { mainClass.set("hu.balazsfalvi.pqclab.integration.app.LocalIntegration") }
springBoot { mainClass.set(application.mainClass) }

// Gradle's test worker has its own launcher classpath. Child services use only reviewed main runtime artifacts.
tasks.withType<Test>().configureEach {
    systemProperty("pqc.lab.childClasspath", sourceSets.main.get().runtimeClasspath.asPath)
    // The public fixture is a test input: changed bytes must invalidate cached integrity results.
    inputs.dir(rootProject.layout.projectDirectory.dir("docs/evidence/06b-compose"))
}

tasks.named<JavaExec>("run") { workingDir = rootProject.projectDir }
tasks.named<ProcessResources>("processResources") {
    dependsOn(rootProject.tasks.named("workbenchBuild"))
    from(rootProject.layout.projectDirectory.dir("workbench/dist")) { into("static") }
}
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    workingDir = rootProject.projectDir
}
tasks.register<JavaExec>("demo") {
    group = "verification"
    description = "Exercises every migration policy over real loopback HTTP and writes local evidence."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("hu.balazsfalvi.pqclab.integration.app.demo.IntegrationDemo")
    workingDir = rootProject.projectDir
    args(rootProject.layout.buildDirectory.dir("reports/integration-demo").get().asFile.absolutePath)
}

tasks.register<JavaExec>("migrationDemo") {
    group = "verification"
    description = "Runs milestone 06A with separate service JVMs, receiver crash recovery and public evidence."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("hu.balazsfalvi.pqclab.integration.app.migration.ProcessMigrationDemo")
    workingDir = rootProject.projectDir
    args(rootProject.layout.buildDirectory.dir("reports/migration-demo").get().asFile.absolutePath)
}

tasks.register<JavaExec>("processMigrationDemo") {
    group = "verification"
    description = "Runs the migration story in separate publisher/receiver JVMs with independent restart and outage checks."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("hu.balazsfalvi.pqclab.integration.app.migration.ProcessMigrationDemo")
    workingDir = rootProject.projectDir
    args(rootProject.layout.buildDirectory.dir("reports/process-migration-demo").get().asFile.absolutePath)
}

tasks.register<JavaExec>("legacyMigrationDemo") {
    group = "verification"
    description = "Retains the milestone-05A one-JVM reference story for comparison."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("hu.balazsfalvi.pqclab.integration.app.migration.MigrationDemo")
    workingDir = rootProject.projectDir
    args(rootProject.layout.buildDirectory.dir("reports/migration-demo-05a").get().asFile.absolutePath)
}

tasks.register<JavaExec>("composeMigrationDemo") {
    group = "verification"
    description = "Builds and tests an owned local Compose deployment, including independent restarts and volume cleanup."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("hu.balazsfalvi.pqclab.integration.app.migration.ProcessMigrationDemo")
    workingDir = rootProject.projectDir
    args(rootProject.layout.buildDirectory.dir("reports/compose-migration-demo").get().asFile.absolutePath, "--compose")
}

tasks.register<JavaExec>("rehearsalDemo") {
    group = "verification"
    description = "Rehearses all four live receiver policies with original envelopes and unchanged durable receipts."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("hu.balazsfalvi.pqclab.integration.app.migration.PolicyMigrationDemo")
    workingDir = rootProject.projectDir
    args(rootProject.layout.buildDirectory.dir("reports/policy-rehearsal").get().asFile.absolutePath)
}

tasks.register<JavaExec>("composeRehearsalDemo") {
    group = "verification"
    description = "Rehearses four actual local Compose receiver deployments while retaining one publisher and ledger."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("hu.balazsfalvi.pqclab.integration.app.migration.PolicyMigrationDemo")
    workingDir = rootProject.projectDir
    args(rootProject.layout.buildDirectory.dir("reports/compose-policy-rehearsal").get().asFile.absolutePath, "--compose")
}
