package org.example.project.data

expect object FileStorage {
    /**
     * Elmenti a szöveges tartalmat egy fájlba és visszaadja az elérési utat.
     */
    fun saveText(fileName: String, content: String): String
    fun readText(fileName: String): String?
    fun listFiles(): List<String>
    fun deleteFile(fileName: String): Boolean
    fun markAsUploaded(fileName: String): Boolean
    fun getPendingUploads(): List<String>

    fun readBytes(filePath: String): ByteArray?
}