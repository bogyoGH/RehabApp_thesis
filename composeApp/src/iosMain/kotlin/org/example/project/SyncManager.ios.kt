
package org.example.project

actual fun scheduleOfflineSync() {
    // iOS-en a háttérfolyamatok máshogy működnek (BGTaskScheduler).
    // Egyelőre hagyjuk üresen, így a kód gond nélkül lefordul.
}