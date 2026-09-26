package top.e404.eclean.menu.trashcan

/** Loaders supply a vanilla-style type name and native block/food properties. */
data class TrashcanItemKind(val name: String, val isBlock: Boolean, val isFood: Boolean)

enum class TrashcanCategory(val key: String) {
    ALL("all"),
    BLOCK("block"),
    EQUIPMENT("equipment"),
    FOOD("food"),
    TOOL("tool"),
    MISC("misc");

    fun matches(item: TrashcanItemKind): Boolean = when (this) {
        ALL -> true
        BLOCK -> item.isBlock
        EQUIPMENT -> EQUIPMENT_MATERIALS.contains(item.name)
        FOOD -> item.isFood
        TOOL -> TOOL_MATERIALS.any { item.name.contains(it) }
        MISC -> !BLOCK.matches(item) && !EQUIPMENT.matches(item) && !FOOD.matches(item) && !TOOL.matches(item)
    }

    private companion object {
        val EQUIPMENT_MATERIALS = setOf(
            "ELYTRA", "SHIELD",
            "LEATHER_HELMET", "LEATHER_CHESTPLATE", "LEATHER_LEGGINGS", "LEATHER_BOOTS",
            "CHAINMAIL_HELMET", "CHAINMAIL_CHESTPLATE", "CHAINMAIL_LEGGINGS", "CHAINMAIL_BOOTS",
            "IRON_HELMET", "IRON_CHESTPLATE", "IRON_LEGGINGS", "IRON_BOOTS",
            "GOLDEN_HELMET", "GOLDEN_CHESTPLATE", "GOLDEN_LEGGINGS", "GOLDEN_BOOTS",
            "DIAMOND_HELMET", "DIAMOND_CHESTPLATE", "DIAMOND_LEGGINGS", "DIAMOND_BOOTS",
            "NETHERITE_HELMET", "NETHERITE_CHESTPLATE", "NETHERITE_LEGGINGS", "NETHERITE_BOOTS",
        )
        val TOOL_MATERIALS = listOf(
            "_PICKAXE", "_AXE", "_SHOVEL", "_HOE", "_SWORD",
            "_BOW", "_CROSSBOW", "_TRIDENT", "FISHING_ROD", "FLINT_AND_STEEL", "SHEARS",
        )
    }
}

enum class TrashcanSort(val key: String) {
    COUNT_DESC("count_desc"),
    NAME_ASC("name_asc"),
    TIME_ASC("time_asc"),
}
