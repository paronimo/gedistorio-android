plugins {
    alias(libs.plugins.android.application)
    // Procesa app/src/google-services.json (proyecto Firebase gedistorio). El
    // plugin agrega la configuracion publica de Firebase; las claves privadas
    // (cuenta de servicio) quedan solo en el backend.
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.example.geditorio"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.gedistorio.conductores"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "0.5"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

            // URL del backend web (sin barra final). Se toma de gradle.properties
            // (geditorio.backendUrl) o de la variable de entorno GEDITORIO_BACKEND_URL.
            // 10.0.2.2 es el equipo anfitrion visto desde el emulador; para un
            // celular real hay que poner la IP del equipo en la misma red, por
            // ejemplo http://192.168.1.50:3000, y recompilar la app.
            val backendUrl = (project.findProperty("geditorio.backendUrl") as String?)
                ?: System.getenv("GEDITORIO_BACKEND_URL")
                ?: "http://10.0.2.2:3000"
            buildConfigField("String", "BACKEND_URL", "\"${backendUrl.trimEnd('/')}\"")
        }
        buildFeatures {
            buildConfig = true
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

}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    // Firebase Authentication (sesion del conductor) y FCM (notificaciones
    // aunque la app este cerrada). BoM para alinear las versiones.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.messaging)
    // Reportes en la nube: se guardan directo en Firestore aunque el backend
    // local esté apagado.
    implementation(libs.firebase.firestore)
    implementation(libs.kotlinx.coroutines.play.services)
    testImplementation(libs.junit)
    // Implementacion real de org.json para las pruebas unitarias de la JVM,
    // en lugar del stub de Android que lanza "not mocked".
    testImplementation("org.json:json:20240303")
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}