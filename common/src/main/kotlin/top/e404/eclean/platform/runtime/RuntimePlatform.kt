package top.e404.eclean.platform.runtime

enum class RuntimePlatform(val id: String) {
    FOLIA("folia"),
    PAPER("paper"),
}

object RuntimePlatformFactory {
    fun create(folia: Boolean): RuntimePlatform =
        if (folia) RuntimePlatform.FOLIA else RuntimePlatform.PAPER
}
