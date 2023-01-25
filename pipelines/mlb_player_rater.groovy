import groovy.json.JsonBuilder
import groovy.json.JsonSlurperClassic

import groovy.transform.Field

@Field
def mlbTeams = []

@Field
def mlbPlayers = []

@Field
def catchers = []

@Field
def firstBaseman = []

@Field
def secondBaseman = []

@Field
def thirdBaseman = []

@Field
def shortStops = []

@Field
def pitchers = []

@Field
def outFielders = []

@Field
def designatedHitters = []

@Field
def hittingStats = [:]

@Field
def pitchingStats = [:]

node('master') {
    stage('CleanWs') {
        cleanWs()
    }

    stage('GetTeams') {
        copyArtifacts filter: 'mlb-teams.json', fingerprintArtifacts: true, projectName: 'get-mlb-teams'

        def mlbTeamsJsonStr = readFile('mlb-teams.json')

        mlbTeams = new JsonSlurperClassic().parseText(mlbTeamsJsonStr)

        mlbTeams.each {
            println it
        }
    }

    stage('GetPlayers') {
        copyArtifacts filter: '*.json', fingerprintArtifacts: true, projectName: 'get-mlb-team-rosters'
        copyArtifacts filter: '*.json', fingerprintArtifacts: true, projectName: 'get-mlb-player-hitting-stats'
        copyArtifacts filter: '*.json', fingerprintArtifacts: true, projectName: 'get-mlb-player-pitching-stats'

//        mlbTeams.each { mlbTeam ->
//            def teamCode = mlbTeam['abbreviation']
        def teamCode = 'LAA'

        def teamRosterJsonStr = readFile("${teamCode}-roster.json")
        def teamHittingStatsJsonStr = readFile("${teamCode}-hitting-stats.json")
        def teamPitchingStatsJsonStr = readFile("${teamCode}-pitching-stats.json")

        def teamRoster = new JsonSlurperClassic().parseText(teamRosterJsonStr)
        def teamHittingStats = new JsonSlurperClassic().parseText(teamHittingStatsJsonStr)
        def teamPitchingStats = new JsonSlurperClassic().parseText(teamPitchingStatsJsonStr)

        mlbPlayers.addAll(teamRoster)
        hittingStats.putAll(teamHittingStats)
        pitchingStats.putAll(teamPitchingStats)

        println mlbPlayers.collect { it['position'] }.unique()
//        }
    }

    stage('GetCatchers') {
        catchers = mlbPlayers.findAll { it['position'] == 'C' }

        def ratedCatchers = getRatedPlayer(catchers)

        writeFile file: "C-rated.json", text: new JsonBuilder(ratedCatchers).toPrettyString()
    }

    stage('GetOutFielders') {
        outFielders = mlbPlayers.findAll { ['LF', 'CF', 'RF', 'OF'].contains(it['position']) }

        def ratedPlayers = getRatedPlayer(outFielders)

        writeFile file: "OF-rated.json", text: new JsonBuilder(ratedPlayers).toPrettyString()
    }

    stage('GetDesignatedHitters') {
        designatedHitters = mlbPlayers.findAll { it['position'] == 'DH' }

        def ratedPlayers = getRatedPlayer(designatedHitters)

        writeFile file: "DH-rated.json", text: new JsonBuilder(ratedPlayers).toPrettyString()
    }

    stage('GetSecondBaseman') {
        secondBaseman = mlbPlayers.findAll { it['position'] == '2B' }

        def ratedPlayers = getRatedPlayer(secondBaseman)

        writeFile file: "2B-rated.json", text: new JsonBuilder(ratedPlayers).toPrettyString()
    }

    stage('GetShortStops') {
        shortStops = mlbPlayers.findAll { it['position'] == 'SS' }

        def ratedPlayers = getRatedPlayer(shortStops)

        writeFile file: "SS-rated.json", text: new JsonBuilder(ratedPlayers).toPrettyString()
    }

    stage('GetFirstBaseman') {
        firstBaseman = mlbPlayers.findAll { it['position'] == '1B' }

        def ratedPlayers = getRatedPlayer(firstBaseman)

        writeFile file: "1B-rated.json", text: new JsonBuilder(ratedPlayers).toPrettyString()
    }

    stage('GetThirdBaseman') {
        thirdBaseman = mlbPlayers.findAll { it['position'] == '3B' }

        def ratedPlayers = getRatedPlayer(thirdBaseman)

        writeFile file: "3B-rated.json", text: new JsonBuilder(ratedPlayers).toPrettyString()
    }

    stage('GetPitchers') {
        pitchers = mlbPlayers.findAll { it['position'] == 'P' }

        def ratedPlayers = getRatedPlayer(pitchers)

        writeFile file: "P-rated.json", text: new JsonBuilder(ratedPlayers).toPrettyString()
    }

    stage('AllPlayers') {
        def allPlayers = []

        // hitters
        allPlayers.addAll(catchers)
        allPlayers.addAll(firstBaseman)
        allPlayers.addAll(secondBaseman)
        allPlayers.addAll(shortStops)
        allPlayers.addAll(thirdBaseman)
        allPlayers.addAll(outFielders)
        allPlayers.addAll(designatedHitters)

        // pitchers (could include 2-way players)
        allPlayers.addAll(pitchers)

        allPlayers = allPlayers.sort { a, b -> b.vScore <=> a.vScore }

        def allPlayersRated = allPlayers.collectWithIndex { player, index ->
            def position = player['position']

            def ratedPlayer = [
                    "vScore"       : player['vScore'],
                    "rotoScore"    : player['rotoScore'],
                    "hittingScore" : player['hittingScore'],
                    "pitchingScore": player['pitchingScore'],
                    "nameFirst"    : player['nameFirst'],
                    "nameLast"     : player['nameLast'],
                    "position"     : position,
                    "age"          : player['age'],
                    "mlbPlayerId"  : player['mlbPlayerId'],
                    "mlbTeamId"    : player['mlbTeamId']
            ]

            if (player['seasonHittingStats']) {
                ratedPlayer.put("atBats", player['seasonHittingStats']['atBats'])
                ratedPlayer.put("runs", player['seasonHittingStats']['runs'])
                ratedPlayer.put("homeRuns", player['seasonHittingStats']['homeRuns'])
                ratedPlayer.put("rbis", player['seasonHittingStats']['rbis'])
                ratedPlayer.put("stolenBases", player['seasonHittingStats']['stolenBases'])
                ratedPlayer.put("avg", player['seasonHittingStats']['avg'])
                ratedPlayer.put("obp", player['seasonHittingStats']['obp'])
                ratedPlayer.put("slg", player['seasonHittingStats']['slg'])
                ratedPlayer.put("ops", player['seasonHittingStats']['ops'])
            }

            if (player['seasonPitchingStats']) {
                ratedPlayer.put("wins", player['seasonPitchingStats']['wins'])
                ratedPlayer.put("saves", player['seasonPitchingStats']['saves'])
                ratedPlayer.put("strikeOuts", player['seasonPitchingStats']['strikeOuts'])
                ratedPlayer.put("era", player['seasonPitchingStats']['era'])
                ratedPlayer.put("whip", player['seasonPitchingStats']['whip'])
            }

            return ratedPlayer
        }

        writeFile file: "all-rated.json", text: new JsonBuilder(allPlayersRated).toPrettyString()

        // currently used for vinny's fantasy baseball
        writeFile file: "player_ratings-rated.json", text: new JsonBuilder(['data': allPlayersRated]).toPrettyString()
    }

    stage('PlayerRatings') {
        archiveArtifacts artifacts: "*-rated.json", onlyIfSuccessful: true
    }
}

