import groovy.json.JsonBuilder
import groovy.json.JsonSlurperClassic
import groovy.transform.Field

//@Field
//def server = 'http://localhost:5051/api/v1/fantasy-baseball'

@Field
def server = 'https://baseballsite.herokuapp.com/api/v1/fantasy-baseball'

@Field
def mlbTeams = []

node('master') {
    stage('GetTeams') {
        mlbTeams = getMlbTeams()
        mlbTeams.each {
            println it
        }
    }

    stage('SaveTeams') {
        writeFile file: 'mlb-teams.json', text: new JsonBuilder(mlbTeams).toPrettyString()
    }

    stage('ArchiveTeams') {
        archiveArtifacts artifacts: 'mlb-teams.json', onlyIfSuccessful: true
    }
}

def getMlbTeams() {
    String jsonStr = "$server/mlb/teams".toURL().text

    mlbTeams = new JsonSlurperClassic().parseText(jsonStr)

    assert mlbTeams.size() == 30

    return mlbTeams
}
