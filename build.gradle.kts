import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "2.5.7"
    id("io.spring.dependency-management") version "1.0.11.RELEASE"
    kotlin("jvm") version "1.6.10"
    kotlin("plugin.spring") version "1.6.10"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_11

repositories {
    mavenCentral()
}

extra["hapiFhirVersion"] = "5.6.1"
extra["postgresVersion"] = "42.3.1"
extra["testcontainersVersion"] = "1.16.2"

dependencyManagement {
    imports {
        mavenBom("org.testcontainers:testcontainers-bom:${property("testcontainersVersion")}")
    }
}
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-logging")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    implementation("ca.uhn.hapi.fhir:hapi-fhir-spring-boot-starter:${property("hapiFhirVersion")}")
    implementation("ca.uhn.hapi.fhir:hapi-fhir-jpaserver-base:${property("hapiFhirVersion")}")
    implementation("ca.uhn.hapi.fhir:hapi-fhir-jaxrsserver-base:${property("hapiFhirVersion")}")
    implementation("ca.uhn.hapi.fhir:hapi-fhir-base:${property("hapiFhirVersion")}")
    implementation("ca.uhn.hapi.fhir:hapi-fhir-client:${property("hapiFhirVersion")}")
    implementation("ca.uhn.hapi.fhir:hapi-fhir-structures-r4:${property("hapiFhirVersion")}")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:elasticsearch")
    testImplementation("org.postgresql:postgresql:${property("postgresVersion")}")
}


tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "11"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
