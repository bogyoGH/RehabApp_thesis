package org.example.project

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.*

actual object FileStorage {

    // 1. LÉPÉS: Ez a segédfüggvény hiányzott! Ez adja vissza az iOS Documents mappáját.
    private fun documentsPath(): String? {
        return NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory,
            NSUserDomainMask,
            true
        ).firstOrNull() as? String
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun saveText(fileName: String, content: String): String {
        val dir = documentsPath() ?: error("Nem található az iOS Documents könyvtár")
        val filePath = "$dir/$fileName"

        val nsString = NSString.create(string = content)
        val data = nsString.dataUsingEncoding(NSUTF8StringEncoding)
            ?: error("Nem sikerült UTF-8 adatot készíteni a szövegből")

        val success = data.writeToFile(filePath, atomically = true)
        if (!success) {
            error("Nem sikerült elmenteni a fájlt iOS-en: $filePath")
        }

        println("Fájl elmentve iOS-en ide: $filePath")
        return filePath
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun readText(fileName: String): String? {
        val dir = documentsPath() ?: return null
        val fullPath = "$dir/$fileName"

        // 2. LÉPÉS: Kotlin/Native-ban a fájlból való beolvasást legbiztosabban
        // a 'create(contentsOfFile...)' konstruktorral lehet megtenni.
        return try {
            NSString.create(
                contentsOfFile = fullPath,
                encoding = NSUTF8StringEncoding,
                error = null
            ) as String?
        } catch (e: Exception) {
            println("Hiba a fájl olvasásakor: ${e.message}")
            null
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun listFiles(): List<String> {
        val dir = documentsPath() ?: return emptyList()

        val entries = NSFileManager.defaultManager.contentsOfDirectoryAtPath(dir, error = null) as? List<*>
            ?: return emptyList()

        // Csak a String típusú elemeket tartjuk meg (fájlnevek), és csökkenő sorrendbe tesszük (legújabb elöl)
        return entries.filterIsInstance<String>().sortedDescending()
    }
}