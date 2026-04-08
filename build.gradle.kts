plugins {
    kotlin("jvm") version "2.2.20"

}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    mavenLocal()
    maven ( uri("https://www.orangepaydev.xyz/repository"))
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation(group = "org.slf4j", name = "slf4j-api", version = "1.7.32")
    implementation(group = "com.google.code.gson", name = "gson", version = "2.8.9")
    implementation(group = "com.squareup.okhttp3", name = "okhttp", version = "4.10.0")
    implementation(group = "com.fasterxml", name = "aalto-xml", version = "1.3.3")
    implementation(group = "org.codehaus.woodstox", name = "stax2-api", version = "4.2.2")
    implementation(group = "com.ximpleware", name = "vtd-xml", version = "2.13.4")
    implementation(group = "org.bouncycastle", name = "bcprov-jdk18on", version = "1.76") // Core provider
    implementation(group = "org.bouncycastle", name = "bcpkix-jdk18on", version = "1.76") // PEM / PKIX / CMS
    implementation(group = "org.bouncycastle", name = "bcpg-jdk18on",   version = "1.76") // OpenPGP
    implementation("orangepay.core:lib-bpmn-v3:1.1.13") { isTransitive = false }
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}