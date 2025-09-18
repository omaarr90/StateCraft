plugins { `java-library` }

dependencies {
    api(project(":core"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
}
