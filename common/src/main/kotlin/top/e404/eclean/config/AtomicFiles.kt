package top.e404.eclean.config

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

object AtomicFiles {
    fun write(path: Path, text: String) {
        Files.createDirectories(path.toAbsolutePath().parent)
        val temp = Files.createTempFile(path.toAbsolutePath().parent, ".eclean-", ".tmp")
        try {
            Files.writeString(temp, text, Charsets.UTF_8)
            try { Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
            catch (_: AtomicMoveNotSupportedException) { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING) }
        } finally { Files.deleteIfExists(temp) }
    }
}
