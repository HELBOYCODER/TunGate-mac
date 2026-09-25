import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.2.0"
    id("org.jetbrains.compose") version "1.9.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.0"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.0"
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.jetbrains.compose.material:material-icons-extended:1.6.11")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("com.google.zxing:core:3.5.3")
}

tasks.register<Copy>("syncAppResources") {
    from("vendor")
    into("app-resources/macos")
    filePermissions { unix("755") }
}

tasks.matching { it.name == "prepareAppResources" || it.name.startsWith("package") || it.name.startsWith("createRuntimeImage") }.configureEach {
    dependsOn("syncAppResources")
}

compose.desktop {
    application {
        mainClass = "com.tungate.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            packageName = "TunGate"
            packageVersion = "1.1.0"
            vendor = "vauth"
            description = "TunGate - WireGuard & AmneziaWG client for macOS"
            appResourcesRootDir = project.layout.projectDirectory.dir("app-resources")

            macOS {
                bundleID = "com.tungate.mac"
                minimumSystemVersion = "11.0"
                iconFile = project.file("icons/TunGate.icns")
                dockName = "TunGate"
            }
        }
    }
}