@NonCPS
def getRatedPlayer(players) {
    if (players != null && players.size() > 0) {
        println players
        println players.class

        // add roto score
        players = players.collect { catcher ->
            println "${catcher['nameFirst']} ${catcher['nameLast']}"
            assert hittingStats.get(catcher['mlbPlayerId'].toString()) != null

            // current year
            def seasonHittingStats = hittingStats.get(catcher['mlbPlayerId'].toString()).find {
                it['season'] == 2021
            }

            // current year
            def seasonPitchingStats = pitchingStats.get(catcher['mlbPlayerId'].toString()).find {
                it['season'] == 2021
            }

            catcher.put('seasonHittingStats', seasonHittingStats)
            catcher.put('seasonPitchingStats', seasonPitchingStats)
            catcher.put('hittingScore', getHittingScore(seasonHittingStats))
            catcher.put('pitchingScore', getPitchingScore(seasonPitchingStats))

            double rotoScore = (double) (catcher['hittingScore'] + catcher['pitchingScore'])

            catcher.put('rotoScore', rotoScore)

            return catcher
        }.sort { a, b -> b.rotoScore <=> a.rotoScore }

        // variables to calculate standard deviation
        def rotoScores = players.collect { (it['hittingScore'].toDouble() + it['pitchingScore'].toDouble()) }
        def rotoScoresSquared = rotoScores.collect { it * it }
        def rotoScoresSquaredSum = rotoScoresSquared.sum()

        def populationSize = rotoScores.size()

        def totalRotoScoresSquared = rotoScores.sum() * rotoScores.sum()

        def standardDeviation = Math.sqrt(
                (((populationSize * rotoScoresSquaredSum) - totalRotoScoresSquared) / (populationSize * populationSize)).doubleValue()
        )
        def mean = rotoScores.sum() / rotoScores.size()

        // add v-score
        players = players.collect { catcher ->
            println "${catcher['nameFirst']} ${catcher['nameLast']}"

            double rotoScore = (catcher['hittingScore'] + catcher['pitchingScore'])

            def vScore = ((rotoScore - mean) / standardDeviation)
            println vScore

            catcher.put('vScore', vScore)
            return catcher
        }

        return players
    }
}

