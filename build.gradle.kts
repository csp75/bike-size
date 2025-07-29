plugins {
    kotlin("jvm") version "1.9.20"
    application
}

group = "com.bikesize"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.openpnp:opencv:4.9.0-0")
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("ch.qos.logback:logback-classic:1.4.14")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(11)
}

application {
    mainClass.set("com.bikesize.BikeGeometryDetectorKt")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.bikesize.BikeGeometryDetectorKt"
    }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.run.configure {
    args = if (project.hasProperty("appArgs")) {
        (project.property("appArgs") as String).split(" ")
    } else {
        emptyList()
    }
}