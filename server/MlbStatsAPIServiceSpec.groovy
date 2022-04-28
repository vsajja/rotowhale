package org.baseballsite.services

import spock.lang.Shared
import spock.lang.Specification

class MlbStatsAPIServiceSpec extends Specification {
    @Shared
    MlbStatsAPIService mlbStatsAPIService

    def setupSpec() {
        mlbStatsAPIService = new MlbStatsAPIService()
    }

    def "get mlb teams"() {
        when:
        def mlbTeams = mlbStatsAPIService.getMlbTeams()

        mlbTeams.each {
            println it.toString()
        }

        then:
        assert mlbTeams.size() == 30
    }

    def "get mlb roster"() {
        setup:
        def jaysTeamId = 141

        when:
        def blueJays = mlbStatsAPIService.getMlbRoster(jaysTeamId)

        blueJays.each {
            println it.toString()
        }

        then:
        assert blueJays.find { it['nameLast'] == 'Guerrero Jr.' }
    }

    def "get player hitting stats"() {
        setup:
        def ohtani = 660271

        when:
        def ohtaniHittingStats = mlbStatsAPIService.getPlayerHittingStats(ohtani)

        then:
        assert ohtaniHittingStats.size() > 0
        // Ohtani hit 22 homers in his rookie year (2018)
        assert ohtaniHittingStats[0]['homeRuns'] == 22
    }

    def "get player pitching stats"() {
        setup:
        def ohtani = 660271

        when:
        def pitchingStats = mlbStatsAPIService.getPlayerPitchingStats(ohtani)

        then:
        assert pitchingStats.size() > 0
        // Ohtani had an era of 3.31 in his rookie year (2018)
        // TODO: why is this a String?
        assert pitchingStats[0]['era'] == '3.31'
    }
}
