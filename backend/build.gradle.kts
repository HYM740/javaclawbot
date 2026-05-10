plugins {
    java
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

repositories {
    mavenCentral()
    maven { url = uri("https://repo.spring.io/milestone") }
    maven { url = uri("https://repo.spring.io/snapshot") }
    maven { url = uri("https://oss.sonatype.org/content/groups/public/") }
    maven { url = uri("https://maven.aliyun.com/repository/public") }
}

dependencies {
    implementation(libs.logback.classic)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    implementation(libs.slf4j.api)
    implementation(libs.cron.utils)
    implementation(libs.picocli)
    implementation(libs.jline)
    implementation(libs.hutool.all)
    implementation(libs.flexmark)
    implementation(libs.flexmark.tables)
    implementation(libs.mapstruct)
    annotationProcessor(libs.mapstruct.processor)
    implementation("net.dankito.readability4j:readability4j:1.0.8")
    implementation("com.dingtalk.open:open-app-stream-client:1.3.12")
    implementation("com.sun.mail:jakarta.mail:2.0.1")
    implementation("com.sun.activation:jakarta.activation:2.0.1")
    implementation("com.larksuite.oapi:oapi-sdk:2.5.3")
    implementation("org.telegram:telegrambots:6.9.7.0")
    implementation("com.formdev:flatlaf:3.4.1")
    implementation("org.apache.poi:poi-ooxml:5.2.5")
    implementation("org.apache.poi:poi-scratchpad:5.2.5")
    implementation("io.modelcontextprotocol.sdk:mcp:1.0.0") {
        exclude(group = "io.modelcontextprotocol.sdk", module = "mcp-json-jackson3")
    }
    implementation("com.networknt:json-schema-validator:2.0.0")
    implementation("org.xerial:sqlite-jdbc:3.45.3.0")
    implementation("com.mysql:mysql-connector-j:9.2.0")
    implementation("org.postgresql:postgresql:42.7.5")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.5.1")
    implementation("com.oracle.database.jdbc:ojdbc11:23.7.0.25.01")
    implementation("com.microsoft.sqlserver:mssql-jdbc:12.8.1.jre11")
    implementation("com.h2database:h2:2.3.232")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.graalvm.polyglot:polyglot:24.1.1")
    implementation("org.graalvm.polyglot:js:24.1.1")

    testImplementation("junit:junit:4.13.2")
}
