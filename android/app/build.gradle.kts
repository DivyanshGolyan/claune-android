import java.util.Properties
import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ktlint)
}

val localProperties =
    Properties().apply {
        val file = rootProject.file("local.properties")
        if (file.exists()) {
            file.inputStream().use(::load)
        }
    }

fun escapeBuildConfigString(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")

fun localBooleanProperty(name: String): String = (localProperties.getProperty(name)?.toBooleanStrictOrNull() ?: false).toString()

val bashkitBridgeDir = rootProject.layout.projectDirectory.dir("bashkit-bridge")
val bashkitBridgeOutputDir = layout.buildDirectory.dir("generated/jniLibs/bashkit")
val bashkitBridgeCargoTargetDir = layout.buildDirectory.dir("cargo/bashkit-bridge")
val bashkitBridgeTarget = "aarch64-linux-android"
val bashkitBridgeAbi = "arm64-v8a"
val bashkitBridgeMinApi = 31

fun resolveBashkitBridgeNdkRoot(): File {
    val sdkDir = localProperties.getProperty("sdk.dir")
    return listOfNotNull(
        System.getenv("ANDROID_NDK_HOME"),
        System.getenv("ANDROID_NDK_ROOT"),
        sdkDir?.let { "$it/ndk/27.3.13750724" },
        System.getenv("ANDROID_HOME")?.let { "$it/ndk/27.3.13750724" },
        "/opt/homebrew/share/android-commandlinetools/ndk/27.3.13750724",
    ).map(::file).firstOrNull { it.exists() }
        ?: error("Android NDK 27.3.13750724 not found. Set ANDROID_NDK_HOME or ANDROID_NDK_ROOT.")
}

fun bashkitBridgeNdkHostTag(): String {
    val osName = System.getProperty("os.name").lowercase()
    return when {
        osName.contains("mac") -> "darwin-x86_64"
        osName.contains("linux") -> "linux-x86_64"
        osName.contains("windows") -> "windows-x86_64"
        else -> error("Unsupported NDK host OS for Bashkit bridge: ${System.getProperty("os.name")}")
    }
}

android {
    namespace = "com.divyanshgolyan.claune.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.divyanshgolyan.claune.android"
        minSdk = 31
        targetSdk = 31
        versionCode = 1
        versionName = "0.1.0"
        ndk {
            abiFilters += bashkitBridgeAbi
        }
        vectorDrawables {
            useSupportLibrary = true
        }
        buildConfigField(
            "String",
            "ANTHROPIC_API_KEY",
            "\"${escapeBuildConfigString(localProperties.getProperty("claune.anthropicApiKey", ""))}\"",
        )
        buildConfigField(
            "String",
            "GEMINI_API_KEY",
            "\"${escapeBuildConfigString(localProperties.getProperty("claune.geminiApiKey", ""))}\"",
        )
        buildConfigField(
            "Boolean",
            "CLAUNE_TELEMETRY_ENABLED",
            localBooleanProperty("claune.telemetry.enabled"),
        )
        buildConfigField(
            "String",
            "LANGSMITH_ENDPOINT",
            "\"${escapeBuildConfigString(localProperties.getProperty("claune.langsmith.endpoint", ""))}\"",
        )
        buildConfigField(
            "String",
            "LANGSMITH_API_URL",
            "\"${escapeBuildConfigString(localProperties.getProperty("claune.langsmith.apiUrl", ""))}\"",
        )
        buildConfigField(
            "String",
            "LANGSMITH_PROJECT",
            "\"${escapeBuildConfigString(localProperties.getProperty("claune.langsmith.project", ""))}\"",
        )
        buildConfigField(
            "String",
            "LANGSMITH_API_KEY",
            "\"${escapeBuildConfigString(localProperties.getProperty("claune.langsmith.apiKey", ""))}\"",
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDir(bashkitBridgeOutputDir)
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/io.netty.versions.properties"
        }
    }

    lint {
        abortOnError = true
        checkDependencies = true
        disable += setOf(
            "AndroidGradlePluginVersion",
            "ExpiredTargetSdkVersion",
            "GradleDependency",
            "NewerVersionAvailable",
            "ObsoleteSdkInt",
            "OldTargetApi",
        )
        htmlReport = true
        xmlReport = true
        sarifReport = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ktlint {
    android.set(true)
    ignoreFailures.set(false)
    outputToConsole.set(true)
}

val buildBashkitBridge by tasks.registering(Exec::class) {
    group = "build"
    description = "Builds the Rust Bashkit JNI bridge for Android arm64-v8a without cargo-ndk."

    val outputFile = bashkitBridgeOutputDir.map { it.file("$bashkitBridgeAbi/libbashkit_bridge.so") }
    inputs.file(bashkitBridgeDir.file("Cargo.toml"))
    inputs.file(bashkitBridgeDir.file("Cargo.lock"))
    inputs.dir(bashkitBridgeDir.dir("src"))
    outputs.file(outputFile)

    workingDir = bashkitBridgeDir.asFile
    executable = "cargo"
    args("build", "--release", "--target", bashkitBridgeTarget)

    doFirst {
        val ndkRoot = resolveBashkitBridgeNdkRoot()
        val toolchain = ndkRoot.resolve("toolchains/llvm/prebuilt/${bashkitBridgeNdkHostTag()}/bin")
        val linker = toolchain.resolve("aarch64-linux-android" + bashkitBridgeMinApi + "-clang")
        require(linker.exists()) {
            "Android NDK clang not found at ${linker.absolutePath}. Install NDK 27.3.13750724 or set ANDROID_NDK_HOME."
        }
        environment("CARGO_TARGET_DIR", bashkitBridgeCargoTargetDir.get().asFile.absolutePath)
        environment("CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER", linker.absolutePath)
        environment("CC_aarch64_linux_android", linker.absolutePath)
        environment("AR_aarch64_linux_android", toolchain.resolve("llvm-ar").absolutePath)
    }
    doLast {
        copy {
            from(bashkitBridgeCargoTargetDir.map { it.file("$bashkitBridgeTarget/release/libbashkit_bridge.so") })
            into(bashkitBridgeOutputDir.map { it.dir(bashkitBridgeAbi) })
        }
    }
}

tasks.configureEach {
    if (name == "preBuild" || name.startsWith("merge") && name.endsWith("JniLibFolders")) {
        dependsOn(buildBashkitBridge)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.quickjs.wrapper.android)
    implementation(libs.rhino)
    implementation(libs.markwon.core)
    implementation(libs.markwon.ext.tables)
    implementation(libs.pi.ai.core)
    implementation(libs.pi.agent.core)
    implementation(libs.pi.coding.agent.core)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.named("check") {
    dependsOn("ktlintCheck", "lintDebug")
}

tasks.withType<Test>().configureEach {
    System.getProperties()
        .stringPropertyNames()
        .filter { it.startsWith("claune.projection.") }
        .forEach { name -> systemProperty(name, System.getProperty(name)) }
}

tasks.matching { it.name in setOf("assembleDebug", "installDebug") }.configureEach {
    dependsOn("testDebugUnitTest")
}
