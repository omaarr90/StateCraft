plugins {
    java
    id("io.quarkus") version "3.33.4.2"
}

dependencies {
    implementation(enforcedPlatform("io.quarkus.platform:quarkus-bom:3.33.4.2"))
    implementation(project(":engines"))
    implementation("io.quarkus:quarkus-rest-jackson")
    implementation("io.quarkus:quarkus-smallrye-health")
    implementation("io.quarkus:quarkus-smallrye-openapi")
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("io.rest-assured:rest-assured")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("--enable-preview", "--add-modules", "jdk.incubator.vector"))
}

tasks.withType<Test>().configureEach {
    jvmArgs("--enable-preview", "--add-modules", "jdk.incubator.vector")
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
}

tasks.named<io.quarkus.gradle.tasks.QuarkusDev>("quarkusDev") {
    jvmArgs = listOf("--enable-preview", "--add-modules", "jdk.incubator.vector")
}
