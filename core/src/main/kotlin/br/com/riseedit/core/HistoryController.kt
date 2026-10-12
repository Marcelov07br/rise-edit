package br.com.riseedit.core

/** Session history. A caller must persist the candidate before moving either stack. */
class HistoryController(private val capacity: Int = 30) {
    private val past = ArrayDeque<Project>()
    private val future = ArrayDeque<Project>()

    init {
        require(capacity > 0)
    }

    val undoCandidate: Project? get() = past.lastOrNull()
    val redoCandidate: Project? get() = future.lastOrNull()
    val canUndo: Boolean get() = past.isNotEmpty()
    val canRedo: Boolean get() = future.isNotEmpty()
    val projects: List<Project> get() = past.toList() + future.toList()

    fun record(previous: Project) {
        past.addLast(previous)
        if (past.size > capacity) past.removeFirst()
        future.clear()
    }

    fun undo(current: Project, persist: (Project) -> Project? = { it }): Project? =
        restore(past, future, current, persist)

    fun redo(current: Project, persist: (Project) -> Project? = { it }): Project? =
        restore(future, past, current, persist)

    private fun restore(
        from: ArrayDeque<Project>,
        to: ArrayDeque<Project>,
        current: Project,
        persist: (Project) -> Project?
    ): Project? {
        val candidate = from.lastOrNull() ?: return null
        val saved = persist(candidate) ?: return null
        from.removeLast()
        to.addLast(current)
        return saved
    }

    fun clear() {
        past.clear()
        future.clear()
    }
}
