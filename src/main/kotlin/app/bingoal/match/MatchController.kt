package app.bingoal.match

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/** Lecture seule, pour les apps : matchs de la sélection et état des cases. */
@RestController
@RequestMapping("/matches")
class MatchController(private val repo: MatchRepository) {

    @GetMapping
    fun list(): List<MatchRow> = repo.findAll()

    @GetMapping("/{id}")
    fun detail(@PathVariable id: Long): Map<String, Any?> {
        val match = repo.find(id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        val states = repo.states(id)
        return mapOf(
            "match" to match,
            "propositions" to repo.activePropositions().map { p ->
                val s = states[p.id]
                mapOf(
                    "id" to p.id,
                    "label" to p.label,
                    "level" to p.level,
                    "points" to p.points,
                    "state" to (s?.state ?: "PENDING"),
                    "validatedMinute" to s?.validatedMinute,
                )
            },
            "events" to repo.events(id),
        )
    }
}
