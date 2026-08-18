package engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.Properties

object AppConfig {
    private val jsonParser = Json { ignoreUnknownKeys = true }

    fun findConfigFile(): File {
        // 1. Directory containing the running JAR
        val jarDir = runCatching {
            File(AppConfig::class.java.protectionDomain.codeSource.location.toURI()).parentFile
        }.getOrNull()
        if (jarDir != null) {
            val f = File(jarDir, "bible-engine.properties")
            if (f.exists()) return f
        }
        // 2. Working directory
        return File("bible-engine.properties")
    }

    fun load(configFile: File) {
        if (!configFile.exists()) {
            writeDefaultConfigFile(configFile)
            return
        }
        val props = Properties()
        configFile.inputStream().use { props.load(it) }

        props.getProperty("stt.server.url")?.takeIf { it.isNotBlank() }
            ?.let { Config.sttServerUrl = it }
        props.getProperty("bible.root")?.takeIf { it.isNotBlank() }
            ?.let { Config.bibleRoot = it }
        props.getProperty("output.port")?.toIntOrNull()
            ?.let { Config.outputPort = it }
    }

    fun discoverBibleRoot(): String? {
        val f = File(System.getProperty("user.home"), ".churchpresenter/settings.json")
        if (!f.exists()) return null
        return runCatching {
            val obj = jsonParser.parseToJsonElement(f.readText()).jsonObject
            obj["bibleSettings"]?.jsonObject?.get("storageDirectory")
                ?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun writeDefaultConfigFile(file: File) {
        file.writeText(
            """
            # bible-engine configuration
            # Edit this file and restart bible-engine to apply changes.

            # Socket.IO STT server URL.
            # Leave blank for standalone WebSocket input mode (for testing with wscat etc.).
            # Example: stt.server.url=http://localhost:5000
            stt.server.url=

            # Path to the Bible SPB files folder.
            # Leave blank to auto-discover from ~/.churchpresenter/settings.json
            # Example (Windows): bible.root=C:\Users\YourName\Documents\Bibles
            # Example (Mac/Linux): bible.root=/home/yourname/Documents/Bibles
            bible.root=

            # WebSocket output server port
            output.port=8765
            """.trimIndent()
        )
        println("Created default config: ${file.absolutePath}")
        println("Edit it and restart to configure bible-engine.")
    }
}
