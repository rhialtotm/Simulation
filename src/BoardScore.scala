/**
 * Created by nathan on 1/30/17.
 * used for migration to weighted scoring system
 *
 */

case class ScoreComponent(
  intValue:        Int,
  normalizedValue: Double,
  weightedValue:   Double
)

case class BoardScore(
  board:         Board,
  specification: Specification
) {

  // sum the weighted results and use this as an alternative comparison to current mechanism

  private def getScore(opt: OptimizationFactor, i: Int): ScoreComponent = {

    if (opt.enabled) {

      // todo - when we stop using the compare, we don't have to multiply these by -1 anymore
      val intValue = if (opt.minimize) i else i * -1

      // normalize the value to be from 0 to 1.  as currently all minvals are 0, there is no need to include a
      // min val in the normalization
      // if an optimization factor is supposed to a low number, then subtract it from it's max value before normalizing
      val normalizedValue: Double = if (opt.minimize) (opt.maxVal - i) / opt.maxVal.toDouble else i / opt.maxVal.toDouble

      // the weighted value is multiplied by the opt factor's weight - which in theory will guarantee goodness
      val weightedValue: Double = normalizedValue * opt.weight

      ScoreComponent(intValue, normalizedValue, weightedValue)
    } else
      ScoreComponent(0, 0.0, 0.0)

  }

  val neighbors = board.countNeighbors(Board.allLocations)

  //val occupied:Int = board.grid.popCount
  val occupiedScore: ScoreComponent = getScore(specification.occupiedOptFactor, board.grid.popCount)
  val maximizerScore: ScoreComponent = getScore(specification.maximizerOptFactor, board.maximizerCount)
  val fourNeighborsScore: ScoreComponent = getScore(specification.fourNeighborsOptFactor, neighbors(4))
  val threeNeighborsScore: ScoreComponent = getScore(specification.threeNeighborOptFactor, neighbors(3))
  val twoNeighborsScore: ScoreComponent = getScore(specification.twoNeighborsOptFactor, neighbors(2))
  val maxContiguousLinesScore: ScoreComponent = getScore(specification.maxContiguousLinesOptFactor, board.grid.maxContiguousOpenLines)
  val openLinesScore: ScoreComponent = getScore(specification.openLinesOptFactor, board.grid.openLineCount)

  // once you get the weights kicked in, this weightedSum
  // will be what you use to compare Simulations to each other
  // ideally the compare will go _a lot_ faster when using this
  val weightedSum = occupiedScore.weightedValue +
    maximizerScore.weightedValue +
    fourNeighborsScore.weightedValue +
    threeNeighborsScore.weightedValue +
    twoNeighborsScore.weightedValue +
    maxContiguousLinesScore.weightedValue +
    openLinesScore.weightedValue

  val results: Array[Int] = getResultsArray // current mechanism

  /*  val normalizedScores:Array[Double] =  // normalize each from 0 to 1
    val weightedScores:Array[Double], // apply weights from the specification
    val score:Double*/

  /*  private def getResultDoubleArray = specification.length match {
      case 5 => Array(0.0, 0.0, 0.0, 0.0, 0.0)
      case 6 => Array(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
      case 7 => Array(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
    }

    def emptyResultDoubleArray:Array[Double] = getResultDoubleArray*/

  private def getResultsArray: Array[Int] = {

    import Specification._
    def getNamedResult(name: String): Int = {
      name match {
        // multiply times -1 to make compare work without having to
        // move this._ and that._ values around
        case s if s == occupiedCountName  => this.occupiedScore.intValue
        case s if s == maximizerCountName => this.maximizerScore.intValue
        case s if s == fourNeighborsName  => this.fourNeighborsScore.intValue
        case s if s == threeNeighborsName => this.threeNeighborsScore.intValue
        case s if s == twoNeighborsName   => this.twoNeighborsScore.intValue
        case s if s == maxContiguousName  => this.maxContiguousLinesScore.intValue
        case s if s == openLinesName      => this.openLinesScore.intValue
      }
    }

    val a = BoardScore.getResultArray(specification.length)
    var i = 0

    // getResults is 3,915/second with while loop
    // vs. 3,133/second with this map - good golly
    //specification.map(spec => getResult(spec.fieldName))

    //val keys = specification.spec.keys.iterator

    while (i < specification.length) {
   // while (keys.hasNext) {

      a(i) = getNamedResult(specification.optimizationFactors(i).fieldName)
      //a(i) = getNamedResult(specification(keys.next).fieldName)
      i += 1
    }

    a

  }

}

object BoardScore {
  def getResultArray(length: Int) = length match {
    case 5 => Array(0, 0, 0, 0, 0)
    case 6 => Array(0, 0, 0, 0, 0, 0)
    case 7 => Array(0, 0, 0, 0, 0, 0, 0)
  }

}