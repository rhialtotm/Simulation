/**
 * Created by nathan on 12/30/16.
 * parses command line and sets defaults
 */

import org.slf4j.LoggerFactory
import org.slf4j.MDC
import scala.language.reflectiveCalls


import ch.qos.logback.classic.{ Logger,  LoggerContext }

class Context(conf: Conf) {

  private val loggerContext: LoggerContext = LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]

  val logger:Logger  = loggerContext.getLogger("root") // Logger("simulation_logger") // gets root logger - could also get it from loggerContext via loggerContext.getLogger("ROOT")

  sys.addShutdownHook(
    {
      logger.info("done")
      // val loggerContext: LoggerContext = LoggerFactory.getILoggerFactory().asInstanceOf[LoggerContext]
      loggerContext.stop()
      if (this.show) println("goodbye - i hoped you enjoyed this simulation") //}
    }
  )

  val jsonLogger:Logger = loggerContext.getLogger("json")

  // used to append a game number to a json file so we get a file per game
  def setJSONFileNameDiscriminators(gameNumber: Int, gameSeed: Int): Unit = {
    MDC.put("gameInfo", "game_" + gameNumber.toString + "_seed_" + gameSeed.toString)
  }

  val abridgedLogs:Boolean = conf.abridgedLogs()

  // vars so you can change test specifications - consider other mechanisms if you wish
  val beep: Boolean = conf.beep()

  val boardSize:Int = conf.dimensions.getOrElse(10) // default board size is 10
  val boardPositions:Int = boardSize * boardSize

  // use these values to provide numbers that when added together will be unique for any combination of the three numbers
  // this was verified in the REPL
  // take the result of the 3 added together numbers and mod them against the total number of permutations
  // this is useful because each permutation will generate most of the same locations and we only need to calculate one of them
  // so we mod by the total permutation count and see if it matches this permutation index and that is the one that is responsible
  val allLocationHashes: Array[Long] = {
    (0 until boardPositions).map(x => (math.sin(x).abs * math.pow(10, 11)).toLong).toArray
  }

  // calculate all locations for a board once - at class Board construction
  // copyBoard was originally: 21.5% of execution time with the tabulate functionality
  // moved to a while loop with a ListBuffer and that was...31.6% - worse!!
  // so moved to a recursive loop with a List - which was 9.1%
  // and finally by building the list in reverse, recursion gave it back in order...
  // so we didn't have to call reverse and end up with 4.8% of execution time
  // worth the optimization - from 21.5% to 4.8%!

  // duh - this value is invariant throughout the game
  // moved it to Board object, lazy instantiated it on first use
  // and now it is only calculated once in less than 1 ms
  // probably you can put the tabulate mechanism back if you wish
  //
  // now with getLocations calculated once, copyBoard is about 3.1% of the overall time
  private def getLocations(boardSize: Int): List[Loc] = /*Array.tabulate(layout.length, layout.length)((i, j) => (i, j)).flatten.toList*/ {

    @annotation.tailrec def loop(row: Int, col: Int, acc: List[Loc]): List[Loc] = {
      (row, col) match {
        case (-1, _) => acc
        case (_, 0)  => loop(row - 1, boardSize - 1, Loc(row, col) :: acc)
        case _       => loop(row, col - 1, Loc(row, col) :: acc)
      }
    }

    val size = boardSize
    loop(size - 1, size - 1, List())

  }

  val allLocations: Array[Loc] = getLocations(boardSize).toArray
  private val directions = Array(Loc(-1, 0), Loc(0, -1), Loc(1, 0), Loc(0, 1))

  val allLocationNeighbors: Array[Array[Loc]] = {

    // stashing all location neighbors once at the beginning rather than calculating it each time has saved about 15% of execution time:
    // on countNeighbors alone it sped up the number of times per second by 1948% (~20x)
    def getNeighbors(loc: Loc): Array[Loc] = {
      List.fill[Int](directions.length)(0).indices.map(n => Loc(loc.row + directions(n).row, loc.col + directions(n).col)).toArray
    }

    val a = allLocations.map(getNeighbors)
    a
  }



  val anySizeEmptyLineValue = 0
  val boardSizeFullLineValue = OccupancyGrid.fillerup(boardSize)

  val popTable: Array[Int] = {

    // all possible combination of on bits in a context.boardSize position row:
    // todo - refactor (theInt >> x) &1
    val countBits = (theInt: Int) => (0 until boardSize).map(x => (theInt >> x) & 1).count(_ == 1)
    (0 to 1023).map(countBits).toArray

  }

  // provides the score for clearing 1 to 6 (max possible) lines at once
  val lineClearingScore: Array[Int] = {
    val r = 1 to 6
    // Array(0,10,30,60,100,150,210)
    r.scanLeft(0)(_ + _ * boardSize).toArray
  }



  // this is pretty theoretical as to the max - it may be possible to get higher than this but I think it's pretty unlikely
  // this problem goes away if we implement z-score
  lazy val maxRoundScore =
    lineClearingScore(lineClearingScore.length - 1) +
      (Specification.maximizer3x3.pointValue * 3) +
      lineClearingScore(3)

  val fixedWeights:Boolean = conf.fixedWeights()

  var gamesToPlay: Int = conf.gamesToPlay()
  val generateWeightsGamesToPlay: Int = conf.weights.getOrElse(0)
  val generatingWeights:Boolean = generateWeightsGamesToPlay > 0

  var stopGameAtRound: Int = conf.roundsToPlay()
  //noinspection VarCouldBeVal
  var parallel: Boolean = !conf.serial()
  var logJSON: Boolean = conf.logJSON()

  var show: Boolean = !conf.hide()
  //noinspection VarCouldBeVal
  var showRoundResultsOnly: Boolean = conf.showRoundResultsOnly()

  val stopAtNewHighScore: Boolean = !conf.continueAtNewHighScore()

  private val multiGameSeed = conf.sessionSeed()
  // either we will generate a new series of games from scratch, or take the provided multi game seed to redo a previously generated series
  private val randomizer = if (multiGameSeed == 0) new scala.util.Random() else new scala.util.Random(multiGameSeed)

  // get the gameSeed from the command line
  private var gameSeed = conf.gameSeed()

  // allows overriding the next game seed - used by weight generator
  def setGameSeed(seed: Int): Unit = gameSeed = seed

  // if there is no passed in gameSeed then generate it from the randomizer,
  // otherwise use the gameSeed that is either passed in via conf or set via setGameSeed
  // appended abs as there is no need for negative numbers to make the json file name confusing
  def getGameSeed: Int = (if (gameSeed == 0) randomizer.nextInt else gameSeed).abs


  //noinspection VarCouldBeVal
  var specification: Specification = Specification(this)

  var replayGame: Boolean = false
  //noinspection VarCouldBeVal
  var ignoreSimulation: Boolean = true

  private var internalReplayListIterator = List[PieceLocCleared]().toIterator

  def setReplayList(plcList: List[PieceLocCleared]): Unit = {
    replayGame = true
    internalReplayListIterator = plcList.toIterator
  }

  def takeReplayPiecesForRound: Iterator[PieceLocCleared] = internalReplayListIterator.take(GamePieces.numPiecesInRound)

  var simulationSelfTest:Boolean = false

}

object Context {
  val FILE_HIGH_SCORE = ".highscore"
}
