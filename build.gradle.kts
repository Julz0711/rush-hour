import io.papermc.hangarpublishplugin.model.Platforms

plugins {
    java
    id("io.papermc.hangar-publish-plugin") version "0.1.4"
    id("com.modrinth.minotaur") version "2.10.0"
}

group = "io.github.julz0711"
version = "0.1.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.3.build.230-stable")
    testImplementation("io.papermc.paper:paper-api:26.3.build.230-stable")
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v26.3:4.119.0")
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java { toolchain.languageVersion.set(JavaLanguageVersion.of(25)) }
tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }
tasks.test { useJUnitPlatform() }
tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("plugin.yml") { expand("version" to project.version) }
}
tasks.jar { archiveBaseName.set("RushHour") }

modrinth {
    token.set(providers.environmentVariable("MODRINTH_TOKEN"))
    projectId.set(providers.environmentVariable("MODRINTH_PROJECT_ID").orElse(""))
    versionNumber.set(project.version.toString())
    versionName.set("Rush Hour ${project.version}")
    versionType.set("alpha")
    uploadFile.set(tasks.jar)
    gameVersions.add("26.3")
    loaders.add("paper")
    changelog.set(providers.fileContents(layout.projectDirectory.file("CHANGELOG.md")).asText)
}
hangarPublish {
    publications.register("plugin") {
        version.set(project.version.toString())
        channel.set("Alpha")
        id.set(providers.environmentVariable("HANGAR_PROJECT").orElse(""))
        apiKey.set(providers.environmentVariable("HANGAR_API_TOKEN"))
        changelog.set(providers.fileContents(layout.projectDirectory.file("CHANGELOG.md")).asText)
        platforms.register(Platforms.PAPER) {
            jar.set(tasks.jar.flatMap { it.archiveFile })
            platformVersions.set(listOf("26.3"))
        }
    }
}
