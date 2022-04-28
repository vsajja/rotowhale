import groovy.json.JsonBuilder
import groovy.json.JsonSlurperClassic

import groovy.transform.Field

// @Field
// def server = 'http://localhost:5051/api/v1/fantasy-baseball'

@Field
def server = 'https://baseballsite.herokuapp.com/api/v1/fantasy-baseball'

@Field
def mlbTeams = []

node('master') {
    stage('GetTeams') {
        def mlbTeamsJsonStr = getMlbTeams()
        mlbTeams.each {
            println it
        }
    }

    stage('GetRosters') {
        mlbTeams.each { mlbTeam ->
            def mlbTeamId = mlbTeam['id']
            // get 40 man roster
            def roster = getMlbRoster(mlbTeamId)

            def fileName = "${mlbTeam['abbreviation']}-roster.json"
            println "Writing file: ${fileName}"
            writeFile file: fileName, text: new JsonBuilder(roster).toPrettyString()
        }
    }

    stage('ArchiveRosters') {
        mlbTeams.each { mlbTeam ->
            def artifactName = "${mlbTeam['abbreviation']}-roster.json"
            println "Archiving file: ${artifactName}"
            archiveArtifacts artifacts: artifactName, onlyIfSuccessful: true
        }
    }
}

def getMlbTeams() {
    String jsonStr = "$server/mlb/teams".toURL().text

    mlbTeams = new JsonSlurperClassic().parseText(jsonStr)

    assert mlbTeams.size() == 30

    return mlbTeams
}

def getMlbRoster(def mlbTeamId) {
    String jsonStr = "$server/mlb/team/rosters?mlbTeamId=${mlbTeamId}".toURL().text

    def players = new JsonSlurperClassic().parseText(jsonStr)

    return players
}
