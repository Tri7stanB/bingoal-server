package app.bingoal.football

import app.bingoal.BingoalProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import tools.jackson.databind.JsonNode

/** Vrais matchs via API-Football : une requête /fixtures?id=X renvoie score, événements, compositions et stats. */
@Component
@ConditionalOnProperty("bingoal.data-source", havingValue = "api-football")
class ApiFootballDataSource(props: BingoalProperties) : FootballDataSource {

    private val client = RestClient.builder()
        .baseUrl(props.apiFootball.baseUrl)
        .defaultHeader("x-apisports-key", props.apiFootball.key)
        .build()

    init {
        require(props.apiFootball.key.isNotBlank()) { "APIFOOTBALL_KEY doit être défini en mode api-football" }
    }

    override fun fetchFixture(id: Long): Fixture? {
        val body = client.get().uri("/fixtures?id={id}", id).retrieve().body(JsonNode::class.java) ?: return null
        val errors = body.path("errors")
        if (!errors.isEmpty) throw IllegalStateException("Erreur API-Football pour le match $id : $errors")
        return FixtureParser.parse(body)
    }
}
