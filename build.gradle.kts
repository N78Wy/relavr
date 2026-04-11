plugins {
    alias(libs.plugins.spotless)
}

spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**/*.kt", ".gradle/**/*.kt", ".gradle-home/**/*.kt")
        ktlint(libs.versions.ktlint.get())
    }
    kotlinGradle {
        target("**/*.kts")
        targetExclude("**/build/**/*.kts", ".gradle/**/*.kts", ".gradle-home/**/*.kts")
        ktlint(libs.versions.ktlint.get())
    }
    format("misc") {
        target("**/*.md", ".gitignore")
        trimTrailingWhitespace()
        endWithNewline()
    }
}
