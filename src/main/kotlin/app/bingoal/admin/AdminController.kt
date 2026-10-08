package app.bingoal.admin

import app.bingoal.BingoalProperties
import app.bingoal.football.ReplayDataSource
import app.bingoal.match.MatchPoller
import app.bingoal.match.MatchRepository
import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.Instant

data class SelectMatchRequest(val fixtureId: Long, val kickoff: Instant? = null)

/** Endpoints admin : choisir les matchs de la semaine. Protégés par l'en-tête X-Admin-Token. */
@RestController
@RequestMapping("/admin")
class AdminController(
    private val repo: MatchRepository,
    private val poller: MatchPoller,
    private val replay: ObjectProvider<ReplayDataSource>,
    private val props: BingoalProperties,
    private val clock: Clock,
) {
    /** Ajoute un match à la sélection. Sans coup d'envoi, il commence tout de suite (pratique pour un rejeu). */
    @PostMapping("/matches")
    fun select(@RequestHeader("X-Admin-Token") token: String, @RequestBody req: SelectMatchRequest) {
        checkToken(token)
        replay.ifAvailable { it.reset(req.fixtureId) }
        repo.addMatch(req.fixtureId, req.kickoff ?: clock.instant())
    }

    @DeleteMapping("/matches/{id}")
    fun remove(@RequestHeader("X-Admin-Token") token: String, @PathVariable id: Long) {
        checkToken(token)
        if (!repo.deleteMatch(id)) throw ResponseStatusException(HttpStatus.NOT_FOUND)
    }

    /** Force un passage du poller sur ce match, sans attendre le prochain cycle. */
    @PostMapping("/matches/{id}/poll")
    fun pollNow(@RequestHeader("X-Admin-Token") token: String, @PathVariable id: Long) {
        checkToken(token)
        repo.find(id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        poller.poll(id)
    }

    private fun checkToken(token: String) {
        if (token != props.adminToken) throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
    }
}
