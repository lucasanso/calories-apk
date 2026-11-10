plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":library-format"))
    implementation(libs.pdfbox)
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
}

application {
    mainClass.set("com.calories.importer.MainKt")
}

val repoRoot = rootProject.layout.projectDirectory

// Gera app/src/main/assets/library/food_library.json e o relatório de importação.
tasks.register<JavaExec>("generateFoodLibrary") {
    group = "nutrition"
    description = "Importa TACO/USDA, valida e gera o asset da biblioteca nutricional."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set(application.mainClass)
    workingDir = repoRoot.asFile
    args("--data-dir", "nutrition-data", "--output", "app/src/main/assets/library/food_library.json",
        "--report", "nutrition-data/reports/import-report.md")
}

// Falha se o asset commitado não for exatamente o que o importador gera hoje.
tasks.register<JavaExec>("verifyFoodLibrary") {
    group = "nutrition"
    description = "Regenera em memória e compara com o asset commitado (byte a byte)."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set(application.mainClass)
    workingDir = repoRoot.asFile
    args("--data-dir", "nutrition-data", "--output", "app/src/main/assets/library/food_library.json",
        "--report", "nutrition-data/reports/import-report.md", "--check")
}

tasks.test {
    // Os testes de integração leem os arquivos oficiais em nutrition-data/ e o asset commitado.
    systemProperty("calories.repoRoot", repoRoot.asFile.absolutePath)
    inputs.dir(repoRoot.dir("nutrition-data/raw"))
    inputs.dir(repoRoot.dir("nutrition-data/curation"))
    inputs.file(repoRoot.file("nutrition-data/sources.lock.json"))
    inputs.file(repoRoot.file("app/src/main/assets/library/food_library.json"))
}
