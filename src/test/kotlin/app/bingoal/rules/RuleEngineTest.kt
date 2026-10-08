package app.bingoal.rules

import app.bingoal.TestFixtures
import app.bingoal.TestFixtures.bank
import app.bingoal.football.MatchEvent
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class RuleEngineTest {

    private val finished = TestFixtures.sample()

    private fun state(id: String, f: app.bingoal.football.Fixture = finished) = RuleEngine.evaluate(bank.getValue(id), f)

    @Test
    fun `match fini - chaque case a son etat definitif`() {
        val expected = mapOf(
            "over_2_5" to PropositionState.VALIDATED,      // 3-1
            "btts" to PropositionState.VALIDATED,
            "goal_before_25" to PropositionState.FAILED,   // premier but à la 29e
            "corners_over_9" to PropositionState.FAILED,   // 7 corners
            "penalty" to PropositionState.VALIDATED,
            "var" to PropositionState.VALIDATED,
            "sub_scorer" to PropositionState.VALIDATED,    // Bayo n'est pas titulaire
            "red_card" to PropositionState.FAILED,         // rouge annulé par la VAR
            "stoppage_goal" to PropositionState.VALIDATED, // 90+3
            "brace" to PropositionState.VALIDATED,         // David x2
            "own_goal" to PropositionState.FAILED,
            "over_4_5" to PropositionState.FAILED,
        )
        assertEquals(expected, bank.keys.associateWith { state(it) })
    }

    @Test
    fun `pendant le match - une case non atteinte reste en attente`() {
        val live = finished.copy(statusShort = "1H", elapsed = 20, fulltimeHome = null, fulltimeAway = null,
            goalsHome = 0, goalsAway = 0, events = emptyList(), stats = null)
        assertEquals(PropositionState.PENDING, state("goal_before_25", live))
        assertEquals(PropositionState.PENDING, state("btts", live))
    }

    @Test
    fun `but avant la 25e - ratee des que la 25e minute est passee`() {
        val live = finished.copy(statusShort = "1H", elapsed = 25, fulltimeHome = null, fulltimeAway = null,
            goalsHome = 0, goalsAway = 0, events = emptyList(), stats = null)
        assertEquals(PropositionState.FAILED, state("goal_before_25", live))
    }

    @Test
    fun `but annule par la VAR - la case redevient en attente au calcul suivant`() {
        val goal = MatchEvent(10, null, 79, "Lille", 2009, "J. David", "Goal", "Normal Goal", null)
        val withGoal = finished.copy(statusShort = "1H", elapsed = 12, fulltimeHome = null, fulltimeAway = null,
            goalsHome = 1, goalsAway = 0, events = listOf(goal), stats = null)
        assertEquals(PropositionState.VALIDATED, state("goal_before_25", withGoal))

        val goalRemoved = withGoal.copy(elapsed = 14, goalsHome = 0, events = emptyList())
        assertEquals(PropositionState.PENDING, state("goal_before_25", goalRemoved))
    }

    @Test
    fun `tirs au but et prolongations ignores`() {
        val shootout = MatchEvent(120, null, 79, "Lille", 2001, "X", "Goal", "Penalty", "Penalty Shootout")
        val extraTimeOwnGoal = MatchEvent(105, null, 81, "Marseille", 1002, "Y", "Goal", "Own Goal", null)
        val f = finished.copy(statusShort = "PEN", events = listOf(shootout, extraTimeOwnGoal))
        assertEquals(PropositionState.FAILED, state("penalty", f))
        assertEquals(PropositionState.FAILED, state("own_goal", f))
    }

    @Test
    fun `un contre son camp ne compte pas pour le doublé ni pour le remplaçant buteur`() {
        val og1 = MatchEvent(30, null, 81, "Marseille", 3000, "Z", "Goal", "Own Goal", null)
        val og2 = og1.copy(minute = 60)
        val f = finished.copy(events = listOf(og1, og2))
        assertEquals(PropositionState.VALIDATED, state("own_goal", f))
        assertEquals(PropositionState.FAILED, state("brace", f))
        assertEquals(PropositionState.FAILED, state("sub_scorer", f))
    }
}
