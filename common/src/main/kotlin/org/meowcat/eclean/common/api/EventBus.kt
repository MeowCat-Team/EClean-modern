package org.meowcat.eclean.common.api

/**
 * Loader-agnostic event bus abstraction.
 *
 * Implementations register platform listeners (for example Bukkit Listener
 * instances) while the rest of the code can depend only on this interface.
 */
interface EventBus {
    fun register(listener: Any)
    fun unregister(listener: Any)
}
