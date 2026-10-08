package app.bingoal.rules

enum class Level { FACILE, MOYENNE, DIFFICILE }

enum class PropositionState { PENDING, VALIDATED, FAILED }

/** Types de règles génériques. Une proposition de la banque = un type + des paramètres. */
enum class RuleType {
    TOTAL_GOALS_OVER,       // threshold
    BOTH_TEAMS_SCORE,
    GOAL_BEFORE_MINUTE,     // minute
    STAT_TOTAL_OVER,        // stat, threshold
    PENALTY_SCORED,
    VAR_EVENT,
    GOAL_IN_STOPPAGE_TIME,
    SUBSTITUTE_SCORES,
    RED_CARD,
    PLAYER_SCORES_N,        // n
    OWN_GOAL,
}

data class Proposition(
    val id: String,
    val label: String,
    val level: Level,
    val ruleType: RuleType,
    val params: Map<String, Any?>,
    val points: Int,
) {
    fun number(name: String): Double = (params[name] as? Number)?.toDouble()
        ?: error("Proposition $id : paramètre numérique '$name' manquant")

    fun text(name: String): String = params[name] as? String
        ?: error("Proposition $id : paramètre texte '$name' manquant")
}
