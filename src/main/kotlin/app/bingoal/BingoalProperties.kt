package app.bingoal

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("bingoal")
data class BingoalProperties(
    val dataSource: String,
    val apiFootball: ApiFootball,
    val replay: Replay,
    val poller: Poller,
    val adminToken: String,
) {
    data class ApiFootball(val baseUrl: String, val key: String)
    data class Replay(val dir: String, val minutesPerPoll: Int)
    data class Poller(val interval: Duration, val startBeforeKickoff: Duration, val finalCheckAfter: Duration)
}
