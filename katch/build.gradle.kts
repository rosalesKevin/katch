import com.vanniktech.maven.publish.SonatypeHost
import java.util.Properties

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.vanniktech.maven.publish")
}

android {
    namespace = "com.katch"
    compileSdk = 35

    defaultConfig {
        minSdk = 21
        consumerProguardFiles("consumer-rules.pro")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation("dev.whyoleg.cryptography:cryptography-provider-jdk:0.5.0")
    compileOnly("com.jakewharton.timber:timber:5.0.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("com.jakewharton.timber:timber:5.0.1")
}

// ── Publish metadata ──────────────────────────────────────────────────────────
val publishProps = Properties().apply {
    load(rootProject.file("gradle/publish.properties").inputStream())
}
fun prop(key: String): String = publishProps.getProperty(key)
    ?: error("Missing publish property: $key")

group   = prop("GROUP_ID")
version = prop("VERSION")

// ── Maven Central (vanniktech) ────────────────────────────────────────────────
mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    // Sign only when signing key is available (CI sets this env var; local builds skip signing)
    if (System.getenv("ORG_GRADLE_PROJECT_signingInMemoryKey") != null) {
        signAllPublications()
    }
    coordinates(prop("GROUP_ID"), prop("ARTIFACT_ID"), prop("VERSION"))

    pom {
        name.set(prop("POM_NAME"))
        description.set(prop("POM_DESCRIPTION"))
        inceptionYear.set("2026")
        url.set(prop("POM_URL"))

        licenses {
            license {
                name.set(prop("POM_LICENSE_NAME"))
                url.set(prop("POM_LICENSE_URL"))
                distribution.set(prop("POM_LICENSE_DIST"))
            }
        }

        developers {
            developer {
                id.set(prop("POM_DEVELOPER_ID"))
                name.set(prop("POM_DEVELOPER_NAME"))
                url.set(prop("POM_DEVELOPER_URL"))
            }
        }

        scm {
            url.set(prop("POM_SCM_URL"))
            connection.set(prop("POM_SCM_CONN"))
            developerConnection.set(prop("POM_SCM_DEV_CONN"))
        }
    }
}
