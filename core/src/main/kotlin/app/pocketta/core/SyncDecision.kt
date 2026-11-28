package app.pocketta.core

/** What the cloud sync should do when it runs. Pure, so every branch can be tested. */
sealed class SyncAction {
    object Nothing : SyncAction()
    /** Phone data goes up, silently. */
    object Upload : SyncAction()
    /** Cloud data replaces the (empty) phone data, silently. */
    object Download : SyncAction()
    /** Phone and cloud already hold the same thing: just remember that they match. */
    object MarkSynced : SyncAction()
    /** Both sides differ and the app never merges, so a wrong silent choice would lose data: ask once. [newest] is the safer preselected side. */
    data class Ask(val newest: Side) : SyncAction()
}

enum class Side { LOCAL, CLOUD }

data class SyncFacts(
    val cloudExists: Boolean,
    val localEmpty: Boolean,
    /** This phone has synced with this account before. */
    val syncedBefore: Boolean,
    /** Phone and cloud data are identical. */
    val sameContent: Boolean,
    /** The phone changed since the last sync. */
    val localChanged: Boolean,
    /** The cloud changed since the last sync. */
    val cloudChanged: Boolean,
    val localAt: Long = 0L,
    val cloudAt: Long = 0L,
    /** The side the person already chose for exactly this pair of copies (so the same question never comes twice), or null. */
    val rememberedAnswer: Side? = null,
)

object SyncDecision {
    fun decide(f: SyncFacts): SyncAction {
        if (!f.cloudExists) return if (f.localEmpty) SyncAction.Nothing else SyncAction.Upload
        if (!f.syncedBefore) {
            return when {
                f.localEmpty -> SyncAction.Download
                f.sameContent -> SyncAction.MarkSynced
                else -> conflict(f)
            }
        }
        return when {
            f.localChanged && f.cloudChanged -> if (f.sameContent) SyncAction.MarkSynced else conflict(f)
            f.localChanged -> SyncAction.Upload
            f.cloudChanged -> SyncAction.Download
            else -> SyncAction.Nothing
        }
    }

    private fun conflict(f: SyncFacts): SyncAction = when (f.rememberedAnswer) {
        Side.LOCAL -> SyncAction.Upload
        Side.CLOUD -> SyncAction.Download
        null -> SyncAction.Ask(if (f.localAt >= f.cloudAt) Side.LOCAL else Side.CLOUD)
    }

    /** The key that remembers an answer: the two copies' hashes. */
    fun pairKey(localHash: String, cloudHash: String) = "$localHash|$cloudHash"
}
