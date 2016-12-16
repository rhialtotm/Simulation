/**
 * Created by nathan on 12/10/16.
 * Game will hold a reference to the current board and will also invoke simulations
 * algo research - measure of entropy is a decent approach:
 *    http://stats.stackexchange.com/questions/17109/measuring-entropy-information-patterns-of-a-2d-binary-matrix
 *    Picture D is problematic - want to avoid
 *
 *    Kevin suggests maximize largest free space region(s) - and weight free space algo with the entropy calculation
 *    penalize smaller free space regions - i.e., a 1
 *
 *    Kevin also suggests:
 *    TODO: After selecting the set of boardsets that clear the most rows/cols, filter out any boardsets that don't allow
 *    Todo: HorizontalLine5, VerticalLine5, BixBox
 *
 *    Thanks Kevin :)
 *
 *    Todo: save every move in a game so you can replay it if it's awesome
 *    Todo: persist and display high score to compare this against history
 *
 *
 */
import GameUtil._

object Game {

  object GameOver extends Exception

  private val MAX_SIMULATION_ITERATIONS =  2000000000l
  private val CONTINUOUS_MODE = true

  private val board: Board = new Board(10)
  private var score: Int = 0
  private val rowsCleared: BufferedIterator[Long] = longIter.buffered
  private val colsCleared: BufferedIterator[Long] = longIter.buffered
  private val rounds: BufferedIterator[Long] = longIter.buffered
  private val placed: BufferedIterator[Long] = longIter.buffered

  def run(): Unit = {

    try {

      do {

        println("\nRound: " + (rounds.next + 1))

        // get 3 random pieces
        val pieces = List.fill(3)(Piece.getRandomPiece)

        // val pieces = Piece.getNamedPieces("Box","BigLowerRightEl","HorizontalLine3")


        // show the pieces in the order they were randomly chosen
        showPieces(pieces)

        // set up a test of running through all orderings of piece placement
       // and for each orering, trying all combinations of legal locations by trying them all out
       val permutations2 = pieces
         .permutations
         .toList
         .map(pieceSequenceSimulation(_,MAX_SIMULATION_ITERATIONS)).minBy(_._1)

       permutations2._2.foreach(tup => handleThePiece(tup._1, tup._2, board.placeKnownLegal) )


        showBoardFooter()

      } while (CONTINUOUS_MODE || (!CONTINUOUS_MODE && (Console.in.read != 'q')) )

    } catch {

      case GameOver => // normal game over
      case e: Throwable => println("abnormal run termination:\n" + e.toString)

    } finally {
      showEndGame()
    }
  }

