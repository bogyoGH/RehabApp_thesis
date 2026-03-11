package org.example.project

import java.io.File

actual object FileStorage {
    actual fun saveText(fileName: String, content: String): String {
        // Átmeneti könyvtár használata (ideális teszteléshez).
        // Éles alkalmazásban érdemes az Android Context-ből lekérni a filesDir-t.
        val directory = File(System.getProperty("java.io.tmpdir"))
        if (!directory.exists()) {
            directory.mkdirs()
        }

        val file = File(directory, fileName)
        file.writeText(content)

        println("Fájl elmentve Androidon ide: ${file.absolutePath}")
        return file.absolutePath
    }
}