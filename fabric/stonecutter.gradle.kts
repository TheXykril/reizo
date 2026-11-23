// Stonecutter controller: picks the version the shared sources are written for right now.
// Switch with ./gradlew :fabric:"Set active project to <version>" (commit only with 1.21.11 active).
plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "1.21.11"
