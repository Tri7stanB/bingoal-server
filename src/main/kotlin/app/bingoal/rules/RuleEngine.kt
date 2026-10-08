package app.bingoal.rules

import app.bingoal.football.Fixture
import app.bingoal.football.MatchEvent

/**
 * Calcule l'état d'une proposition pour un match, toujours à partir de la liste
 * COMPLÈTE des événements : si la VAR annule un but et que le fournisseur retire
 * l'événement, le calcul suivant corrige la case tout seul.
 *
 * Constats faits sur API-Football (Ligue 1 2022-23, 105 matchs) :
 * - les penaltys ratés ne sont jamais publiés, d'où PENALTY_SCORED et pas « penalty sifflé » ;
 * - un but annulé par la VAR disparaît des événements (0 écart buts/score) ;
 * - un rouge annulé par la VAR reste, suivi d'un événement Var « Red card cancelled ».
 */
object RuleEngine {

    fun evaluate(p: Proposition, f: Fixture): PropositionState = when {
        isMet(p, f) -> PropositionState.VALIDATED
        f.isFinished -> PropositionState.FAILED
        p.ruleType == RuleType.GOAL_BEFORE_MINUTE && (f.elapsed ?: 0) >= p.number("minute") -> PropositionState.FAILED
        else -> PropositionState.PENDING
    }

    fun isMet(p: Proposition, f: Fixture): Boolean = when (p.ruleType) {
        RuleType.TOTAL_GOALS_OVER -> score(f).let { (h, a) -> h + a > p.number("threshold") }
        RuleType.BOTH_TEAMS_SCORE -> score(f).let { (h, a) -> h > 0 && a > 0 }
        RuleType.GOAL_BEFORE_MINUTE -> goals(f).any { it.minute < p.number("minute") }
        RuleType.STAT_TOTAL_OVER -> (f.stats?.get(p.text("stat")) ?: 0) > p.number("threshold")
        RuleType.PENALTY_SCORED -> goals(f).any { it.detail == "Penalty" }
        RuleType.VAR_EVENT -> events(f).any { it.type == "Var" }
        RuleType.GOAL_IN_STOPPAGE_TIME -> goals(f).any { (it.extra ?: 0) > 0 }
        RuleType.SUBSTITUTE_SCORES -> f.starters.isNotEmpty() &&
            goals(f).any { it.detail != "Own Goal" && it.playerId != null && it.playerId !in f.starters }
        RuleType.RED_CARD -> redCards(f) > 0
        RuleType.PLAYER_SCORES_N -> goals(f).filter { it.detail != "Own Goal" && it.playerId != null }
            .groupingBy { it.playerId }.eachCount().values.any { it >= p.number("n") }
        RuleType.OWN_GOAL -> goals(f).any { it.detail == "Own Goal" }
    }

    /** Événements du temps réglementaire : prolongations et tirs au but exclus. */
    fun events(f: Fixture): List<MatchEvent> =
        f.events.filter { it.minute <= 90 && it.comments?.contains("shootout", ignoreCase = true) != true }

    /** Buts comptés : penaltys marqués et contre-son-camp inclus. */
    fun goals(f: Fixture): List<MatchEvent> =
        events(f).filter { it.type == "Goal" && it.detail != "Missed Penalty" }

    /** Score à la fin du temps réglementaire, ou score actuel pendant le match. */
    fun score(f: Fixture): Pair<Int, Int> =
        if (f.fulltimeHome != null && f.fulltimeAway != null) f.fulltimeHome to f.fulltimeAway
        else (f.goalsHome ?: 0) to (f.goalsAway ?: 0)

    private fun redCards(f: Fixture): Int {
        val ev = events(f)
        val reds = ev.count { it.type == "Card" && it.detail in setOf("Red Card", "Second Yellow card") }
        val cancelled = ev.count { it.type == "Var" && it.detail == "Red card cancelled" }
        return reds - cancelled
    }
}
