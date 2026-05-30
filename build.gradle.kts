plugins {
    java
    application
    `maven-publish`
}

group = "io.vpscore"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

application {
    mainClass.set("io.vpscore.VPSCore")
}

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

val nettyVersion = "4.1.118.Final"
val log4jVersion = "2.24.3"

dependencies {
    implementation("io.netty:netty-all:$nettyVersion")
    implementation("org.apache.logging.log4j:log4j-core:$log4jVersion")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:$log4jVersion")
    implementation("org.yaml:snakeyaml:2.3")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")

    implementation("org.jline:jline-terminal:3.28.0")
    implementation("org.jline:jline-reader:3.28.0")
    implementation("org.jline:jline-terminal-jni:3.28.0")

    implementation("org.apache.sshd:sshd-core:2.14.0")
    implementation("org.apache.sshd:sshd-sftp:2.14.0")

    implementation("com.h2database:h2:2.3.232")
    implementation("com.zaxxer:HikariCP:6.2.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    implementation("org.telegram:telegrambots-longpolling:8.2.0")
    implementation("org.telegram:telegrambots-client:8.2.0")
    implementation("net.dv8tion:JDA:5.2.3") {
        exclude("club.minnced", "opus-java")
    }

    implementation("org.bouncycastle:bcprov-jdk18on:1.79")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.79")

    implementation("io.prometheus:simpleclient:0.16.0")
    implementation("io.prometheus:simpleclient_httpserver:0.16.0")
    implementation("io.prometheus:simpleclient_hotspot:0.16.0")

    implementation("org.java-websocket:Java-WebSocket:1.5.7")
    implementation("gg.jte:jte:3.1.16")
    implementation("dev.samstevens.totp:totp:1.7.1")
    implementation("commons-io:commons-io:2.18.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.mockito:mockito-core:5.15.2")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.withType<JavaExec> {
    jvmArgs("-Dfile.encoding=UTF-8")
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "io.vpscore.VPSCore",
            "Implementation-Title" to "VPS Core",
            "Implementation-Version" to project.version,
            "Multi-Release" to "true"
        )
    }
}

tasks.register<Jar>("fatJar") {
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest.from(tasks.jar.get().manifest)
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/maven/**")
    }
    from(tasks.compileJava.get().destinationDirectory)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
