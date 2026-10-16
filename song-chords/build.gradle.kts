plugins {
    id("cp.jvm-module")
    id("cp.pitest")
}

// The chord grammar is pure text: no coroutines, no serialization, no I/O, and deliberately no
// dependency on :core-models. It is the rule a song's markup is read by, so everything that reads
// a song can depend on it without taking a model or a file format along.
dependencies {
    testImplementation(kotlin("test"))
    testImplementation(libs.kotest.property)
}

