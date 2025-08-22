package app.lawnchair.util

object AllowedApps {
    private val allowedBasePackages = listOf(
        "com.simplemobiletools.dialer",
        "com.simplemobiletools.smsmessenger",
        "com.android.camera2",
        "com.google.android.deskclock"
    )

    fun isAllowed(packageName: String): Boolean {
        return allowedBasePackages.any { basePackage ->
            // Exact match
            packageName == basePackage ||
                // Or starts with base package followed by dot and any alphanumeric suffix
                (packageName.startsWith("$basePackage.") &&
                    packageName.substring(basePackage.length + 1).matches(Regex("^[a-zA-Z0-9._-]+$")))
        }
    }

    // Legacy method for LoaderTask.java compatibility
    fun getAllowedPackages(): List<String> = allowedBasePackages
}
