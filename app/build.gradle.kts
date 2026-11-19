plugins {
    alias(libs.plugins.android.application)
    // Lee app/google-services.json (no se sube a GitHub)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.dosnueve.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.dosnueve.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.recyclerview)

    // Navegación entre pantallas (nav_graph.xml)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)

    // ViewModel y LiveData
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)

    // Firebase: cuentas (Auth) y datos en la nube (Firestore)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)

    // Entrar con Google (Credential Manager)
    implementation(libs.credentials)
    implementation(libs.credentials.play.services.auth)
    implementation(libs.googleid)

    // Recordatorio semanal (RF-31)
    implementation(libs.work.runtime)

    // Foto de Google en Perfil e Inicio (la guarda en caché para verla sin Internet)
    implementation(libs.glide)

    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
