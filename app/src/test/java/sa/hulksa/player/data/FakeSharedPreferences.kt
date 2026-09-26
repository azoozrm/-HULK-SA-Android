package sa.hulksa.player.data

import android.content.SharedPreferences

/**
 * In-memory SharedPreferences that mirrors the platform contract these regressions depend on:
 * commit applies the editor to the process map even when the durable write fails, and only a
 * true result updates the durable file.
 */
internal class FakeSharedPreferences private constructor(
    private val durable: MutableMap<String, Any?>,
) : SharedPreferences {
    private val memory: MutableMap<String, Any?> = durable.toMutableMap()

    var commitResult: Boolean = true

    constructor() : this(mutableMapOf())

    fun restart(): FakeSharedPreferences = FakeSharedPreferences(durable.toMutableMap())

    fun durableValue(key: String): Any? = durable[key]

    fun durableSnapshot(): Map<String, Any?> = durable.toMap()

    fun durableKeys(): Set<String> = durable.keys.toSet()

    override fun getAll(): MutableMap<String, *> = memory.toMutableMap()

    override fun getString(key: String?, defValue: String?): String? =
        memory[key] as? String ?: defValue

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        (memory[key] as? Set<*>)?.filterIsInstance<String>()?.toMutableSet() ?: defValues

    override fun getInt(key: String?, defValue: Int): Int = memory[key] as? Int ?: defValue

    override fun getLong(key: String?, defValue: Long): Long = memory[key] as? Long ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float = memory[key] as? Float ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean =
        memory[key] as? Boolean ?: defValue

    override fun contains(key: String?): Boolean = memory.containsKey(key)

    override fun edit(): SharedPreferences.Editor = FakeEditor()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit

    private inner class FakeEditor : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private val removed = mutableSetOf<String>()
        private var clearPending = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor =
            put(key, value)

        override fun putStringSet(
            key: String?,
            values: MutableSet<String>?,
        ): SharedPreferences.Editor = put(key, values?.toSet())

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = put(key, value)

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = put(key, value)

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = put(key, value)

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor =
            put(key, value)

        override fun remove(key: String?): SharedPreferences.Editor {
            removed += key.orEmpty()
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clearPending = true
            return this
        }

        override fun commit(): Boolean {
            applyPending()
            if (commitResult) {
                durable.clear()
                durable.putAll(memory)
            }
            return commitResult
        }

        override fun apply() {
            applyPending()
            durable.clear()
            durable.putAll(memory)
        }

        private fun put(key: String?, value: Any?): SharedPreferences.Editor {
            pending[key.orEmpty()] = value
            return this
        }

        private fun applyPending() {
            if (clearPending) memory.clear()
            removed.forEach(memory::remove)
            pending.forEach { (key, value) ->
                if (value == null) memory.remove(key) else memory[key] = value
            }
        }
    }
}
