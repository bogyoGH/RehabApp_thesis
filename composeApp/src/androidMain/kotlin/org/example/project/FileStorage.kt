package org.example.project.data

import android.content.Context
import java.io.File
import org.example.project.AppContextHolder

actual object FileStorage {

    private fun getContext(): Context? = AppContextHolder.context

    actual fun saveText(fileName: String, content: String): String {
        val context = getContext() ?: return "ERROR: Context not initialized"
        val directory = context.filesDir
        if (!directory.exists()) {
            directory.mkdirs()
        }

        val file = File(directory, fileName)
        file.writeText(content)

        println("Fájl elmentve Androidon ide: ${file.absolutePath}")
        return file.absolutePath
    }

    actual fun readText(fileName: String): String? {
        val context = getContext() ?: return null
        val safeName = fileName.substringAfterLast('/')
        if (safeName.isBlank()) return null

        val file = File(context.filesDir, safeName)
        return if (file.exists() && file.isFile) file.readText() else null
    }

    actual fun listFiles(): List<String> {
        val context = getContext() ?: return emptyList()
        val dir = context.filesDir
        return dir.listFiles()
            ?.asSequence()
            ?.filter { it.isFile }
            ?.map { it.name }
            ?.sortedDescending()
            ?.toList()
            ?: emptyList()
    }

    actual fun deleteFile(fileName: String) : Boolean{
        val context = getContext() ?: return false
        val safeName = fileName.substringAfterLast('/')
        if (safeName.isBlank()) return false

        val file = File(context.filesDir, safeName)
        return if (file.exists() && file.isFile) {
            file.delete()
        } else {
            false
        }
    }
}
