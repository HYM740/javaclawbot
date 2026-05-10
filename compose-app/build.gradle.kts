plugins {
    kotlin("jvm")
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.compose.compiler)
    application
}

repositories {
    mavenCentral()
    maven { url = uri("https://repo.spring.io/milestone") }
    maven { url = uri("https://repo.spring.io/snapshot") }
    maven { url = uri("https://oss.sonatype.org/content/groups/public/") }
    maven { url = uri("https://maven.aliyun.com/repository/public") }
}

application {
    mainClass.set("gui.ui.MainKt")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":backend"))
    implementation(project(":java-fx-app"))
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.coroutines.swing)
    // Flexmark (shared with backend for Markdown rendering)
    implementation(libs.flexmark)
    implementation(libs.flexmark.tables)
    // Gson for JSON parsing (AI question dialogs etc)
    implementation("com.google.code.gson:gson:2.10.1")
}
