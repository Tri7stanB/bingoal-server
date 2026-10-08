package app.bingoal.match

import app.bingoal.football.Fixture
import app.bingoal.rules.Level
import app.bingoal.rules.Proposition
import app.bingoal.rules.PropositionState
import app.bingoal.rules.RuleType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant

enum class MatchStatus { SCHEDULED, LIVE, FINISHED, CLOSED }

data class MatchRow(
    val id: Long,
    val homeTeam: String?,
    val awayTeam: String?,
    val kickoff: Instant,
    val status: MatchStatus,
    val apiStatus: String?,
    val minute: Int?,
    val scoreHome: Int?,
    val scoreAway: Int?,
    val finishedAt: Instant?,
)

data class StateRow(val propositionId: String, val state: PropositionState, val validatedMinute: Int?)

@Repository
class MatchRepository(private val jdbc: JdbcClient, private val mapper: ObjectMapper) {

    fun addMatch(id: Long, kickoff: Instant) {
        jdbc.sql("INSERT INTO match (id, kickoff) VALUES (:id, :kickoff) ON CONFLICT (id) DO UPDATE SET kickoff = :kickoff")
            .param("id", id).param("kickoff", Timestamp.from(kickoff)).update()
    }

    fun deleteMatch(id: Long) = jdbc.sql("DELETE FROM match WHERE id = :id").param("id", id).update() > 0

    fun find(id: Long): MatchRow? =
        jdbc.sql("SELECT * FROM match WHERE id = :id").param("id", id).query(::mapMatch).optional().orElse(null)

    fun findAll(): List<MatchRow> = jdbc.sql("SELECT * FROM match ORDER BY kickoff DESC").query(::mapMatch).list()

    /** Matchs que le poller doit interroger : coup d'envoi proche, en cours, ou fini depuis peu. */
    fun toPoll(now: Instant, startBeforeKickoff: java.time.Duration): List<MatchRow> =
        jdbc.sql("SELECT * FROM match WHERE status <> 'CLOSED' AND kickoff <= :limit ORDER BY kickoff")
            .param("limit", Timestamp.from(now.plus(startBeforeKickoff)))
            .query(::mapMatch).list()

    fun activePropositions(): List<Proposition> =
        jdbc.sql("SELECT * FROM proposition WHERE active ORDER BY points, id").query { rs, _ ->
            Proposition(
                id = rs.getString("id"),
                label = rs.getString("label"),
                level = Level.valueOf(rs.getString("level")),
                ruleType = RuleType.valueOf(rs.getString("rule_type")),
                params = mapper.readValue<Map<String, Any?>>(rs.getString("params")),
                points = rs.getInt("points"),
            )
        }.list()

    fun states(matchId: Long): Map<String, StateRow> =
        jdbc.sql("SELECT * FROM proposition_state WHERE match_id = :id").param("id", matchId).query { rs, _ ->
            StateRow(rs.getString("proposition_id"), PropositionState.valueOf(rs.getString("state")), rs.getObject("validated_minute") as Int?)
        }.list().associateBy { it.propositionId }

    fun events(matchId: Long): List<Map<String, Any?>> =
        jdbc.sql("SELECT minute, extra, team_name, player_name, type, detail FROM match_event WHERE match_id = :id ORDER BY seq")
            .param("id", matchId).query().listOfRows()

    /** Enregistre le dernier état du match : la liste d'événements est remplacée en entier. */
    @Transactional
    fun saveSnapshot(f: Fixture, status: MatchStatus, finishedAt: Instant?, now: Instant, states: List<StateRow>) {
        jdbc.sql(
            """
            UPDATE match SET home_team = :home, away_team = :away, status = :status, api_status = :apiStatus,
                   minute = :minute, score_home = :sh, score_away = :sa, finished_at = :finishedAt, last_polled_at = :now
            WHERE id = :id
            """
        )
            .param("id", f.id).param("home", f.homeTeam).param("away", f.awayTeam)
            .param("status", status.name).param("apiStatus", f.statusShort).param("minute", f.elapsed)
            .param("sh", f.goalsHome).param("sa", f.goalsAway)
            .param("finishedAt", finishedAt?.let(Timestamp::from)).param("now", Timestamp.from(now))
            .update()

        jdbc.sql("DELETE FROM match_event WHERE match_id = :id").param("id", f.id).update()
        f.events.forEachIndexed { i, e ->
            jdbc.sql(
                """
                INSERT INTO match_event (match_id, seq, minute, extra, team_id, team_name, player_id, player_name, type, detail, comments)
                VALUES (:m, :seq, :minute, :extra, :teamId, :teamName, :playerId, :playerName, :type, :detail, :comments)
                """
            )
                .param("m", f.id).param("seq", i).param("minute", e.minute).param("extra", e.extra)
                .param("teamId", e.teamId).param("teamName", e.teamName)
                .param("playerId", e.playerId).param("playerName", e.playerName)
                .param("type", e.type).param("detail", e.detail).param("comments", e.comments)
                .update()
        }

        states.forEach { s ->
            jdbc.sql(
                """
                INSERT INTO proposition_state (match_id, proposition_id, state, validated_minute, updated_at)
                VALUES (:m, :p, :state, :vm, :now)
                ON CONFLICT (match_id, proposition_id)
                DO UPDATE SET state = :state, validated_minute = :vm, updated_at = :now
                """
            )
                .param("m", f.id).param("p", s.propositionId).param("state", s.state.name)
                .param("vm", s.validatedMinute).param("now", Timestamp.from(now))
                .update()
        }
    }

    fun close(id: Long) = jdbc.sql("UPDATE match SET status = 'CLOSED' WHERE id = :id").param("id", id).update()

    private fun mapMatch(rs: ResultSet, @Suppress("UNUSED_PARAMETER") n: Int) = MatchRow(
        id = rs.getLong("id"),
        homeTeam = rs.getString("home_team"),
        awayTeam = rs.getString("away_team"),
        kickoff = rs.getTimestamp("kickoff").toInstant(),
        status = MatchStatus.valueOf(rs.getString("status")),
        apiStatus = rs.getString("api_status"),
        minute = rs.getObject("minute") as Int?,
        scoreHome = rs.getObject("score_home") as Int?,
        scoreAway = rs.getObject("score_away") as Int?,
        finishedAt = rs.getTimestamp("finished_at")?.toInstant(),
    )
}
