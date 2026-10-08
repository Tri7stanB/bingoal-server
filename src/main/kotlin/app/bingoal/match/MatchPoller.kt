package app.bingoal.match

import app.bingoal.BingoalProperties
import app.bingoal.football.FootballDataSource
import app.bingoal.rules.PropositionState
import app.bingoal.rules.RuleEngine
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock

/**
 * Seule partie du serveur qui appelle le fournisseur de données.
 * Elle ne tourne que pour les matchs de la sélection, de 5 min avant le coup d'envoi
 * à 10 min après le coup de sifflet final : le nombre d'appels dépend du nombre de
 * matchs, pas du nombre de joueurs.
 */
@Component
class MatchPoller(
    private val source: FootballDataSource,
    private val repo: MatchRepository,
    private val props: BingoalProperties,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${bingoal.poller.interval}")
    fun pollAll() {
        for (match in repo.toPoll(clock.instant(), props.poller.startBeforeKickoff)) {
            try {
                poll(match.id)
            } catch (e: Exception) {
                log.warn("Échec du passage du poller pour le match {} : {}", match.id, e.message)
            }
        }
    }

    fun poll(matchId: Long) {
        val match = repo.find(matchId) ?: return
        val now = clock.instant()
        if (match.status == MatchStatus.FINISHED && match.finishedAt != null &&
            now.isAfter(match.finishedAt.plus(props.poller.finalCheckAfter))
        ) {
            repo.close(matchId)
            log.info("Match {} clos : points définitifs", matchId)
            return
        }
        val fixture = source.fetchFixture(matchId) ?: run {
            log.warn("Match {} inconnu du fournisseur de données", matchId)
            return
        }

        val previous = repo.states(matchId)
        val states = repo.activePropositions().map { p ->
            val state = RuleEngine.evaluate(p, fixture)
            val before = previous[p.id]
            val minute = when {
                state != PropositionState.VALIDATED -> null
                before?.state == PropositionState.VALIDATED -> before.validatedMinute
                else -> fixture.elapsed
            }
            if (state != before?.state) log.info("Match {} : « {} » {} -> {}", matchId, p.label, before?.state ?: "-", state)
            StateRow(p.id, state, minute)
        }

        val status = when {
            fixture.isFinished -> MatchStatus.FINISHED
            fixture.isStarted -> MatchStatus.LIVE
            else -> MatchStatus.SCHEDULED
        }
        val finishedAt = if (status == MatchStatus.FINISHED) match.finishedAt ?: now else null
        repo.saveSnapshot(fixture, status, finishedAt, now, states)
    }
}
