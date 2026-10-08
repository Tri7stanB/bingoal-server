package app.bingoal

import app.bingoal.football.Fixture
import app.bingoal.football.FixtureParser
import app.bingoal.rules.Level
import app.bingoal.rules.Proposition
import app.bingoal.rules.RuleType
import tools.jackson.databind.json.JsonMapper

object TestFixtures {
    val mapper = JsonMapper.builder().build()

    /** Match d'exemple : voir src/test/resources/fixtures/README.md. */
    fun sample(): Fixture =
        FixtureParser.parse(mapper.readTree(javaClass.getResource("/fixtures/999001.json")!!.readText()))!!

    /** La banque de départ, identique à la migration V2. */
    val bank = listOf(
        prop("over_2_5", RuleType.TOTAL_GOALS_OVER, "threshold" to 2.5),
        prop("btts", RuleType.BOTH_TEAMS_SCORE),
        prop("goal_before_25", RuleType.GOAL_BEFORE_MINUTE, "minute" to 25),
        prop("corners_over_9", RuleType.STAT_TOTAL_OVER, "stat" to "Corner Kicks", "threshold" to 9.5),
        prop("penalty", RuleType.PENALTY_SCORED),
        prop("var", RuleType.VAR_EVENT),
        prop("sub_scorer", RuleType.SUBSTITUTE_SCORES),
        prop("red_card", RuleType.RED_CARD),
        prop("stoppage_goal", RuleType.GOAL_IN_STOPPAGE_TIME),
        prop("brace", RuleType.PLAYER_SCORES_N, "n" to 2),
        prop("own_goal", RuleType.OWN_GOAL),
        prop("over_4_5", RuleType.TOTAL_GOALS_OVER, "threshold" to 4.5),
    ).associateBy { it.id }

    private fun prop(id: String, type: RuleType, vararg params: Pair<String, Any>) =
        Proposition(id, id, Level.FACILE, type, params.toMap(), 10)
}
