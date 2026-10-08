package app.bingoal.football

/**
 * Un match tel que le serveur le voit, indépendamment du fournisseur de données.
 * Seul FixtureParser connaît le format d'API-Football.
 */
data class Fixture(
    val id: Long,
    val statusShort: String,
    val elapsed: Int?,
    val homeTeamId: Long?,
    val homeTeam: String?,
    val awayTeamId: Long?,
    val awayTeam: String?,
    val goalsHome: Int?,
    val goalsAway: Int?,
    /** Score à la fin du temps réglementaire, connu seulement une fois le match fini. */
    val fulltimeHome: Int?,
    val fulltimeAway: Int?,
    val events: List<MatchEvent>,
    /** Ids des titulaires des deux équipes, vide si les compositions ne sont pas publiées. */
    val starters: Set<Long>,
    /** Statistiques additionnées pour les deux équipes, null tant qu'elles ne sont pas disponibles. */
    val stats: Map<String, Int>?,
) {
    val isFinished: Boolean get() = statusShort in FINISHED
    val isStarted: Boolean get() = statusShort !in NOT_STARTED

    companion object {
        val FINISHED = setOf("FT", "AET", "PEN", "AWD", "WO")
        val NOT_STARTED = setOf("TBD", "NS", "PST", "CANC")
    }
}

data class MatchEvent(
    val minute: Int,
    val extra: Int?,
    val teamId: Long?,
    val teamName: String?,
    val playerId: Long?,
    val playerName: String?,
    val type: String,
    val detail: String?,
    val comments: String?,
)
