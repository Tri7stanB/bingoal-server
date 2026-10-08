package app.bingoal.football

import app.bingoal.BingoalProperties
import app.bingoal.TestFixtures
import org.junit.jupiter.api.Test
import java.io.File
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReplayDataSourceTest {

    private fun source(step: Int): ReplayDataSource {
        val dir = File(javaClass.getResource("/fixtures/999001.json")!!.toURI()).parentFile
        val props = BingoalProperties(
            dataSource = "replay",
            apiFootball = BingoalProperties.ApiFootball("", ""),
            replay = BingoalProperties.Replay(dir.path, step),
            poller = BingoalProperties.Poller(Duration.ofSeconds(30), Duration.ofMinutes(5), Duration.ofMinutes(10)),
            adminToken = "test",
        )
        return ReplayDataSource(props, TestFixtures.mapper)
    }

    @Test
    fun `le rejeu avance de quelques minutes a chaque appel et finit sur le vrai match`() {
        val replay = source(step = 30)
        val kickoff = replay.fetchFixture(999001)!!
        assertEquals("NS", kickoff.statusShort)
        assertTrue(kickoff.events.isEmpty())

        val min30 = replay.fetchFixture(999001)!!
        assertEquals("1H", min30.statusShort)
        assertEquals(30, min30.elapsed)
        assertEquals(listOf("subst", "Goal"), min30.events.map { it.type })
        assertEquals(0 to 1, min30.goalsHome to min30.goalsAway)
        assertNull(min30.stats)

        var last = min30
        repeat(10) { last = replay.fetchFixture(999001)!! }
        assertEquals(TestFixtures.sample(), last)
    }

    @Test
    fun `le but des arrets de jeu n'apparait qu'apres la 90e`() {
        val replay = source(step = 1)
        val seen = generateSequence { replay.fetchFixture(999001) }.take(200).toList()
        val at90 = seen.first { it.statusShort == "2H" && it.elapsed == 90 }
        assertEquals(2 to 1, at90.goalsHome to at90.goalsAway)
        assertEquals("FT", seen.last().statusShort)
        assertEquals(3 to 1, seen.last().goalsHome to seen.last().goalsAway)
    }

    @Test
    fun `match absent du dossier de rejeu`() {
        assertNull(source(5).fetchFixture(123))
    }
}
