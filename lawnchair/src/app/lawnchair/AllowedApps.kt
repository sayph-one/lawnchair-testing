package app.lawnchair.util

object AllowedApps {
    private val allowedBasePackages = listOf(
        "com.simplemobiletools.dialer",
        "com.simplemobiletools.smsmessenger",
        "com.sec.android.gallery3d",
        "com.sec.android.app.camera",
        "com.sec.factory.camera",
        "com.samsung.android.da.daagent",
        "com.sec.android.app.clockpackage.ClockPackage",
        "com.sec.android.app.popupcalculator",
        "com.sec.android.app.clockpackage"
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
