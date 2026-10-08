package app.bingoal.football

import app.bingoal.BingoalProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Rejoue un match déjà joué comme s'il était en direct, sans consommer de requêtes.
 * Le fichier JSON (sortie de /fixtures?id=X, par exemple match_871824.json de l'outil
 * d'étape 1) est lu dans le dossier de rejeu. Chaque appel avance l'horloge du match
 * de `minutes-per-poll` minutes et ne renvoie que les événements déjà survenus.
 */
@Component
@ConditionalOnProperty("bingoal.data-source", havingValue = "replay", matchIfMissing = true)
class ReplayDataSource(props: BingoalProperties, private val mapper: ObjectMapper) : FootballDataSource {

    private val dir = File(props.replay.dir)
    private val step = props.replay.minutesPerPoll
    private val cursors = ConcurrentHashMap<Long, Int>()

    override fun fetchFixture(id: Long): Fixture? {
        val full = load(id) ?: return null
        val timeline = Timeline.of(full)
        val tick = cursors.merge(id, 0) { current, _ -> minOf(current + step, timeline.lastIndex) }!!
        return timeline.at(tick)
    }

    /** Remet un match au coup d'envoi (pour le rejouer depuis le début). */
    fun reset(id: Long) {
        cursors.remove(id)
    }

    private fun load(id: Long): Fixture? {
        val file = listOf("$id.json", "match_$id.json").map { File(dir, it) }.firstOrNull { it.isFile } ?: return null
        return FixtureParser.parse(mapper.readTree(file))
    }

    /** Les minutes d'un match : avant-match, 1re mi-temps et ses arrêts, mi-temps, 2e mi-temps et ses arrêts, fin. */
    class Timeline private constructor(private val full: Fixture, private val ticks: List<Tick>) {

        data class Tick(val status: String, val minute: Int, val extra: Int)

        val lastIndex get() = ticks.lastIndex

        fun at(index: Int): Fixture {
            val tick = ticks[index]
            if (tick.status == "FT") return full
            val events = full.events.filter { e ->
                tick.status != "NS" && (e.minute < tick.minute || (e.minute == tick.minute && (e.extra ?: 0) <= tick.extra))
            }
            val (home, away) = scoreOf(events)
            return full.copy(
                statusShort = tick.status,
                elapsed = if (tick.status == "NS") null else tick.minute,
                goalsHome = if (tick.status == "NS") null else home,
                goalsAway = if (tick.status == "NS") null else away,
                fulltimeHome = null,
                fulltimeAway = null,
                events = events,
                stats = null, // statistiques connues seulement à la fin dans un rejeu
            )
        }

        private fun scoreOf(events: List<MatchEvent>): Pair<Int, Int> {
            val goals = events.filter { it.type == "Goal" && it.detail != "Missed Penalty" && it.minute <= 90 }
            val home = goals.count { scoredForHome(it) }
            return home to goals.size - home
        }

        private fun scoredForHome(goal: MatchEvent): Boolean {
            val forTeam = if (goal.detail == "Own Goal" && ownGoalTeamIsConceding) goal.teamId != full.homeTeamId
            else goal.teamId == full.homeTeamId
            return forTeam
        }

        /**
         * Pour un but contre son camp, l'équipe de l'événement est-elle celle qui encaisse ?
         * On choisit la convention qui redonne le vrai score final du match.
         */
        private val ownGoalTeamIsConceding: Boolean by lazy {
            val finalHome = full.fulltimeHome ?: full.goalsHome ?: return@lazy false
            val goals = full.events.filter { it.type == "Goal" && it.detail != "Missed Penalty" && it.minute <= 90 }
            val homeIfBenefiting = goals.count { it.teamId == full.homeTeamId }
            homeIfBenefiting != finalHome
        }

        companion object {
            fun of(full: Fixture): Timeline {
                val extra1 = maxExtra(full, 45)
                val extra2 = maxExtra(full, 90)
                val ticks = buildList {
                    add(Tick("NS", 0, 0))
                    (1..45).forEach { add(Tick("1H", it, 0)) }
                    (1..extra1).forEach { add(Tick("1H", 45, it)) }
                    add(Tick("HT", 45, extra1))
                    (46..90).forEach { add(Tick("2H", it, 0)) }
                    (1..extra2).forEach { add(Tick("2H", 90, it)) }
                    add(Tick("FT", 90, extra2))
                }
                return Timeline(full, ticks)
            }

            private fun maxExtra(f: Fixture, minute: Int) =
                maxOf(2, f.events.filter { it.minute == minute }.maxOfOrNull { it.extra ?: 0 } ?: 0)
        }
    }
}
