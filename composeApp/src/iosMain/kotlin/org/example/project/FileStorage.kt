package org.example.project

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Foundation.writeToFile

actual object FileStorage {
    @OptIn(ExperimentalForeignApi::class)
    actual fun saveText(fileName: String, content: String): String {
        val documentDirectory = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory,
            NSUserDomainMask,
            true
        ).firstOrNull() as? String
            ?: error("Nem található az iOS Documents könyvtár")

        val filePath = "$documentDirectory/$fileName"

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
}