@NonCPS
def getHittingScore(seasonHittingStats) {
    println seasonHittingStats

    if (seasonHittingStats != null) {
        def season = seasonHittingStats['season']

        assert season == 2021

        def atBats = seasonHittingStats['atBats']

        def runs = seasonHittingStats['runs']
        def homeRuns = seasonHittingStats['homeRuns']
        def rbis = seasonHittingStats['rbis']
        def stolenBases = seasonHittingStats['stolenBases']
        def avg = seasonHittingStats['avg']

        return (avg * atBats) + (3 * runs) + (12 * homeRuns) + (3.5 * rbis) + (6 * stolenBases)
    } else {
        return 0;
    }

////    seasonHittingStats['slg']
////    seasonHittingStats['obp']
////    seasonHittingStats['ops']
////
////    seasonHittingStats['strikeOuts']
////    seasonHittingStats['doubles']
////    seasonHittingStats['triples']
////    seasonHittingStats['hitByPitch']
////    seasonHittingStats['sacBunts']
////    seasonHittingStats['totalBases']
////    seasonHittingStats['games']
////    seasonHittingStats['teamNumber']
////    seasonHittingStats['airOuts']
////    seasonHittingStats['sacFlies']
////    seasonHittingStats['caughtStealing']
////    seasonHittingStats['baseOnBalls']
////    seasonHittingStats['numberOfPitches']
////    seasonHittingStats['hits']
////    seasonHittingStats['plateAppearances']
////    seasonHittingStats['groundOuts']
////    seasonHittingStats['leftOnBase']
////    seasonHittingStats['babip']
////    seasonHittingStats['intentionalWalks']
////    seasonHittingStats['groundIntoDoublePlay']
}

@NonCPS
def getPitchingScore(seasonPitchingStats) {
    println seasonPitchingStats

    if (seasonPitchingStats != null) {
        def season = seasonPitchingStats['season']

        assert season == 2021

        int wins = seasonPitchingStats['wins']
        double inningsPitched = Double.parseDouble(seasonPitchingStats['inningsPitched']).doubleValue()
        int saves = seasonPitchingStats['saves']
        double earnedRuns = seasonPitchingStats['earnedRuns']
        double baseOnBalls = seasonPitchingStats['baseOnBalls']
        double hits = seasonPitchingStats['hits']

        int strikeOuts = (int) seasonPitchingStats['strikeOuts']

        return (5 * strikeOuts) + (3 * inningsPitched) + (10 * wins) + (15 * saves) - (4 * baseOnBalls) - (3 * hits) - (4 * earnedRuns)
    } else {
        return 0;
    }
}
