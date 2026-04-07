plugins {
    kotlin("jvm")
    application
}

kotlin {
    jvmToolchain(11)
}

application {
    mainClass.set("com.katch.decryptor.MainKt")
}

dependencies {
    implementation("dev.whyoleg.cryptography:cryptography-provider-jdk:0.5.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("com.android.tools:r8:8.2.42")
    testImplementation("junit:junit:4.13.2")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.katch.decryptor.MainKt"
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}
