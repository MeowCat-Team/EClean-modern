package org.meowcat.eclean.config

/**
 * Configuration files used by the profile-based loader.
 *
 * `PROFILE` is the root selector, `NORMAL` is the single normal preset file,
 * and the remaining entries are the full dev preset files.
 */
enum class ConfigFiles(
    val diskName: String,
    val resourcePath: String,
) {
    PROFILE("config.yml", "config.yml"),
    NORMAL("config/normal/config.yml", "config/normal/config.yml"),
    GLOBAL("config/dev/config.yml", "config/dev/config.yml"),
    CLEANUP("config/dev/cleanup.yml", "config/dev/cleanup.yml"),
    DROP("config/dev/drop.yml", "config/dev/drop.yml"),
    LIVING("config/dev/living.yml", "config/dev/living.yml"),
    CHUNK_DENSITY("config/dev/chunk-density.yml", "config/dev/chunk-density.yml"),
    TRASHCAN("config/dev/trashcan.yml", "config/dev/trashcan.yml"),
    PER_WORLD("config/dev/per-world.yml", "config/dev/per-world.yml"),
    ADVANCED("config/dev/advanced.yml", "config/dev/advanced.yml"),
    ;

    companion object {
        /** Files that belong to the dev preset. */
        val DEV_ENTRIES: List<ConfigFiles> = listOf(
            GLOBAL,
            CLEANUP,
            DROP,
            LIVING,
            CHUNK_DENSITY,
            TRASHCAN,
            PER_WORLD,
            ADVANCED,
        )

        /** Legacy root-level files from before the normal/dev profiles. */
        val LEGACY_FILES: List<String> = listOf(
            "config.yml",
            "cleanup.yml",
            "drop.yml",
            "living.yml",
            "chunk-density.yml",
            "trashcan.yml",
            "per-world.yml",
        )
    }
}