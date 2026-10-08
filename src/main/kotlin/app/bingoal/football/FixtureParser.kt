package app.bingoal.football

import tools.jackson.databind.JsonNode

/** Convertit un match au format API-Football (/fixtures?id=X) en [Fixture]. */
object FixtureParser {

    /** Accepte la réponse complète ({"response": [...]}) ou directement l'objet match. */
    fun parse(node: JsonNode): Fixture? {
        val m = if (node.has("response")) node.path("response").path(0) else node
        if (m.isMissingNode || m.isNull) return null
        val fixture = m.path("fixture")
        val teams = m.path("teams")
        val fulltime = m.path("score").path("fulltime")
        return Fixture(
            id = fixture.path("id").asLong(),
            statusShort = fixture.path("status").path("short").asString(),
            elapsed = fixture.path("status").path("elapsed").intOrNull(),
            homeTeamId = teams.path("home").path("id").longOrNull(),
            homeTeam = teams.path("home").path("name").stringOrNull(),
            awayTeamId = teams.path("away").path("id").longOrNull(),
            awayTeam = teams.path("away").path("name").stringOrNull(),
            goalsHome = m.path("goals").path("home").intOrNull(),
            goalsAway = m.path("goals").path("away").intOrNull(),
            fulltimeHome = fulltime.path("home").intOrNull(),
            fulltimeAway = fulltime.path("away").intOrNull(),
            events = m.path("events").items().map(::parseEvent),
            starters = m.path("lineups").items().flatMap { team -> team.path("startXI").items().mapNotNull { it.path("player").path("id").longOrNull() } }.toSet(),
            stats = parseStats(m.path("statistics")),
        )
    }

    private fun parseEvent(e: JsonNode) = MatchEvent(
        minute = e.path("time").path("elapsed").intOrNull() ?: 0,
        extra = e.path("time").path("extra").intOrNull(),
        teamId = e.path("team").path("id").longOrNull(),
        teamName = e.path("team").path("name").stringOrNull(),
        playerId = e.path("player").path("id").longOrNull(),
        playerName = e.path("player").path("name").stringOrNull(),
        type = e.path("type").asString(),
        detail = e.path("detail").stringOrNull(),
        comments = e.path("comments").stringOrNull(),
    )

    private fun parseStats(statistics: JsonNode): Map<String, Int>? {
        if (statistics.isEmpty) return null
        val totals = mutableMapOf<String, Int>()
        for (team in statistics.items()) {
            for (s in team.path("statistics").items()) {
                val value = s.path("value")
                if (value.isNull || value.isMissingNode) continue
                val n = value.asString().removeSuffix("%").toIntOrNull() ?: continue
                totals.merge(s.path("type").asString(), n, Int::plus)
            }
        }
        return totals
    }

    private fun JsonNode.items(): List<JsonNode> = iterator().asSequence().toList()
    private fun JsonNode.intOrNull(): Int? = if (isNull || isMissingNode) null else asInt()
    private fun JsonNode.longOrNull(): Long? = if (isNull || isMissingNode) null else asLong()
    private fun JsonNode.stringOrNull(): String? = if (isNull || isMissingNode) null else asString()
}
