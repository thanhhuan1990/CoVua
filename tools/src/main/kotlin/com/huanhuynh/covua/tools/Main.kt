package com.huanhuynh.covua.tools

import java.io.File

/**
 * ./gradlew :tools:run --args="mine --games 20000 --minutes 60 --out build/mined.jsonl"
 * ./gradlew :tools:run --args="pack --in build/mined.jsonl --out ../app/src/main/assets/content --csv content/puzzles.csv"
 */
fun main(args: Array<String>) {
    val opt = args.drop(1).chunked(2).filter { it.size == 2 }.associate { it[0].removePrefix("--") to it[1] }
    fun f(k: String, d: String) = File(opt[k] ?: d)
    when (args.firstOrNull()) {
        "mine" -> ChessMiner(opt["seed"]?.toLong() ?: 20261112L).run(
            opt["games"]?.toInt() ?: 20000, f("out", "build/mined.jsonl"),
            opt["threads"]?.toInt() ?: Runtime.getRuntime().availableProcessors(), opt["minutes"]?.toInt() ?: 60,
        )
        "pack" -> ContentPacker().run(
            opt["in"]!!.split(',').map { File(it) }, f("out", "../app/src/main/assets/content"), f("csv", "content/puzzles.csv"),
            f("legacy", "../app/src/main/assets/puzzles.json"), opt["total"]?.toInt() ?: 600,
        )
        else -> System.err.println("Lệnh: mine | pack (xem Main.kt)")
    }
}
