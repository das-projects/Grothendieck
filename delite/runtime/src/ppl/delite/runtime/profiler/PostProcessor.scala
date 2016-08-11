package ppl.delite.runtime
package profiler

import java.io.{File, PrintWriter}
import scala.collection.mutable.{HashSet, HashMap}
import _root_.scala.util.parsing.json.JSON
import tools.nsc.io.Path

object PostProcessor {
  
  val TARGET_SCALA = 1
  val TARGET_CPP = 2
  val TARGET_CUDA = 4
  val tabs = "   "
  val twoTabs = tabs + tabs

  val profileDBFileName = "profile.db"
  val uiDataFileName = "profileDataUI.js"
  val gcStatsFileName = "gcStats.txt"

  // For debugging
  def time[R](block: => R, msg: String): R = {
    val t0 = System.nanoTime()
    val result = block    // call-by-name
    val t1 = System.nanoTime()
    println("[TIME] " + msg + ": " + ((t1 - t0)/1000000) + "ms")
    result
  }

  def processDegFile(degFile: String): DependencyGraph = {
    DependencyGraph.dependencyGraphNew(degFile)
  }

  def processRawProfileDataFile(depGraph: DependencyGraph, profileOutputDirectory: String): ExecutionProfile = {
    val executionProfile: ExecutionProfile = new ExecutionProfile(depGraph, profileOutputDirectory)
    executionProfile.init()

    executionProfile
  }

  def postProcessProfileData(globalStartNanos: Long, degFile: String) {
    val depGraph = processDegFile(degFile)
    val executionProfile = processRawProfileDataFile(depGraph, Config.profileOutputDirectory)

    executionProfile.writeDNodesToDB()
    executionProfile.writeExecutionSummariesToDB()
    executionProfile.writeTicTocTNodesToDB()
    executionProfile.writeTicTocNodeSummariesToDB()
    executionProfile.writeKernelMemAccessStatsToDB()
    executionProfile.writeAppDataToDB()
    executionProfile.close()

    dumpProcessedData(globalStartNanos, depGraph, executionProfile)  
  }

  private def dumpProcessedData(globalStartNanos: Long, depGraph: DependencyGraph, executionProfile: ExecutionProfile) {
    val directory = Path( Config.profileOutputDirectory ).createDirectory()
    val file = directory / uiDataFileName
    var writer = new PrintWriter( file.jfile )

    writer.println( "{\"Profile\":{" )

    executionProfile.dumpAppDataInJSON( writer, tabs )
    depGraph.dumpDepGraphInJSON( writer, tabs )
    executionProfile.dumpTimelineDataInJSON( writer, tabs )
    SamplerThread.dumpMemUsageSamplesInJSON( writer, tabs )

    writer.println( "}}" )
    writer.close()
  }
}
