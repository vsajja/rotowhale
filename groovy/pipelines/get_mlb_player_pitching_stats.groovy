import groovy.json.JsonBuilder
import groovy.json.JsonSlurperClassic

import groovy.transform.Field

// @Field
// def server = 'http://localhost:5051/api/v1/fantasy-baseball'

@Field
def server = 'https://baseballsite.herokuapp.com/api/v1/fantasy-baseball'

@Field
def mlbTeams = []

@Field
def roster = []

node('master') {
    stage('CleanWs') {
        cleanWs()
    }

    stage('GetTeams') {
        mlbTeams = getMlbTeams()

        mlbTeams.each {
            println it
        }
    }

    stage('GetPitchingStats') {
        // get all artifacts from last successful build
        copyArtifacts projectName: 'get-mlb-team-rosters'

        mlbTeams.each { mlbTeam ->
            def teamCode = mlbTeam['abbreviation']

            def rosterJsonStr = readFile("${teamCode}-roster.json")

            roster = new JsonSlurperClassic().parseText(rosterJsonStr)

            def fileName = "${teamCode}-pitching-stats.json"
            println "Writing file: ${fileName}"

            def pitchingStats = [:]

            roster.each { player ->
                def mlbPlayerId = player['mlbPlayerId']
                def stats = getPlayerPitchingStats(mlbPlayerId)

                println stats.collect {
                    def season = it['season']

                    def teamId = it['mlbTeamId']

                    def team = mlbTeams.find {
                        it['id'] == teamId
                    }

                    def games = it['games']
                    def gamesStarted = it['gamesStarted']
                    def wins = it['wins']
                    def losses = it['losses']
                    def saves = it['saves']
                    def blownSaves = it['blownSaves']
                    def holds = it['holds']
                    def completeGames = it['completeGames']
                    def shutouts = it['shutouts']
                    def inningsPitched = it['inningsPitched']
                    def hits = it['hits']
                    def runs = it['runs']
                    def earnedRuns = it['earnedRuns']
                    def homeRuns = it['homeRuns']
                    def baseOnBalls = it['baseOnBalls']
                    def strikeOuts = it['strikeOuts']
                    def era = it['era']
                    def whip = it['whip']
                    def avg = it['avg']

                    return "${season} ${team != null ? team['abbreviation'] : null} " +
                            "${games}G ${gamesStarted}GS " +
                            "${inningsPitched}IP " +
                            "${wins}W " +
                            "${saves}SV " +
                            "${strikeOuts}K " +
                            "${era}ERA " +
                            "${whip}WIHP ${avg}BAA"
                }.join('\n')

                pitchingStats.put(player['mlbPlayerId'], stats)
            }

            writeFile file: fileName, text: new JsonBuilder(pitchingStats).toPrettyString()
        }
    }

    stage('ArchiveHittingStats') {
        archiveArtifacts artifacts: '**-pitching-stats.json', onlyIfSuccessful: true
    }
}

def getPlayerPitchingStats(def mlbPlayerId) {
    String jsonStr = "$server/mlb/player/stats/pitching?mlbPlayerId=$mlbPlayerId".toURL().text

    def pitchingStats = new JsonSlurperClassic().parseText(jsonStr)

    return pitchingStats
}

def getMlbTeams() {
    String jsonStr = "$server/mlb/teams".toURL().text

    mlbTeams = new JsonSlurperClassic().parseText(jsonStr)

    assert mlbTeams.size() == 30

    return mlbTeams
}
