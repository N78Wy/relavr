package io.relavr.sender.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class AppThemeContractTest {
    @Test
    fun `application theme points at the shared relavr appcompat theme`() {
        val manifest = parseXml(repoFile("app", "src", "main", "AndroidManifest.xml"))
        val applicationNode = manifest.getElementsByTagName("application").item(0)
        val theme =
            applicationNode.attributes
                .getNamedItemNS(ANDROID_NAMESPACE, "theme")
                ?.nodeValue

        assertEquals("@style/Theme.Relavr", theme)
    }

    @Test
    fun `relavr theme stays on an appcompat-compatible parent`() {
        val themes = parseXml(repoFile("app", "src", "main", "res", "values", "themes.xml"))
        val styleNodes = themes.getElementsByTagName("style")
        val relavrStyle =
            (0 until styleNodes.length)
                .map { styleNodes.item(it) }
                .firstOrNull { node ->
                    node.attributes?.getNamedItem("name")?.nodeValue == "Theme.Relavr"
                }
                ?: error("Theme.Relavr is missing from themes.xml")

        val parent = relavrStyle.attributes?.getNamedItem("parent")?.nodeValue
        assertTrue(
            "Theme.Relavr must inherit from an AppCompat-compatible parent, but was $parent",
            parent == "Theme.AppCompat.DayNight.NoActionBar" ||
                parent?.contains("AppCompat") == true ||
                parent?.contains("MaterialComponents") == true,
        )
    }

    private fun parseXml(file: File) =
        DocumentBuilderFactory
            .newInstance()
            .apply {
                isNamespaceAware = true
            }.newDocumentBuilder()
            .parse(file)

    private fun repoFile(vararg segments: String): File {
        val userDir = requireNotNull(System.getProperty("user.dir"))
        val root =
            generateSequence(File(userDir).absoluteFile) { current ->
                current.parentFile
            }.firstOrNull { candidate ->
                File(candidate, "settings.gradle.kts").isFile
            } ?: error("Unable to locate repository root from $userDir")
        return segments.fold(root) { parent, segment -> File(parent, segment) }
    }

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    }
}
