/**
 * Created by rhialtotm on  1/15/2017.
 *
 * introducing the Specification in order to configure it on the context
 *
 *  so that tests can be run to ensure that all specification combinations
 *   (from 1 to Specification.length) and then the permutations of those
 *   combinations all result in valid games that can play
 *   This is a general test that is useful
 *
 *   Also, this paves the way to run the game by trying all of the above
 *   n times to see which combination/permutation of specifications
 *   results in the highest scores...
 */

import scala.collection.JavaConverters._

import Implicits._
import Specification._
import scala.collection.immutable.{Iterable, ListMap}

case class OptimizationFactor(
  key:         String,
  minimize:    Boolean,
  weight:      Double,
  minVal:      Int,
  maxVal:      Int,
  label:       String,
  explanation: String
)

case class Specification(random: Boolean, optFactor: Option[OptimizationFactor], constructionInfo: ConstructionInfo) {

  val boardSize: Int = constructionInfo.boardSizeInfo.boardSize

  val avoidMiddleArray: Array[Array[Int]] = {
    // creates an array of board size
    // where int values in the array as you get closer to the middle
    // get much larger
    // then we add up all board positions where a value is on

    val smallestValue = 3
    // Array.tabulate(boardSize, boardSize) { (_, _) => 0 }
    Array.tabulate(boardSize, boardSize) { (row, col) =>
      val pos: Int = {
        val rowVal = math.pow(smallestValue, if (row < boardSize / 2) row + 1 else boardSize - row).toInt
        val colVal = math.pow(smallestValue, if (col < boardSize / 2) col + 1 else boardSize - col).toInt
        rowVal.min(colVal)
      }
      pos
    }
  }

  private val avoidMiddleArraySum: Int = avoidMiddleArray.map(row => row.sum).sum

  // for readability
  private val minimize = true
  private val maximize = false

  private val totalPositions = constructionInfo.boardSizeInfo.boardPositions

  // todo - all things being equal, favor a position closer to the upper left (just add Loc Row and Col) - this will allow seeded games to repeat themselves I believe
  // todo - OptimizationFactor of all combinations of 3x3 1x5 and 5x1 - this is actually a lookahead.  dive into machine learning, m'lad

  // the following code can be paste in by running weightGenerator command line option
  // make sure that if you change any of the key names above, that you change them here as well

  // providing made up value for initial roundScoreKey feature
  private val fixedWeightMap = Map(

    "maximizerKey" -> 0.622593416953521,
    "avoidMiddleKey" -> 0.1628650146642616,
    "occupiedKey" -> 0.051514039866263454,
    "openLinesKey" -> 0.043463901967082864,
    "maxContiguousKey" -> 0.041670072171356126,
    "lineContiguousUnoccupiedKey" -> 0.03385277575559437,
    "neighborsFourKey" -> 0.01903122736923337,
    "neighborsThreeKey" -> 0.018033335297438363,
    "neighborsTwoKey" -> 0.0069762159552488,
    "roundScoreKey" -> 0.001

  )

  // magic - 5.1MM high score weights!
  /*
  {"type":"Weights",
  "maximizer":0.19974355898611665,
  "avoid middle":0.18347596377558661,
  "spaces on a line":0.16154182283466706,
  "4 neighbors":0.11850099681586584,
  "3 neighbors":0.11184045788599091,
  "connected open":0.10014732501776767,
  "occupied":0.06129258525854067,
  "open rows + cols":0.04983397323751548,
  "2 neighbors":0.013623316187948986}

   */

  // public for testing purposes
  val allFeatures = ListMap(

    // you'll need to update class Specification named optFactors below, plus the calls from BoardScore.scores if you change this

    // this one needs to run a subsequent simulation - not yet easy to do
    //allMaximizersCountName -> OptimizationFactor(allMaximizersKey, maximize, fixedWeightMap(allMaximizersKey), 0.0, maximizerArray.length, "all maximizers", "count of boards that can fit all combinations (with repeats) of 3x3, 5x1 and 1x5 pieces  - if each piece was placed on the board"),
    avoidMiddleKey -> OptimizationFactor(avoidMiddleKey, minimize, fixedWeightMap(avoidMiddleKey), 0, avoidMiddleArraySum, "avoid middle", Specification.allFeatureDescriptions(avoidMiddleKey)),
    lineContiguousUnoccupiedKey -> OptimizationFactor(lineContiguousUnoccupiedKey, minimize, fixedWeightMap(lineContiguousUnoccupiedKey), boardSize * 2, totalPositions / 2, "spaces on a line", Specification.allFeatureDescriptions(lineContiguousUnoccupiedKey)),
    maxContiguousKey -> OptimizationFactor(maxContiguousKey, maximize, fixedWeightMap(maxContiguousKey), 0, boardSize, "connected open", Specification.allFeatureDescriptions(maxContiguousKey)),
    maximizerKey -> OptimizationFactor(maximizerKey, maximize, fixedWeightMap(maximizerKey), 0, math.pow(boardSize - constructionInfo.maximizer3x3.cols + 1, 2).toInt, "maximizer", Specification.allFeatureDescriptions(maximizerKey)),
    neighborsFourKey -> OptimizationFactor(neighborsFourKey, minimize, fixedWeightMap(neighborsFourKey), 0, totalPositions / 2, "4 neighbors", Specification.allFeatureDescriptions(neighborsFourKey)),
    neighborsThreeKey -> OptimizationFactor(neighborsThreeKey, minimize, fixedWeightMap(neighborsThreeKey), 0, totalPositions / 2, "3 neighbors", Specification.allFeatureDescriptions(neighborsThreeKey)),
    // i'm really not sure that 60 is the maximum number of two neighbors that can be created on a board
    // but i couldn't find another solution that was better
    neighborsTwoKey -> OptimizationFactor(neighborsTwoKey, minimize, fixedWeightMap(neighborsTwoKey), 0, (totalPositions * .6).toInt, "2 neighbors", Specification.allFeatureDescriptions(neighborsTwoKey)),
    occupiedKey -> OptimizationFactor(occupiedKey, minimize, fixedWeightMap(occupiedKey), 0, totalPositions, "occupied", Specification.allFeatureDescriptions(occupiedKey)),
    openLinesKey -> OptimizationFactor(openLinesKey, maximize, fixedWeightMap(openLinesKey), 0, boardSize * 2, "open rows + cols", Specification.allFeatureDescriptions(openLinesKey)),
    roundScoreKey -> OptimizationFactor(roundScoreKey, maximize, fixedWeightMap(roundScoreKey), Game.numPiecesInRound, constructionInfo.maxRoundScore, "round score", Specification.allFeatureDescriptions(roundScoreKey))

  )

  private def normalizeOptimizationFactorWeights(optFactors: ListMap[String, OptimizationFactor]): ListMap[String, OptimizationFactor] = {

    // divide each current weight into the previous weight
    // initially current weights were set to 100 (to divide by 100) to mimic the comparison mechanism
    // originally used in this program
    // first pass
    // val weights = Array.tabulate(length)(n => 1 / (math.pow(10, n) * math.pow(10, n)))
    // now using weights from initialWeightFactor
    // val weights = optFactors.values.map(each => each.initialWeightFactor).scanLeft(1.0)((a, b) => a / b).tail
    val weights = optFactors.values.map(each => each.weight)

    // the idea to normalize weights this way is that when you multiply weights times the
    // the normalized values derived from creating the scores, we will get a distribution from 0 to 1
    // Brendan's idea and I like it.  Makes it easy to grok the scores
    // however, just calculating it by mapping each weight over the sum caused a bug
    // when the board clears, the scores are perfect - all normalize to 1.0
    // but when multiplying by normalized weights, we ended up with this number:
    // 1.0000000000000002
    // which is larger than one - and unacceptable :)
    // so the answer is to take the first n normalized weights and to calculate the last one by
    // subtracting the sum of the first n from 1.  And here it is:
    val sumOfWeights = weights.sum
    val initialNormalized = weights.init.map(_ / sumOfWeights)
    val last: Double = 1.0 - initialNormalized.sum
    val normalizedWeights = initialNormalized ++ Iterable(last)

    val weighted = optFactors.zip(normalizedWeights)
      .map {
        case (specEntry, weight) =>
          specEntry match {
            case (key, opt) => (key, OptimizationFactor(opt.key, opt.minimize, weight, opt.minVal, opt.maxVal, opt.label, opt.explanation))
          }
      }

    val sortedWeights = ListMap(weighted.toSeq.sortBy(-_._2.weight): _*)
    sortedWeights
  }

  // public for testing purposes
  def weightedSpecification(optFactors: ListMap[String, OptimizationFactor]): ListMap[String, OptimizationFactor] = {
    // create a specification with only the filtered parameters
    val weightedFactors = normalizeOptimizationFactorWeights(optFactors)

    weightedFactors
  }

  // following are used to construct results
  val maxOptFactorLabelLength: Int = allFeatures.values.map(_.label.length).max
  val maxOptFactorKeyLength: Int = allFeatures.values.map(_.key.length).max

  val spec: ListMap[String, OptimizationFactor] = optFactor match {

    case Some(optFactor) =>
      // get a specification just for this optimization factor used in weight generation
      val optFactorListMap = ListMap(optFactor.key -> optFactor)
      weightedSpecification(optFactorListMap)

    case None =>
      if (random) {

        val doubleRandomizer = new scala.util.Random()

        // copy the current specification into a random specification
        val randomSpec = allFeatures.map {
          case (key, opt) =>
            (key, OptimizationFactor(opt.key, opt.minimize, doubleRandomizer.nextDouble, opt.minVal, opt.maxVal, opt.label, opt.explanation))
        }
        weightedSpecification(randomSpec)
      } else
        weightedSpecification(allFeatures)
  }

  val length: Int = spec.size

  // provided so that the getNamedResult function in boardScore can iterate over a while loop
  // as using named keys into the ListMap along with apply() access is VERY SLOW
  val optimizationFactors: Array[OptimizationFactor] = spec.values.toArray

  val avoidMiddleOptFactor: OptimizationFactor = spec(Specification.avoidMiddleKey)
  val fourNeighborsOptFactor: OptimizationFactor = spec(Specification.neighborsFourKey)
  val lineContiguousOptFactor: OptimizationFactor = spec(Specification.lineContiguousUnoccupiedKey)
  val maxContiguousLinesOptFactor: OptimizationFactor = spec(Specification.maxContiguousKey)
  val maximizerOptFactor: OptimizationFactor = spec(Specification.maximizerKey)
  val occupiedOptFactor: OptimizationFactor = spec(Specification.occupiedKey)
  val openLinesOptFactor: OptimizationFactor = spec(Specification.openLinesKey)
  val roundScoreOptFactor: OptimizationFactor = spec(Specification.roundScoreKey)
  val threeNeighborOptFactor: OptimizationFactor = spec(Specification.neighborsThreeKey)
  val twoNeighborsOptFactor: OptimizationFactor = spec(Specification.neighborsTwoKey)

  def getSimulationResultsString(simulationResults: List[SimulationInfo], chosen: Simulation, bullShit: String, gamePieces: GamePieces): String = {
    // todo - add in simulations, skipped simulations, perSecond information

    val weightBufferLength = 13 - StringFormats.weightFormatLength
    val weightBuffer = " ".repeat(weightBufferLength + 3)

    val prefixWeightFormatLength = StringFormats.weightFormatLength + 3
    val piecePrefixLength = maxOptFactorLabelLength + prefixWeightFormatLength + 4

    val columnPadding = Game.numPiecesInRound * 2

    val spaceAndVertical = " " + StringFormats.VERTICAL_LINE
    val scoreColumnString = "score".rightAlignedPadded(columnPadding + 1)
    val normalizedColumnString = "normalized".rightAlignedPadded(columnPadding + 6)

    val permutationColumnWidth = ((gamePieces.widestPiece * 2 - 1) * 3) + 7
    val weightedColumnString = "weighted".rightAlignedPadded(permutationColumnWidth - (scoreColumnString.length + normalizedColumnString.length))
    val columnHeader = scoreColumnString + normalizedColumnString + weightedColumnString + spaceAndVertical

    def getPrefixString(name: String, value: String) = name.leftAlignedPadded(maxOptFactorLabelLength + 1).appendColon +
      value.rightAlignedPadded(prefixWeightFormatLength) + spaceAndVertical

    val wrappedBullShit = bullShit.wrap(piecePrefixLength, gamePieces.tallestPiece + 1, StringFormats.BRIGHT_MAGENTA)

    val piecesString = simulationResults
      .zipWithIndex
      .map { case (result, index) => result.pieces.permutationPiecesHeader(index, gamePieces) }
      .spreadHorizontal(bracketWith = StringFormats.VERTICAL_LINE + " ", separator = StringFormats.VERTICAL_LINE + " ")

    val piecesHeader = wrappedBullShit.splice(piecesString.split("\n")) + "\n"

    // get the scores from the simulationInfo
    // transpose them to get all the same score values on the same row
    val scores: List[List[ScoreComponent]] = simulationResults.map(info => info.best.board.boardScore.scores).transpose

    val horizontal = StringFormats.HORIZONTAL_LINE.repeat(piecePrefixLength) +
      (StringFormats.CROSS_PIECE + StringFormats.HORIZONTAL_LINE.repeat(columnHeader.length - 1)).repeat(simulationResults.length) +
      StringFormats.VERTICAL_AND_LEFT + "\n"

    val scoreHeader = getPrefixString("score factor", "weight") + columnHeader.repeat(simulationResults.length) + "\n"

    val scoreString = scores.map(optScores =>
      getPrefixString(optScores.head.label, optScores.head.weight.weightLabel) +

        optScores.map(score =>
          " " + score.intValue.optFactorLabel.rightAlignedPadded(columnPadding) +
            score.normalizedValue.label(2).rightAlignedPadded(columnPadding + 3)
            + weightBuffer + score.weightedValue.weightLabel)

        .mkString(" " + StringFormats.VERTICAL_LINE)).mkString(" " + StringFormats.VERTICAL_LINE + "\n") + spaceAndVertical + "\n"

    // val winner = simulationResults.map(_.best.weightedSum).max

    val weightedSumString = "sum".leftAlignedPadded(maxOptFactorLabelLength + 1).appendColon +
      scores.map(optScores => optScores.head.weight).sum.weightLabel.rightAlignedPadded(prefixWeightFormatLength) + spaceAndVertical +
      simulationResults.map { result =>

        val winner = result.best == chosen

        val sum = result.best.board.boardScore.scores.map(scoreComponent => scoreComponent.weightedValue).sum.weightLabel
        val sumWithLabel = (if (winner) "winner: " + sum else sum).rightAlignedPadded(permutationColumnWidth)
        if (winner) sumWithLabel.green else sumWithLabel.yellow

      }.mkString(spaceAndVertical) + spaceAndVertical

    val sb = new StringBuilder
    sb ++= piecesHeader
    sb ++= horizontal
    sb ++= scoreHeader
    sb ++= horizontal
    sb ++= scoreString
    sb ++= horizontal
    sb ++= weightedSumString

    sb.toString

    // piecesHeader + horizontal + scoreHeader + horizontal + scoreString + horizontal + weightedSumString
  }

  def getWeightsJSON: String = {
    val weights = this.optimizationFactors.map(factor =>
      factor.label.jsonNameValuePairLast(factor.weight)).mkString(JSONFormats.delimiter)

    ("type".jsonNameValuePair("Weights".doubleQuote) + weights).curlyBraces
  }

}

