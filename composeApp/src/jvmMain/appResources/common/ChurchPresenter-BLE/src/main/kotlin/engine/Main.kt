package engine

import java.util.concurrent.CountDownLatch

fun main(args: Array<String>) {
    // 1. Load config file (creates default on first run)
    val configFile = AppConfig.findConfigFile()
    AppConfig.load(configFile)

    // 2. CLI args override config file values
    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "--stt-url"    -> { Config.sttServerUrl = args.getOrElse(i + 1) { "" }; i += 2 }
            "--bible-root" -> { Config.bibleRoot    = args.getOrElse(i + 1) { "" }; i += 2 }
            "--port"       -> { args.getOrNull(i + 1)?.toIntOrNull()?.let { Config.outputPort = it }; i += 2 }
            else -> i++
        }
    }

    // 3. Discover Bible root from ChurchPresenter settings if not supplied
    if (Config.bibleRoot.isBlank()) {
        Config.bibleRoot = AppConfig.discoverBibleRoot() ?: ""
    }

    // 4. Start the engine (non-blocking), then block until process exit.
    val handle = EngineServer.start(Config.sttServerUrl, Config.bibleRoot, Config.outputPort)
    if (handle == null) {
        System.err.println(
            "  Set 'bible.root' in ${configFile.absolutePath} or pass --bible-root <path>"
        )
        return
    }
    if (Config.sttServerUrl.isBlank()) {
        println("No stt.server.url set — using WebSocket input mode.")
    }
    println("bible-engine running on ws://localhost:${Config.outputPort}/bible-engine")
    Runtime.getRuntime().addShutdownHook(Thread { handle.stop() })
    CountDownLatch(1).await()
}
