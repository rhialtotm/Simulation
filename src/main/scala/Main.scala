/**
 * Created by nathan on 12/9/16.
 * Main is just the stub to get it all off the ground
 * renamed object to simulate so when sbt/packInstall is run
 * you can run this from ~/local/bin/simulate
 *
 * i haven't resolved the issue that i have a class name that i like called simulation
 * so while the whole project is called simulation, you have to run it with
 *
 * $ simulate
 *
 * at the command line otherwise a module called simulation would shadow the class called simulation
 * todo - think about a new name for simulation class
 */

//noinspection ScalaFileName

object simulate {

  def main(args: Array[String]): Unit = {

    val conf = new Conf(args)
    val context = new Context(conf)

    conf match {

      case c if c.weightGenerator1() > 0 => GameRunner.generateWeights(context, false)
      case c if c.weightGenerator2() > 0 => GameRunner.generateWeights(context, true)
      case c if c.printPieces()          => new GamePieces(seed = 0).printPossiblePieces(context)
      case _                             => GameRunner.play(context)

    }

  }

}