  private def pieceSequenceSimulation(pieces:List[Piece], maxIters:Long): (Int, List[(Piece, Option[(Int, Int)])], Board)  = {

    val t1 = System.currentTimeMillis()
    val l = longIter.buffered

    val p1 = pieces.head
    val p2 = pieces(1)
    val p3 = pieces(2)

    def placeMe(piece: Piece, theBoard: Board, loc: (Int, Int)): Board = {
      val boardCopy = copyBoard(List(piece), theBoard)
      boardCopy.simulatePlacement(piece, loc)
    }

    def createOptions: List[(Int, Option[(Int,Int)], Option[(Int,Int)], Option[(Int,Int)], Board)] = {

      val listBuffer1 = new scala.collection.mutable.ListBuffer[(Int, Option[(Int,Int)], Option[(Int,Int)], Option[(Int,Int)], Board)]
      val listBuffer2 = new scala.collection.mutable.ListBuffer[(Int, Option[(Int,Int)], Option[(Int,Int)], Option[(Int,Int)], Board)]
      val listBuffer3 = new scala.collection.mutable.ListBuffer[(Int, Option[(Int,Int)], Option[(Int,Int)], Option[(Int,Int)], Board)]

      for (loc1 <- this.board.legalPlacements(p1).par) {
        if (l.head < maxIters) {
          val board1Copy = placeMe(p1, this.board, loc1)
          synchronized {
            listBuffer1 append ((board1Copy.occupiedCount, Some(loc1), None, None, board1Copy))
          }

          for (loc2 <- board1Copy.legalPlacements(p2).par) {
            if (l.head < maxIters) {

              val board2Copy = placeMe(p2, board1Copy, loc2)
              synchronized {
                listBuffer2 append ((board2Copy.occupiedCount, Some(loc1), Some(loc2), None, board2Copy))
              }

              for (loc3 <- board2Copy.legalPlacements(p3).par) {
                if (l.head < maxIters)  {

                  val board3Copy = placeMe(p3, board2Copy, loc3)
                  synchronized {
                    listBuffer3 append ((board3Copy.occupiedCount, Some(loc1), Some(loc2), Some(loc3), board3Copy))
                  }

                  // we are limiting the maximum number of tries with this code as right now it is not very performant
                  // todo: Make this performant
                  // todo: make this recursive...
                  /*if ((l.head)==maxIters) throw SimulationOver*/
                  l.next()

                }
              }
            }
          }
        }
      }


      // if we have a 3 piece solution we should use it as two piece and one piece solutions mean the game is over
      // at least along this particular simulation path
      if (listBuffer3.nonEmpty)
        listBuffer3.toList
      else if (listBuffer2.nonEmpty)
        listBuffer2.toList
      else if (listBuffer1.nonEmpty)
        listBuffer1.toList
      else
        // arbitrary large number so that this option will never wih against
        // options that are still viable
        List((100000,None,None,None, this.board))

    }


    if (board.occupiedCount == 0) {
      println("bypassing simulation for empty grid")
      val legal1 = board.legalPlacements(p1)
      val board1 = placeMe(p1, board, legal1(0))
      val legal2 = board1.legalPlacements(p2)
      val board2 = placeMe(p2, board1, legal2(0))
      val legal3 = board2.legalPlacements(p3)
      val board3 = placeMe(p3, board2, legal3(0))
      (board3.occupiedCount, List((p1, Some(legal1(0))), (p2, Some(legal2(0))), (p3, Some(legal3(0)))), board3)
    }
    else {

      val options = createOptions
      val a = options.minBy(_._1)
      val b = options.maxBy(_._1)

      val simulCount = "%,7d".format(l.head)

      val t2 = System.currentTimeMillis

      val duration = t2 - t1
      val durationString = "%,7d".format(duration)

      println("simulations: " + simulCount
        + " min: " + a._1 + " max: " + b._1
        + " - pieces: " + pieces.map(_.name).mkString(", ")
        + ":" + durationString + "ms" )


      (a._1, List((p1, a._2), (p2, a._3), (p3, a._4)), a._5 )
    }
  }

/*  // this is an attemp to make a recursive solution, but I couldn't make it go
  // also tried one like the findQueens example, but still no go.  
  // the non-recursive solution specifies precise ordering that does work.   keep trying.
  // ask others
  private def recursiveSimulationAttempt1(pieces:List[Piece]):List[(Board, Piece, Option[(Int, Int)])] = {

    val boardCopy = copyBoard(pieces, this.board)
    // for every position on boardCopy, evaluate all legal positions for the n passed in pieces




    def getOne(copy: Board, piece:Piece): (Board, Piece, Option[(Int, Int)]) = {

      val legal = copy.legalPlacements(piece)
      if (legal.isEmpty)
        (copy, piece, None)
      else
      {
        boardCopy.tryPlacementSimulation(piece,legal.head)
         (copy, piece,Some(legal.head))
      }
    }

    for {piece <- pieces}
      yield getOne(boardCopy, piece)

  }*/

  
/*  // so for each piece in the list
  // f: make a board and iterate against all legal locations to create a solution
  // a solution which is a List of 3 ordered piece / location combinations - we need to keep track of the boards for all 3
  // - the third board will be the one we compare to see which is the best solution
  // - at least one solution must have location
  
  // couldn't get this recursive solution to work either

  // List[List[(Piece, Option[(Int,Int)])]]

  private def recursiveSimulationAttempt2(pieces: List[Piece]):  List[List[(Piece, (Int, Int), Board)]]  = {

    // accept a list of Pieces, return a list of Piece, Location, Board tuples
    // then for each piece, select the best Piece / location combo based on Board outcomes

    def simulate(pieces: List[Piece], simulBoard:Board = this.board):  List[List[(Piece, (Int, Int), Board)]] = {
      if (pieces.isEmpty)
        List(List())
      else {
        val piece = pieces.head

        for {
          solutions <- simulate(pieces.tail, simulBoard)
          loc <- simulBoard.legalPlacements(piece).take(2)
          solution = (piece, loc, simulBoard)

          if (simulBoard.tryPlacementSimulation(pieces.head, loc)) // try placement simulation will be invoked on this simulation copy - so no worries
        } yield solution :: solutions
      }
    }

    simulate(pieces, copyBoard(pieces, this.board))

  }*/

