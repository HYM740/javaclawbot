plugins {
    java
    application
}

group = "com.zjky.ai"
version = "2.2.8"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

application {
    mainClass.set("gui.ui.Launcher")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":backend"))

    // JavaFX
    val javafxVersion = "17.0.2"
    // OpenJFX main artifacts are POM-only (empty jars); real classes
    // live in platform-specific artifacts (classified by OS).
    val javafxModules = listOf("base", "graphics", "controls", "media", "web")
    val javafxPlatforms = listOf("win", "linux", "mac", "mac-aarch64")

    // Current OS platform artifact for compilation
    val os = System.getProperty("os.name").lowercase()
    val currentPlatform = when {
        os.contains("win") -> "win"
        os.contains("mac") -> "mac"
        os.contains("linux") -> "linux"
        else -> "win"
    }
    javafxModules.forEach { mod ->
        implementation("org.openjfx:javafx-$mod:$javafxVersion:$currentPlatform")
    }

    // Cross-platform artifacts for runtime
    javafxPlatforms.forEach { platform ->
        if (platform != currentPlatform) {
            javafxModules.forEach { mod ->
                runtimeOnly("org.openjfx:javafx-$mod:$javafxVersion:$platform")
            }
        }
    }

    // Dependencies needed by gui code at compile time.
    // Backend declares these as `implementation` so they are not
    // transitively visible at compile time; declare them here directly.
    implementation(libs.slf4j.api)
    implementation(libs.jackson.databind)
    implementation("com.google.code.gson:gson:2.9.0")
    implementation("com.zaxxer:HikariCP:5.1.0")
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    // flexmark — markdown rendering (used directly in gui code)
    implementation(libs.flexmark)
    implementation(libs.flexmark.tables)

    implementation("io.github.raghul-tech:javafx-markdown-preview-all:1.0.3")
}
