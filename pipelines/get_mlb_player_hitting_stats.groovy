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

    stage('GetHittingStats') {
        // get all artifacts from last successful build
        copyArtifacts projectName: 'get-mlb-team-rosters'

        mlbTeams.each { mlbTeam ->
            def teamCode = mlbTeam['abbreviation']

            def rosterJsonStr = readFile("${teamCode}-roster.json")

            roster = new JsonSlurperClassic().parseText(rosterJsonStr)

            def fileName = "${teamCode}-hitting-stats.json"

            def hittingStats = [:]

            roster.each { player ->

                def mlbPlayerId = player['mlbPlayerId']

                def stats = getPlayerHittingStats(mlbPlayerId)

                println stats.collect {
                    def season = it['season']

                    def teamId = it['mlbTeamId']

                    def team = mlbTeams.find {
                        it['id'] == teamId
                    }

                    def games = it['games']
                    def atBats = it['atBats']
                    def runs = it['runs']
                    def homeRuns = it['homeRuns']
                    def rbis = it['rbis']
                    def stolenBases = it['stolenBases']
                    def avg = it['avg']
                    def obp = it['obp']
                    def slg = it['slg']
                    def ops = it['ops']

                    return "${season} ${team != null ? team['abbreviation'] : null} ${games}G  ${atBats}AB ${runs}R ${homeRuns}HR ${rbis}RBI ${stolenBases}SB ${avg}/${obp}/${slg}/${ops}"
                }.join('\n')

                hittingStats.put(player['mlbPlayerId'], stats)
            }

            println "Writing: $fileName"
            writeFile file: fileName, text: new JsonBuilder(hittingStats).toPrettyString()
        }
    }

    stage('ArchiveHittingStats') {
        archiveArtifacts artifacts: '**-hitting-stats.json', onlyIfSuccessful: true
    }
}

def getPlayerHittingStats(def mlbPlayerId) {
    String jsonStr = "$server/mlb/player/stats/hitting?mlbPlayerId=$mlbPlayerId".toURL().text

    def hittingStats = new JsonSlurperClassic().parseText(jsonStr)

    return hittingStats
}

def getMlbTeams() {
    String jsonStr = "$server/mlb/teams".toURL().text

    mlbTeams = new JsonSlurperClassic().parseText(jsonStr)

    assert mlbTeams.size() == 30

    return mlbTeams
}