  private def copyBoard(pieces: List[Piece], aBoard: Board): Board = Board.copy("board: " + pieces.map(_.name).mkString(", "), aBoard)


  private def handleThePiece(piece: Piece, loc: Option[(Int, Int)], f: (Piece, Option[(Int,Int)]) => Boolean ): Unit = {

    println("\nAttempting piece: " + ((placed.next % 3) + 1) + "\n" + piece.toString)

    if (!f(piece, loc)) throw GameOver // this will be caught by the run method do loop otherwise start aggregating...

    piece.usage.next
    score += piece.pointValue

    // placing a piece puts underlines on it to highlight it
    println(board)

    // so now clear any previously underlined cells for the next go around
    // Todo: if place piece returned the location that it was placed, this could be used to do a clear underlines
    // Todo: as right now clearUnderlines just loops through the whole board which is between 91 to 99 operations to many
    // Todo: potential operation
    clearPieceUnderlines()

    handleLineClearing()

    println("Score: " + score)
    println
  }

  private def handleLineClearing() = {

    val result = board.clearLines()

    if (result._1 > 0 || result._2 > 0) {

      def printme(i: Int, s: String): Unit = if (i > 0) println("cleared " + i + " " + s + (if (i > 1) "s" else ""))

      printme(result._1, "row")
      printme(result._2, "column")

      // show an updated board reflecting the cleared lines
      println("\n" + board)

      def incrementCleared(count: Int, it: Iterator[Long]):Unit = for (i <- 0 until count) it.next
      incrementCleared(result._1, rowsCleared)
      incrementCleared(result._2, colsCleared)

    }

    score += (result._1 + result._2) * board.layout.length
  }

  private def clearPieceUnderlines() = {
    for {
      row <- board.layout
      cell <- row
      if cell.underline
    } cell.underline = false
  }

  private def showEndGame() = {

    println
    val sFormat = "%18s: %,6d"

    Piece.pieces.sortBy(piece => (piece.usage.head, piece.name))
      .foreach { piece => println(sFormat.format(piece.name, piece.usage.next)) }

    println("\nGAME OVER!!\n")

    println(sFormat.format("Final Score", score))
    println(sFormat.format("Pieces Used", placed.head))
    println(sFormat.format("Rows Cleared", rowsCleared.head))
    println(sFormat.format("Cols Cleared", colsCleared.head))
    println(sFormat.format("Rounds", rounds.head))

  }

  private def showBoardFooter() = {
    println("\nAfter " + rounds.head + " rounds"
      + " - rows cleared: " + rowsCleared.head
      + " - columns cleared: " + colsCleared.head
      + " - positions occupied: " + board.occupiedCount  )
    if (!CONTINUOUS_MODE)
      println("type enter to place another piece and 'q' to quit")
  }

  private def showPieces(pieces: List[Piece]): Unit = {

    // we'll need the height of the tallest piece as a guard for shorter pieces where we need to
    // print out spaces as placeholders.  otherwise array access in the for would be broken
    // if we did some magic to append fill rows to the pieces as strings array...
    val tallestPieceHeight = pieces.map(piece => piece.rows).reduceLeft((a, b) => if (a > b) a else b)

    // because we're not printing out one piece, but three across, we need to split
    // the toString from each piece into an Array.  In this case, we'll create a List[Array[String]]
    val piecesToStrings = pieces map { piece =>

      val a = piece.toString.split('\n')
      if (a.length < tallestPieceHeight)
        a ++ Array.fill(tallestPieceHeight - a.length)(piece.printFillString) // fill out the array
      else
        a // just return the array

    }

    println("Candidate pieces:")

    // turn arrays into a list so you can transpose them
    // transpose will create a list of 1st rows, a list of 2nd rows, etc.
    // then print them out - across and then newline delimit
    piecesToStrings.map(a => a.toList)
      .transpose
      .foreach { l => print(l.mkString); println }

    println

  }

}