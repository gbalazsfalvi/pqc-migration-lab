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
    description = "Runs milestone 05A with fresh managed keys, two full restarts and public evidence."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("hu.balazsfalvi.pqclab.integration.app.migration.MigrationDemo")
    workingDir = rootProject.projectDir
    args(rootProject.layout.buildDirectory.dir("reports/migration-demo").get().asFile.absolutePath)
}
