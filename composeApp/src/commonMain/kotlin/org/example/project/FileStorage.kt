package org.example.project

expect object FileStorage {
    /**
     * Elmenti a szöveges tartalmat egy fájlba és visszaadja az elérési utat.
     */
    fun saveText(fileName: String, content: String): String
    fun readText(fileName: String): String?
    fun listFiles(): List<String>
}