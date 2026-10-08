package app.bingoal.football

/** Seule porte d'entrée vers les données football : le poller est le seul à l'appeler. */
interface FootballDataSource {
    /** Dernier état connu du match, ou null si le fournisseur ne le connaît pas. */
    fun fetchFixture(id: Long): Fixture?
}
