/**
 * Created by nathan on 12/30/16.
 * parses command line and sets defaults
 */

import com.typesafe.scalalogging.Logger

class Context(conf: Conf) {

  val logger = Logger("simulation_logger")
  logger.info("starting simulation")


  // vars so you can change test specifications - consider other mechanisms if you wish
  val beep: Boolean = conf.beep()
  var gamesToPlay: Int = conf.gamesToPlay()
  val generateWeightsGamesToPlay:Int = conf.weightGenerator()
  //noinspection VarCouldBeVal
  var maxSimulations: Int = conf.maxSimulations()
  var stopGameAtRound: Int = conf.endGameAtRound()
  var parallel: Boolean = conf.parallel()
  var show: Boolean = conf.show()
  var showRoundResultsOnly:Boolean = conf.showRoundResultsOnly()

  //noinspection VarCouldBeVal
  var showWorst: Boolean = conf.showWorst()

  val stopAtNewHighScore:Boolean = conf.stopAtNewHighScore()

  // TODO - always set a random seed and then log it with the game score so you can replay that game with a new algo if you wish
  var randomSeed: Int = conf.randomSeed()

  //noinspection VarCouldBeVal
  var specification: Specification = Specification()

  var replayGame: Boolean = false
  //noinspection VarCouldBeVal
  var ignoreSimulation: Boolean = true

  private var internalReplayListIterator = List[PieceLocCleared]().toIterator

  def setReplayList(plcList: List[PieceLocCleared]): Unit = {
    replayGame = true
    internalReplayListIterator = plcList.toIterator
  }
  def takeReplayPiecesForRound: Iterator[PieceLocCleared] = internalReplayListIterator.take(3)

}

object Context {
  val FILE_HIGH_SCORE = ".highscore"
  val FILE_SAVED_GAME = ".simulationSavedGame"
}
