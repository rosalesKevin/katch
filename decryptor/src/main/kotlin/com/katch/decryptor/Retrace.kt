package com.katch.decryptor

import com.android.tools.r8.retrace.ProguardMappingSupplier
import com.android.tools.r8.retrace.ProguardMapProducer
import com.android.tools.r8.retrace.RetraceCommand
import com.android.tools.r8.retrace.Retrace
import java.nio.file.Paths

/**
 * Deobfuscates [stackTrace] using the ProGuard/R8 mapping file at [mappingPath].
 * Throws on I/O error or mapping parse failure.
 */
internal fun retrace(mappingPath: String, stackTrace: String): String {
    val result = mutableListOf<String>()
    val supplier = ProguardMappingSupplier.builder()
        .setProguardMapProducer(ProguardMapProducer.fromPath(Paths.get(mappingPath)))
        .build()
    val command = RetraceCommand.builder()
        .setMappingSupplier(supplier)
        .setStackTrace(stackTrace.lines())
        .setRetracedStackTraceConsumer { lines -> result.addAll(lines) }
        .build()
    Retrace.run(command) // com.android.tools.r8.retrace.Retrace
    return result.joinToString("\n")
}
