plugins {
    kotlin("jvm")
    id("jps-compatible")
}

kotlin {
    explicitApi()
}

description = "Kotlin KLIB Library Commonizer"
publish()

dependencies {
    api(kotlinStdlib())

    implementation(project(":native:kotlin-native-utils"))
    testCompile(project(":kotlin-test::kotlin-test-junit"))
    testImplementation(commonDep("junit:junit"))
    testImplementation(projectTests(":compiler:tests-common"))
}

sourceSets {
    "main" { projectDefault() }
    "test" { projectDefault() }
}

runtimeJar()
sourcesJar()
javadocJar()
