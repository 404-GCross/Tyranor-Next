import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.Zip
import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) {
        file.inputStream().use { load(it) }
    }
}
fun configValue(name: String): String =
    System.getenv(name)?.takeIf { it.isNotBlank() }
        ?: localProperties.getProperty(name)?.takeIf { it.isNotBlank() }
        ?: ""
fun String.asBuildConfigString(): String =
    "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val appNativePluginSourceDir = layout.projectDirectory.dir("src/main/nativeplugins")
val engineNativePluginSourceDir = rootProject.layout.projectDirectory.dir("engine/src/main/nativeplugins")
val mergedNativePluginSourceDir = layout.buildDirectory.dir("generated/nativeplugin-sources")
val bundledNativePluginAssetsDir = layout.buildDirectory.dir("generated/assets/nativeplugins")
val bundledNativePluginEngineIds = listOf("kirikiroid2", "ons", "artemis")
val engineAssetSourceDir = rootProject.layout.projectDirectory.dir("engine/src/main/assets")
val sharedEngineAssetNames = listOf(
    "__rmmz__.js",
    "__hook_rmmz_core.js",
    "__hook_rmmz_managers.js",
)
val hikarinagiClientId = configValue("HIKARINAGI_CLIENT_ID")
val ciKeystoreFile = System.getenv("ANDROID_KEYSTORE_FILE")?.takeIf { it.isNotBlank() }
val ciKeystorePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")?.takeIf { it.isNotBlank() }
val ciKeyAlias = System.getenv("ANDROID_KEY_ALIAS")?.takeIf { it.isNotBlank() }
val ciKeyPassword = System.getenv("ANDROID_KEY_PASSWORD")?.takeIf { it.isNotBlank() }
val hasCiReleaseSigning = listOf(
    ciKeystoreFile,
    ciKeystorePassword,
    ciKeyAlias,
    ciKeyPassword,
).all { !it.isNullOrBlank() }

val syncBundledNativePluginSources by tasks.registering(Sync::class) {
    group = "native plugins"
    description = "Merges engine-owned native plugin binaries with app-owned plugin manifests."
    duplicatesStrategy = DuplicatesStrategy.FAIL
    from(engineNativePluginSourceDir) {
        exclude("**/.DS_Store")
    }
    from(appNativePluginSourceDir) {
        exclude("**/.DS_Store")
    }
    into(mergedNativePluginSourceDir)
}

val bundledNativePluginZipTasks = bundledNativePluginEngineIds.map { engineId ->
    tasks.register<Zip>("package${engineId.replaceFirstChar { it.uppercase() }}NativePlugin") {
        group = "native plugins"
        description = "Packages the $engineId native engine plugin as a compressed asset."
        dependsOn(syncBundledNativePluginSources)
        from(mergedNativePluginSourceDir.map { it.dir(engineId) })
        destinationDirectory.set(bundledNativePluginAssetsDir)
        archiveFileName.set("$engineId.zip")
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }
}

val packageBundledNativePlugins by tasks.registering {
    group = "native plugins"
    description = "Packages bundled native engine plugins as compressed assets."
    dependsOn(bundledNativePluginZipTasks)
}

val syncSharedEngineAssets by tasks.registering(Sync::class) {
    group = "native plugins"
    description = "Copies shared engine-owned scripts into the app asset tree."
    from(engineAssetSourceDir) {
        include(sharedEngineAssetNames)
        eachFile {
            path = "engine/$path"
        }
        includeEmptyDirs = false
    }
    into(layout.buildDirectory.dir("generated/assets"))
}

android {
    namespace = "com.tyranor.next"
    // miuix 0.9.2 传递依赖要求 compileSdk 37（本地平台为 android-37.0）
    compileSdk {
        version = release(37) {
            minorApiLevel = 0
        }
    }
    defaultConfig {
        applicationId = "com.tyranor.next"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.20"
        buildConfigField("String", "HIKARINAGI_CLIENT_ID", hikarinagiClientId.asBuildConfigString())
    }

    signingConfigs {
        create("ciRelease") {
            if (hasCiReleaseSigning) {
                storeFile = file(ciKeystoreFile!!)
                storePassword = ciKeystorePassword
                keyAlias = ciKeyAlias
                keyPassword = ciKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName(
                if (hasCiReleaseSigning) "ciRelease" else "debug"
            )
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = true
      shaders = false
    }

    packaging {
      jniLibs {
        useLegacyPackaging = true
      }
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }

    // 引擎原生插件先压缩为 assets/nativeplugins/<engine>.zip，首次启动自动安装到 app 私有目录
    sourceSets {
      getByName("main") {
        assets.directories.clear()
        assets.directories.add("src/main/assets")
        assets.directories.add("build/generated/assets")
      }
    }
}

tasks.matching {
    (it.name.startsWith("merge") && it.name.endsWith("Assets")) ||
        it.name.contains("Lint", ignoreCase = true)
}.configureEach {
    dependsOn(packageBundledNativePlugins)
    dependsOn(syncSharedEngineAssets)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.process)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)
  implementation(libs.androidx.documentfile)
  implementation(libs.appauth)

  // Miuix 组件库（设置页 Card + Preference 体系）
  implementation(libs.miuix.ui)
  implementation(libs.miuix.preference)
  // 液态玻璃导航（圆角流体玻璃底部导航，参考 RinneMobile）
  implementation(libs.backdrop)
  implementation(project(":engine"))
}
