import org.gradle.api.JavaVersion.VERSION_1_8
import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-android-extensions")
    id("eclipse")
}

android {
    compileSdkVersion(29)

    defaultConfig {
        applicationId = "com.zeapo.pwdstore"
        minSdkVersion(21)
        targetSdkVersion(29)
        versionCode = 10302
        versionName = "1.3.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = VERSION_1_8
        targetCompatibility = VERSION_1_8
    }

    lintOptions {
        isAbortOnError = true // make sure build fails with lint errors!
        disable("MissingTranslation", "PluralsCandidate")
    }

    packagingOptions {
        exclude(".readme")
        exclude("META-INF/LICENSE.txt")
        exclude("META-INF/NOTICE.txt")
    }

    /*
     * To sign release builds, create the file `gradle.properties` in
     * $HOME/.gradle or in your project directory with this content:
     *
     * mStoreFile=/path/to/key.store
     * mStorePassword=xxx
     * mKeyAlias=alias
     * mKeyPassword=xxx
     */
    if (project.hasProperty("mStoreFile") &&
            project.hasProperty("mStorePassword") &&
            project.hasProperty("mKeyAlias") &&
            project.hasProperty("mKeyPassword")) {
        signingConfigs {
            getByName("release") {
                storeFile = file(project.properties["mStoreFile"] as String)
                storePassword = project.properties["mStorePassword"] as String
                keyAlias = project.properties["mKeyAlias"] as String
                keyPassword = project.properties["mKeyPassword"] as String
            }
        }
        buildTypes.getByName("release").signingConfig = signingConfigs.getByName("release")
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.1.0")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.constraintlayout:constraintlayout:2.0.0-beta2")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.preference:preference:1.1.0")
    implementation("androidx.recyclerview:recyclerview:1.1.0-beta04")
    implementation("com.google.android.material:material:1.1.0-alpha10")
    implementation("androidx.annotation:annotation:1.1.0")
    implementation("org.sufficientlysecure:openpgp-api:12.0")
    implementation("org.eclipse.jgit:org.eclipse.jgit:3.7.1.201504261725-r") {
        exclude(group = "org.apache.httpcomponents", module = "httpclient")
    }
    implementation("com.jcraft:jsch:0.1.55")
    implementation("commons-io:commons-io:2.5")
    implementation("commons-codec:commons-codec:1.13")
    implementation("com.jayway.android.robotium:robotium-solo:5.6.3")
    implementation(kotlin("stdlib-jdk8", KotlinCompilerVersion.VERSION))
    implementation("org.sufficientlysecure:sshauthentication-api:1.0")

    // Testing-only dependencies
    androidTestImplementation("junit:junit:4.13-beta-3")
    androidTestImplementation("org.mockito:mockito-core:3.0.0")
    androidTestImplementation("androidx.test:runner:1.3.0-alpha02")
    androidTestImplementation("androidx.test:rules:1.3.0-alpha02")
    androidTestImplementation("androidx.test.ext:junit:1.1.2-alpha02")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.3.0-alpha02")
    androidTestImplementation("androidx.test.espresso:espresso-intents:3.3.0-alpha02")
}

tasks {
    withType<JavaCompile> {
        options.compilerArgs.add("-Xlint:unchecked")
        options.isDeprecation = true
    }
    withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = "1.8"
            freeCompilerArgs += "-Xnew-inference"
        }
    }
}
