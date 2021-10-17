# mlb-player-rater

The MLB Player rater is a simple front-end only project

We load player ratings from a static JSON data file (data/player_ratings.json) and displays them using [datatables.net](https://datatables.net/).

Datatables adds the ability to search, filter and export player ratings and automatically group players into tiers based on a positional calculated ~~Z-Score~~ V-Score.

The overall website is based on a free HTML5 Up template called [Massively](https://html5up.net/massively) to quickly get up and running. This way, it's mobile friendly and responsive without too much effort.

![2021 Player Rater](/images/player_rater_preview.png)

# data/player_ratings.json

```
{
  "data": [
    {
      "pitchingScore": 294.0,
      "vScore": 7.741556930893517,
      "rotoScore": 1285.979,
      "nameFirst": "Shohei",
      "pitchHand": "R",
      "mlbDebutDate": "2018-03-29",
      "weight": 210,
      "birthCity": "Oshu",
      "birthDate": "1994-07-05",
      "heightFt": 6,
      "nameLast": "Ohtani",
      "bats": "L",
      "mlbPlayerId": 660271,
      "heightInches": 4,
      "jerseyNumber": 17,
      "birthCountry": "Japan",
      "seasonPitchingStats": {
        ... <snipped>
      },
      "seasonHittingStats": {
        ... <snipped>

      },
      "position": "P",
      "age": 27,
      "hittingScore": 991.979
    }
    ... <snipped>
    ]
}
```

# how is the player_ratins.json file generated?

The player_ratings.json file is generated using the following steps.

Stage 1: Use the MLB Stats API to get the current fantasy baseball player pool (40 man rosters)

1. MLB Stats API: `getMLBTeams()`
2. MLB Stats API: `get40ManRosters(mlbTeamId)`
3. MLB Stats API: `getPlayerHittingStats(mlbPlayerId)`
4. MLB Stats API: `getPlayerPitchingStats(mlbPlayerId)`

Stage 2: Calculate the Roto Score

5. Calculate: `hittingScore = calculateHittingScore(mlbPlayerId)`
6. Calculate: `pitchingScore = calculatePitchingScore(mlbPlayerId)`
7. Calculate: `rotoScore = pitchingScore + hittingScore`

Stage 3: Once we have a set of rotoScores for a player set, we can calculate the zScore.

```
// variables to calculate standard deviation
def rotoScores = players.collect { (it['hittingScore'].toDouble() + it['pitchingScore'].toDouble()) }
def rotoScoresSquared = rotoScores.collect { it * it }
def rotoScoresSquaredSum = rotoScoresSquared.sum()

def populationSize = rotoScores.size()

def totalRotoScoresSquared = rotoScores.sum() * rotoScores.sum()
```

```
def standardDeviation = Math.sqrt(
    (((populationSize * rotoScoresSquaredSum) - totalRotoScoresSquared) / (populationSize * populationSize)).doubleValue())
```

```
def mean = rotoScores.sum() / rotoScores.size()
```

```
ratedCatchers = catchers.collect { catcher ->
    double rotoScore = (catcher['hittingScore'] + catcher['pitchingScore'])

    def vScore = ((rotoScore - mean) / standardDeviation)
    println vScore

    catcher.put('vScore', vScore)
    return catcher
}

ratedPlayers.add(ratedCatchers)
```

Note that this is server side pseudocode not included in this project. It is a high level overview for now.

# MLB Stats API

Please note that use of MLB data from the MLB Stats API (the data/player_ratings.json file) is subject to the following copyright agreement.

```

"Copyright 2021 MLB Advanced Media, L.P. Use of any content on this page acknowledges agreement to the terms posted here http://gdx.mlb.com/components/copyright.txt"
```
