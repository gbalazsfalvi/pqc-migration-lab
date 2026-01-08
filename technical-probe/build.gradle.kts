plugins { application }

dependencies {
    implementation(project(":lab-core"))
    implementation(project(":crypto-jca"))
    implementation(project(":dsse-json"))
    implementation(libs.bouncycastle)
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)
    testImplementation(libs.archunit)
}

application { mainClass.set("hu.balazsfalvi.pqclab.probe.TechnicalProbe") }

tasks.named<JavaExec>("run") {
    // Reports always belong to this project's ignored build directory.
    workingDir = rootProject.projectDir
    args("--output", rootProject.layout.buildDirectory.dir("reports/technical-probe").get().asFile.absolutePath)
}
