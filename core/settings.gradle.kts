// Permite compilar y probar el módulo de lógica pura de forma aislada
// (por ejemplo, en CI o entornos sin acceso al repositorio Maven de Google):
//   cd core && ../gradlew test
// Cuando se compila desde la raíz, Gradle ignora este archivo.
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "cumbre-core"
