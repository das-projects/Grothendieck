package ppl.delite.runtime
package profiler

import ppl.delite.runtime.Config
import ppl.delite.runtime.graph.DeliteTaskGraph
import java.lang.management.ManagementFactory
import tools.nsc.io.Path

//front-facing interface to activate all profiling tools
object Profiling {

  private var globalStartNanos = 0L

  def init(graph: DeliteTaskGraph) {
    val totalResources = Config.numThreads + Config.numCpp + Config.numCuda + Config.numOpenCL
    PerformanceTimer.initializeStats(totalResources)
    MemoryProfiler.initializeStats(Config.numThreads, Config.numCpp, Config.numCuda, Config.numOpenCL)
  }

  def startRun() {
    PerformanceTimer.isFinalRun = true
    PerformanceTimer.recordAppStartTimeStats()

    PerformanceTimer.clearAll()
    MemoryProfiler.clearAll()

    globalStartNanos = System.nanoTime()
    
    if (Config.dumpProfile) SamplerThread.start()
  }

  def endRun() {
    if (Config.dumpProfile) SamplerThread.stop()
    PerformanceTimer.stop()
    if (Config.dumpStats) PerformanceTimer.dumpStats()
    if (Config.dumpProfile) PostProcessor.postProcessProfileData(globalStartNanos, Config.degFilePath)
  }

}