object Specification {

  val avoidMiddleKey = "avoidMiddleKey"
  val lineContiguousUnoccupiedKey = "lineContiguousUnoccupiedKey"
  val maxContiguousKey = "maxContiguousKey"
  val maximizerKey = "maximizerKey"
  val neighborsFourKey = "neighborsFourKey"
  val neighborsThreeKey = "neighborsThreeKey"
  val occupiedKey = "occupiedKey"
  val openLinesKey = "openLinesKey"
  val neighborsTwoKey = "neighborsTwoKey"
  val roundScoreKey = "roundScoreKey"

  // todo - this was only created to try to work around an initialization issue - you can put it back in allFeatures
  val allFeatureDescriptions = ListMap(

    Specification.avoidMiddleKey -> "unoccupied positions in the middle are bad so score them with a high score",
    Specification.lineContiguousUnoccupiedKey -> "number of separate spaces on a given line - indicator of how many pieces needed to clear",
    Specification.maxContiguousKey -> "number of lines (either horizontal or vertical) that are open and contiguous",
    Specification.maximizerKey -> "positions in which a 3x3 piece can fit",
    Specification.neighborsFourKey -> "number of positions surrounded on all 4 sides",
    Specification.neighborsThreeKey -> "number of positions surrounded on 3 of 4 sides",
    Specification.neighborsTwoKey -> "number of positions surrounded on 2 of 4 sides",
    Specification.occupiedKey -> "occupied positions",
    Specification.openLinesKey -> "count of open rows plus open columns",
    Specification.roundScoreKey -> "total score for the round"

  )

  def getOptimizationFactorExplanations: String = {
    // used by showGameStart
    allFeatureDescriptions.map(desc => "* " + desc._1.label + " - " + desc._2).mkString("\n")
  }

  def apply(random: Boolean, constructionInfo: ConstructionInfo): Specification = {
    Specification(random, None, constructionInfo)
  }

  def apply(optFactor: OptimizationFactor, constructionInfo: ConstructionInfo): Specification = {
    Specification(random = true, Some(optFactor), constructionInfo)
  }

  def apply(constructionInfo: ConstructionInfo): Specification = {
    Specification(random = true, None, constructionInfo)
  }

  // used in formatting results

}