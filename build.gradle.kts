import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm") version "1.4.20"
    application
    id("com.github.johnrengelman.shadow") version "6.0.0"
}

group = "dev.zieger"
version = "1.0-SNAPSHOT"

repositories {
    mavenLocal()
    mavenCentral()
    jcenter()
    maven("https://jitpack.io")
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.3.9")
    implementation("dev.zieger.utils:core:2.3.0")
    implementation("dev.zieger.utils:jdk:2.3.0")
    implementation("org.apache.commons:commons-lang3:3.10")
    implementation("com.googlecode.lanterna:lanterna:3.1.0-beta2")

    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.6.0")
}

tasks {
    compileKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }
    compileTestKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }
}

tasks.test {
    useJUnitPlatform()
    outputs.upToDateWhen { false }
}

tasks.named<ShadowJar>("shadowJar") {
    archiveFileName.set("FlakyTest.jar")
    mergeServiceFiles()
    manifest {
        attributes(mapOf("Main-Class" to "MainKt"))
    }
    destinationDirectory.set(File("/home/user/Dokumente"))
}

application {
    mainClassName = "MainKt"
}