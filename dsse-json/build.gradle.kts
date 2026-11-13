dependencies {
    api(project(":lab-core"))
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)
}
