package ppl.delite.runtime.profiler

import collection.mutable.{ArrayBuffer, Map, Stack}
import java.io.{BufferedWriter, File, PrintWriter, FileWriter}
import java.util.concurrent.ConcurrentHashMap
import ppl.delite.runtime.Config
import java.lang.management.ManagementFactory
import tools.nsc.io.Path

object PerformanceTimer {
  var threadCount = 0
  val threadIdToWriter = new ArrayBuffer[PrintWriter] // thread id -> thread-specific profile data file
  val threadIdToKernelCallStack = new ArrayBuffer[Stack[Timing]]
  var ticTocRegionToTiming: Map[String, Timing] = Map()

  var jvmUpTimeAtAppStart = 0L
  var appStartTimeInMillis = 0L
  var cppStartTime: Long = 0

  var statsForTrackedComponent = new ArrayBuffer[Long]()
  var isFinalRun = false

  def initializeStats(numThreads: Int) = synchronized {
    threadCount = numThreads
    for (i <- 0 until numThreads) {
      threadIdToKernelCallStack += new Stack[Timing]
    }

    if (Config.profile) {
      val profileFilePrefix = Config.profileOutputDirectory + "/profile_t_"
      Path(Config.profileOutputDirectory).createDirectory()

      for (i <- 0 until numThreads) {
        val profileFile: File = new File( profileFilePrefix + i + ".csv" )
        threadIdToWriter += new PrintWriter(profileFile)
      }

      val tmp = Config.profileOutputDirectory + "/profile_tic_toc_scala.csv"
      threadIdToWriter += new PrintWriter( new File(tmp) )
    }
  }

  def recordAppStartTimeStats() = synchronized {
    jvmUpTimeAtAppStart = ManagementFactory.getRuntimeMXBean().getUptime()
    appStartTimeInMillis = System.currentTimeMillis
  }

  def start(component: String, threadId: Int) = {
    if (isFinalRun) {
      val startTime = System.currentTimeMillis
      val stack = threadIdToKernelCallStack(threadId)
      stack.push(new Timing(startTime, component))
    }
  }

  def start(component: String): Unit = {
    val startTime = System.currentTimeMillis
    ticTocRegionToTiming += component -> new Timing(startTime, component)
  }

  def startMultiLoop(component: String, threadId: Int): Unit = {
    start(component + "_" + threadId, threadId)
  }

  def stopMultiLoop(component: String, threadId: Int): Unit = {
    stop(component + "_" + threadId, threadId)
  }

  def stop(component: String, threadId: Int) = {
    if (isFinalRun) {
      val endTime = System.currentTimeMillis
      val stack = threadIdToKernelCallStack(threadId)
      val currKernel = stack.pop()
      if (currKernel.component != component) {
        error( "cannot stop timing that doesn't exist. [TID: " + threadId + ",  Component: " + component + ",  Stack top: " + currKernel.component + "]" )
      }

      currKernel.endTime = endTime
      if (Config.profile) {
        val writer = threadIdToWriter(threadId)
        writer.println(component + "," + currKernel.startTime + "," + currKernel.elapsedMillis + "," + stack.length)
      }
    }
  }

  def stop(component: String): Unit = {
    val endTime = System.currentTimeMillis
    val t = ticTocRegionToTiming(component)
    t.endTime = endTime
    if (Config.profile && isFinalRun) threadIdToWriter(threadCount).println(component + "," + t.startTime + "," + t.elapsedMillis + ",0")
    ticTocRegionToTiming -= component
    
    if (component == Config.dumpStatsComponent) statsForTrackedComponent.append(t.elapsedMillis)

    println("[METRICS]: Time for component " + t.component + ": " + (t.elapsedMillis.toFloat / 1000).formatted("%.3f") + "s")
  }

  def stop() {
    for (w <- threadIdToWriter) { w.close() }
  }

  def clearAll() {
    initializeStats(threadCount)
  }

  def dumpStats() {
    val directory = new File(Config.statsOutputDirectory)
    if (!directory.exists) directory.mkdirs
    else if (!directory.isDirectory)
      throw new RuntimeException("statsOutputDirectory doesn't refer to a directory")      
    val timesFile = new File(directory.getCanonicalPath + File.separator  + Config.statsOutputFilename)
    if(!Config.dumpStatsOverwrite && timesFile.exists)
      throw new RuntimeException("stats file " + timesFile + " already exists")
    val fileStream = new PrintWriter(new FileWriter(timesFile))
    fileStream.println(statsForTrackedComponent.mkString("\n"))
    fileStream.close
  }

  def setCppStartTime(start: Long) {
    cppStartTime = start
  }
  
  def getNameOfCurrKernel(threadId: Int): String = {
    val stack = threadIdToKernelCallStack(threadId)
    if (stack.length > 0) {
      for (t <- stack) {
        if (!t.component.startsWith("_")) return t.component
      }
    }

    "null"
  }
  
}
