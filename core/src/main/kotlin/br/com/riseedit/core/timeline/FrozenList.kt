package br.com.riseedit.core.timeline

/** Defensive list snapshot. Data-class copies cannot introduce a mutable backing list. */
class FrozenList<out T>(values: Collection<T>) : AbstractList<T>() {
    private val snapshot = values.toList()
    override val size: Int get() = snapshot.size
    override fun get(index: Int): T = snapshot[index]
}
