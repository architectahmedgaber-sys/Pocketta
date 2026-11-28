package app.pocketta.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards for "the AI key never leaves the phone". The Android export / sync / CSV code cannot run in a plain JVM test, so these tests read the
 * source and fail if the key's storage is referenced from any of those code paths, if the backup rules stop excluding it, or if a key is logged or committed.
 * (The web side has the same check on its real export: tests/web/key-privacy.test.mjs.)
 */
class QuickAiKeyGuardTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() && File(it, "app").exists() }
    private fun src(dir: String) = File(root, dir).walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "xml") }.toList()
    private val app = src("app/src/main")
    private val core = src("core/src/main")

    @Test fun backupAndTransferRulesExcludeTheKeyFile() {
        for (name in listOf("backup_rules.xml", "data_extraction_rules.xml")) {
            val f = File(root, "app/src/main/res/xml/$name").readText()
            assertTrue("$name must exclude pocketta_quickai.xml", f.contains("""path="pocketta_quickai.xml""""))
            assertEquals("$name: cloud backup and device transfer both exclude it", if (name == "backup_rules.xml") 1 else 2, Regex("pocketta_quickai\\.xml").findAll(f).count())
        }
        assertTrue(File(root, "app/src/main/AndroidManifest.xml").readText().contains("@xml/data_extraction_rules"))
    }

    @Test fun onlyTheAssistantScreensTouchTheKeyStore() {
        val allowed = setOf("QuickAiStore.kt", "QuickAiScreen.kt", "QuickAiSetup.kt", "SettingsScreen.kt")
        val users = app.filter { it.extension == "kt" && Regex("QuickAiStore|pocketta_quickai|key_enc").containsMatchIn(it.readText()) }.map { it.name }.toSet()
        assertTrue("unexpected users of the key store: ${users - allowed}", allowed.containsAll(users))
        // Database, export, backup, sync and CSV code must never reference it.
        val sensitive = app.filter { f -> "/data/" in f.path || f.name.contains("Sync") || f.name.contains("Csv") || f.name.contains("Backup") || f.name.contains("Entities") } + core
        for (f in sensitive) assertTrue("${f.name} must not reference the AI key store", !Regex("QuickAiStore|pocketta_quickai|key_enc|x-api-key").containsMatchIn(f.readText()))
    }

    @Test fun theKeyIsNeverLoggedOrPrinted() {
        val files = app.filter { it.name.startsWith("QuickAi") || it.path.contains("/ai/QuickAi") }
        assertTrue(files.size >= 4)
        for (f in files) f.readLines().forEachIndexed { i, line ->
            val code = line.substringBefore("//")
            if (Regex("Log\\.|println\\(|addLog\\(|printStackTrace|Toast").containsMatchIn(code))
                assertTrue("${f.name}:${i + 1} must not log the key", !Regex("(?i)\\bkey\\b|\\bfield\\b|apiKey").containsMatchIn(code))
        }
    }

    @Test fun noApiKeyIsCommittedInSourceOrDocs() {
        val shape = Regex("sk-ant-[A-Za-z0-9_-]{20,}")
        val files = root.walkTopDown().onEnter { it.name != ".git" && it.name != "build" && it.name != "node_modules" && it.name != "test" && it.name != "tests" }
            .filter { it.isFile && it.extension in setOf("kt", "kts", "js", "mjs", "xml", "md", "html", "json", "yml", "properties", "txt") }.toList()
        assertTrue(files.size > 50)
        for (f in files) assertTrue("${f.path} contains something shaped like an Anthropic key", !shape.containsMatchIn(f.readText()))
    }

    @Test fun theOnlyAnthropicHostInTheAppIsTheOfficialOne() {
        for (f in app.filter { it.extension == "kt" && it.readText().contains("x-api-key") && it.name.startsWith("QuickAi") }) {
            val hosts = Regex("https?://[a-zA-Z0-9.-]+").findAll(f.readText()).map { it.value }.toSet()
            assertTrue("${f.name}: $hosts", hosts.all { it == "https://api.anthropic.com" || it == "https://console.anthropic.com" })
        }
    }
}
