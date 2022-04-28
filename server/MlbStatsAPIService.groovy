package org.baseballsite.services

import groovy.json.JsonSlurper
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class MlbStatsAPIService {
    final Logger log = LoggerFactory.getLogger(this.class)

    def MLB_STATS_API_BASE_URL = 'https://statsapi.mlb.com/api/v1'

    def MLB_SPORT_CODE = 1

    // league ids
    def int AL = 103
    def int NL = 104

    // AL division ids
    def AL_EAST = 201
    def AL_CENTRAL = 202
    def AL_WEST = 200

    def NL_EAST = 204
    def NL_CENTRAL = 205
    def NL_WEST = 203

    def LEAGUE_LEVELS = [
            'Independent Leagues',
            'Winter Leagues',
            'Rookie Advanced',
            'Class A Short Season',
            'Class A',
            'Class A Advanced',
            'Double-A',
            'Triple-A',
            'Major League Baseball'
    ]


    def getMlbTeams() {
        String jsonStr = "$MLB_STATS_API_BASE_URL/teams?sportCode=${MLB_SPORT_CODE}".toURL().text

        def teamsObj = new JsonSlurper().parseText(jsonStr)

        def mlbTeams = teamsObj['teams'].findAll {
            it['league'] &&
                    it['division'] &&
                    it['sport'] &&
                    it['sport']['name'] != 'National Basketball Association'
        }.collect { team ->
            return [
                    'team'           : team['name'],
                    'abbreviation'   : team['abbreviation'],
                    'division'       : team['division']['name'],
                    'league'         : team['league']['name'],
                    'level'          : team['sport']['name'],
                    'firstYearOfPlay': Integer.parseInt(team['firstYearOfPlay']),
                    'divisionId'     : team['division']['id'],
                    'leagueId'       : team['league']['id'],
                    'id'             : team['id']
            ]
        }.findAll { it.level == 'Major League Baseball' }

        return mlbTeams
    }

    def getMlbRoster(def mlbTeamId) {
        log.info("Getting 40 man roster for mlbTeamId: $mlbTeamId")

        String jsonStr = "$MLB_STATS_API_BASE_URL/teams/${mlbTeamId}/roster?rosterType=40Man".toURL().text
        def jsonObj = new JsonSlurper().parseText(jsonStr)
        def teamRoster = []

        log.info("Processing 40 man roster for mlbTeamId: $mlbTeamId")

        teamRoster = jsonObj.roster.collect { player ->
            Integer mlbPlayerId = player['person']['id']
            Integer jerseyNumber = player['jerseyNumber'] != '' ? Integer.parseInt(player['jerseyNumber']) : null
            String playerLink = player['person']['link']

            // use the playerLink to get more player details
            def playerInfo = new JsonSlurper().parseText(
                    "https://statsapi.mlb.com/${playerLink}".toURL().text
            )

            String nameFirst = playerInfo['people']['useName'][0]
            String nameLast = playerInfo['people']['lastName'][0]

            log.info("Processing player: $nameFirst $nameLast")

            String birthDate = playerInfo['people']['birthDate'][0]
            Integer age = playerInfo['people']['currentAge'][0]

            String birthCity = playerInfo['people']['birthCity'][0]
            String birthCountry = playerInfo['people']['birthCountry'][0]
            Integer heightFt = Integer.parseInt((playerInfo['people']['height'][0].split(' ')[0] - "'").trim())
            Integer heightInches = Integer.parseInt(playerInfo['people']['height'][0].split(' ')[1] - "\"".trim())
            Integer weight = playerInfo['people']['weight'][0]
            String position = playerInfo['people']['primaryPosition']['abbreviation'][0]
            String mlbDebutDate = playerInfo['people']['mlbDebutDate'][0]

            String pitchHand = playerInfo['people']['pitchHand']['code'][0]
            String bats = playerInfo['people']['batSide']['code'][0]

            return [
                    'mlbTeamId'   : mlbTeamId,
                    'mlbPlayerId' : mlbPlayerId,
                    'jerseyNumber': jerseyNumber,
                    'nameFirst'   : nameFirst,
                    'nameLast'    : nameLast,
                    'birthDate'   : birthDate,
                    'age'         : age,
                    'birthCity'   : birthCity,
                    'birthCountry': birthCountry,
                    'heightFt'    : heightFt,
                    'heightInches': heightInches,
                    'weight'      : weight,
                    'position'    : position,
                    'mlbDebutDate': mlbDebutDate,
                    'pitchHand'   : pitchHand,
                    'bats'        : bats
            ]
        }

        return teamRoster
    }

    def getPlayerHittingStats(def mlbPlayerId) {
        Integer startYear = null
        Integer endYear = Integer.valueOf(Calendar.getInstance().get(Calendar.YEAR));

        // use the playerLink to get more player details
        def playerInfo = new JsonSlurper().parseText(
                "$MLB_STATS_API_BASE_URL/people/${mlbPlayerId}".toURL().text
        )['people'][0]

        def hittingStats = []

        def mlbDebutDate = playerInfo['mlbDebutDate']

        // does this player have any major league stats?
        if (mlbDebutDate != null) {
            startYear = Integer.parseInt(mlbDebutDate.split('-')[0])

            log.info("getPlayerHittingStats: ${playerInfo['firstName']} ${playerInfo['lastName']} (${startYear}-${endYear}) ${mlbPlayerId}")

            for (year in (startYear..endYear)) {
                def jsonStr = "$MLB_STATS_API_BASE_URL/people/${mlbPlayerId}?hydrate=stats(group=[hitting],type=season,season=${year})".toURL().text
                def jsonObj = new JsonSlurper().parseText(jsonStr)

                // some players (pitchers) don't have hitting stats
                if (jsonObj['people']['stats'][0] != null) {
                    // println new JsonBuilder(jsonObj['people']).toString()

                    def splits = jsonObj['people']['stats'][0]['splits'][0]

                    def stats = []

                    // split season (player on multiple teams)
                    if (splits.size() > 1) {
                        splits.eachWithIndex { splitSeasonStats, teamNumber ->
                            splitSeasonStats.putAt('teamNumber', teamNumber)
                            stats.add(splitSeasonStats)
                        }
                    } else {
                        stats.add(splits[0])
                    }

                    stats.each { stat ->
                        def seasonStats = stat['stat']
                        def team = stat['team']
                        def teamNumber = stat['teamNumber'] != null ? stat['teamNumber'] : 0
                        def season = Integer.parseInt(stat['season'])

                        def hittingStatLine = parseSeasonHittingStats(seasonStats)

                        // set the team the player played for this season (player can be on multiple teams)
                        if (team != null) {
                            def mlbTeamId = stat['team']['id']
                            hittingStatLine['mlbTeamId'] = mlbTeamId
                        } else {
                            // combined stats for entire season, no team
                        }

                        // set mlb player id & season
                        hittingStatLine['mlbPlayerId'] = mlbPlayerId
                        hittingStatLine['season'] = season
                        hittingStatLine['teamNumber'] = teamNumber

                        hittingStats.add(hittingStatLine)
                    }
                }
            }
        }

        return hittingStats
    }

    def parseSeasonHittingStats(seasonStats) {
        def games = seasonStats['gamesPlayed']
        def atBats = seasonStats['atBats']
        def runs = seasonStats['runs']
        def homeRuns = seasonStats['homeRuns']
        def rbis = seasonStats['rbi']
        def stolenBases = seasonStats['stolenBases']
        def caughtStealing = seasonStats['caughtStealing']
        def avg = Double.parseDouble(seasonStats['avg'])
        def obp = Double.parseDouble(seasonStats['obp'])
        def slg = Double.parseDouble(seasonStats['slg'])
        def ops = Double.parseDouble(seasonStats['ops'])
        def doubles = seasonStats['doubles']
        def triples = seasonStats['triples']
        def hits = seasonStats['hits']
        def strikeOuts = seasonStats['strikeOuts']
        def baseOnBalls = seasonStats['baseOnBalls']
        def intentionalWalks = seasonStats['intentionalWalks']
        def groundOuts = seasonStats['groundOuts']
        def airOuts = seasonStats['airOuts']
        def hitByPitch = seasonStats['hitByPitch']
        def groundIntoDoublePlay = seasonStats['groundIntoDoublePlay']
        def numberOfPitches = seasonStats['numberOfPitches']
        def plateAppearances = seasonStats['plateAppearances']
        def totalBases = seasonStats['totalBases']
        def leftOnBase = seasonStats['leftOnBase']
        def sacBunts = seasonStats['sacBunts']
        def sacFlies = seasonStats['sacFlies']
        def babip = seasonStats['babip'] != '.---' ? Double.parseDouble(seasonStats['babip']) : null

        return [
                'games'               : games,
                'atBats'              : atBats,
                'runs'                : runs,
                'homeRuns'            : homeRuns,
                'rbis'                : rbis,
                'stolenBases'         : stolenBases,
                'caughtStealing'      : caughtStealing,
                'avg'                 : avg,
                'obp'                 : obp,
                'slg'                 : slg,
                'ops'                 : ops,
                'doubles'             : doubles,
                'triples'             : triples,
                'hits'                : hits,
                'strikeOuts'          : strikeOuts,
                'baseOnBalls'         : baseOnBalls,
                'intentionalWalks'    : intentionalWalks,
                'groundOuts'          : groundOuts,
                'airOuts'             : airOuts,
                'hitByPitch'          : hitByPitch,
                'groundIntoDoublePlay': groundIntoDoublePlay,
                'numberOfPitches'     : numberOfPitches,
                'plateAppearances'    : plateAppearances,
                'totalBases'          : totalBases,
                'leftOnBase'          : leftOnBase,
                'sacBunts'            : sacBunts,
                'sacFlies'            : sacFlies,
                'babip'               : babip
        ]
    }

    def getPlayerPitchingStats(mlbPlayerId) {
        Integer startYear = null
        Integer endYear = Integer.valueOf(Calendar.getInstance().get(Calendar.YEAR));

        // use the playerLink to get more player details
        def playerInfo = new JsonSlurper().parseText(
                "$MLB_STATS_API_BASE_URL/people/${mlbPlayerId}".toURL().text
        )['people'][0]

        def mlbDebutDate = playerInfo['mlbDebutDate']

        def pitchingStats = []

        // does this player have any major league stats?
        if (mlbDebutDate != null) {
            startYear = Integer.parseInt(mlbDebutDate.split('-')[0])

            log.info("getPlayerPitchingStats: ${playerInfo['firstName']} ${playerInfo['lastName']} (${startYear}-${endYear}) ${mlbPlayerId}")

            for (year in (startYear..endYear)) {
                def jsonStr = "$MLB_STATS_API_BASE_URL/people/${mlbPlayerId}?hydrate=stats(group=[pitching],type=season,season=${year})".toURL().text
                def jsonObj = new JsonSlurper().parseText(jsonStr)

                // some players (pitchers) don't have hitting stats
                if (jsonObj['people']['stats'][0] != null) {
                    // println new JsonBuilder(jsonObj['people']).toString()

                    def splits = jsonObj['people']['stats'][0]['splits'][0]

                    def stats = []

                    // split season (player on multiple teams)
                    if (splits.size() > 1) {
                        splits.eachWithIndex { splitSeasonStats, teamNumber ->
                            splitSeasonStats.putAt('teamNumber', teamNumber)
                            // println splitSeasonStats.toString()
                            stats.add(splitSeasonStats)
                        }
                    } else {
                        stats.add(splits[0])
                    }

                    stats.each { stat ->
                        def seasonStats = stat['stat']
                        def team = stat['team']
                        def teamNumber = stat['teamNumber'] != null ? stat['teamNumber'] : 0
                        def season = Integer.parseInt(stat['season'])

                        def pitchingStatLine = parseSeasonPitchingStats(seasonStats)

                        // println seasonStats.toString()

                        // set the team the player played for this season (player can be on multiple teams)
                        if (team != null) {
                            def mlbTeamId = stat['team']['id']
                            pitchingStatLine['mlbTeamId'] = mlbTeamId
                        } else {
                            // combined stats for entire season, no team
                        }

                        // set mlb player id & season
                        pitchingStatLine['mlbPlayerId'] = playerInfo['mlbPlayerId']
                        pitchingStatLine['season'] = season
                        pitchingStatLine['teamNumber'] = teamNumber

                        pitchingStats.add(pitchingStatLine)
                    }
                }
            }
        }

        return pitchingStats
    }

    def parseSeasonPitchingStats(seasonStats) {
        println seasonStats.toString()

        def games = seasonStats['gamesPlayed']
        def gamesStarted = seasonStats['gamesStarted']
        def wins = seasonStats['wins']
        def losses = seasonStats['losses']
        def saves = seasonStats['saves']
        def blownSaves = seasonStats['blownSaves']
        def holds = seasonStats['holds']
        def completeGames = seasonStats['completeGames']
        def shutouts = seasonStats['shutouts']

        def inningsPitched = seasonStats['inningsPitched']
        def hits = seasonStats['hits']
        def runs = seasonStats['runs']
        def earnedRuns = seasonStats['earnedRuns']
        def homeRuns = seasonStats['homeRuns']
        def baseOnBalls = seasonStats['baseOnBalls']
        def strikeOuts = seasonStats['strikeOuts']
        def era = seasonStats['era']
        def whip = seasonStats['whip']
        def avg = seasonStats['avg']

        return [
                games         : seasonStats['gamesPlayed'],
                gamesStarted  : seasonStats['gamesStarted'],
                wins          : seasonStats['wins'],
                losses        : seasonStats['losses'],
                saves         : seasonStats['saves'],
                blownSaves    : seasonStats['blownSaves'],
                holds         : seasonStats['holds'],
                completeGames : seasonStats['completeGames'],
                shutouts      : seasonStats['shutouts'],
                inningsPitched: seasonStats['inningsPitched'],
                hits          : seasonStats['hits'],
                runs          : seasonStats['runs'],
                earnedRuns    : seasonStats['earnedRuns'],
                homeRuns      : seasonStats['homeRuns'],
                baseOnBalls   : seasonStats['baseOnBalls'],
                strikeOuts    : seasonStats['strikeOuts'],
                era           : seasonStats['era'],
                whip          : seasonStats['whip'],
                avg           : seasonStats['avg']
        ]

//    def gamesFinished = seasonStats['gamesFinished']
//    def homeRunsPer9 = seasonStats['homeRunsPer9']
//    def triples = seasonStats['triples']
//    def catchersInterference = seasonStats['catchersInterference']
//    def saveOpportunities = seasonStats['saveOpportunities']
//    def strikeoutsPer9Inn = seasonStats['strikeoutsPer9Inn']
//    def inheritedRunnersScored = seasonStats['inheritedRunnersScored']
//    def totalBases = seasonStats['totalBases']
//    def airOuts = seasonStats['airOuts']
//    def sacFlies = seasonStats['sacFlies']
//    def strikePercentage = seasonStats['strikePercentage']
//    def balks = seasonStats['balks']
//    def numberOfPitches = seasonStats['numberOfPitches']
//    def slg = seasonStats['slg']
//    def groundOuts = seasonStats['groundOuts']
//    def ops = seasonStats['ops']
//    def stolenBasePercentage = seasonStats['stolenBasePercentage']
//    def doubles = seasonStats['doubles']
//    def wildPitches = seasonStats['wildPitches']
//    def groundOutsToAirouts = seasonStats['groundOutsToAirouts']
//    def intentionalWalks = seasonStats['intentionalWalks']
//    def groundIntoDoublePlay = seasonStats['groundIntoDoublePlay']
//    def inheritedRunners = seasonStats['inheritedRunners']
//    def walksPer9Inn = seasonStats['walksPer9Inn']
//    def hitByPitch = seasonStats['hitByPitch']
//    def sacBunts = seasonStats['sacBunts']
//    def outs = seasonStats['outs']
//    def stolenBases = seasonStats['stolenBases']
//    def strikes = seasonStats['strikes']
//    def atBats = seasonStats['atBats']
//    def caughtStealing = seasonStats['caughtStealing']
//    def gamesPitched = seasonStats['gamesPitched']
//    def hitBatsmen = seasonStats['hitBatsmen']
//    def strikeoutWalkRatio = seasonStats['strikeoutWalkRatio']
//    def runsScoredPer9 = seasonStats['runsScoredPer9']
//    def hitsPer9Inn = seasonStats['hitsPer9Inn']
//    def battersFaced = seasonStats['battersFaced']
//    def pitchesPerInning = seasonStats['pitchesPerInning']
//    def obp = seasonStats['obp']
//    def winPercentage = seasonStats['winPercentage']

        return seasonStats
    }
}
