package ppl.delite.runtime.profiler

import collection.mutable
import java.io.{BufferedWriter, File, PrintWriter, FileWriter}
import ppl.delite.runtime.Config
import scala.collection.mutable.{ArrayBuffer, HashMap, Stack}

class MemoryAccessStats(l2HitRatio: Double, l2Misses: Int, l3HitRatio: Double, l3Misses: Int) {
  var l2CacheHitRatio: Double = l2HitRatio
  var l2CacheMisses: Int = l2Misses
  var l3CacheHitRatio: Double = l3HitRatio
  var l3CacheMisses: Int = l3Misses
  //var bytesReadFromMC: Double = bytesReadFromMem
}

object MemoryProfiler {
  var threadCount = 0
  var kernelToMemUsageMaps = new ArrayBuffer[Map[String, Long]]()
  var kernelToTotMemUsage = Map[String, Long]()
  var threadToId: Map[String, Int] = Map()
  var kernelToMemAccessStats = Map[String, ArrayBuffer[MemoryAccessStats]]()

  var cppTidStart = -1
  var numCppThreads = 0
  var numScalaThreads = 0
  var numCudaThreads = 0
  var numOpenCLThreads = 0

  def initializeStats(numScala: Int, numCpp:Int, numCuda: Int, numOpenCL: Int) = synchronized {
    val numThreads = numScala + numCpp + numCuda + numOpenCL
    threadCount = numThreads

    if (numCpp > 0) {
      cppTidStart = numScala
    }

    numCppThreads = numCpp
    numScalaThreads = numScala
    numCudaThreads = numCuda
    numOpenCLThreads = numOpenCL

    for (i <- List.range(0, numThreads)) {
      val threadName = "ExecutionThread" + i
      threadToId += threadName -> i
      kernelToMemUsageMaps += Map[String, Long]()
    }

    threadToId += "main" -> numThreads
  }

  def addMemoryAccessStats(
        sourceContext: String, threadId: Int,
        l2CacheHitRatio: Double, l2CacheMisses: Int,
        l3CacheHitRatio: Double, l3CacheMisses: Int): Unit = {
    val stats = new MemoryAccessStats( l2CacheHitRatio, l2CacheMisses, l3CacheHitRatio, l3CacheMisses )
    if ( !kernelToMemAccessStats.contains( sourceContext ) ) {
      kernelToMemAccessStats += sourceContext -> new ArrayBuffer[MemoryAccessStats]()
    }

    kernelToMemAccessStats( sourceContext ).append( stats )
  }

  def addCppKernelMemUsageStats( kernel:String, memUsage: Long ) {
    if (!kernelToTotMemUsage.contains( kernel )) { 
      kernelToTotMemUsage += kernel -> 0
    }

    kernelToTotMemUsage += kernel -> ( memUsage + kernelToTotMemUsage( kernel ) )
  }

  def clearAll() {
    kernelToMemUsageMaps.clear()
    threadToId = Map()
    kernelToMemAccessStats = Map[String, ArrayBuffer[MemoryAccessStats]]()
    initializeStats(numScalaThreads, numCppThreads, numCudaThreads, numOpenCLThreads)
  }

  def logArrayAllocation(component: String, threadId: Int, arrayLength: Int, elemType: String) = {
    val currKernel = PerformanceTimer.getNameOfCurrKernel(threadId) 
  
    if (!kernelToMemUsageMaps( threadId ).contains( currKernel )) {
      kernelToMemUsageMaps( threadId ) += currKernel -> 0
    }

    val arrayMemSize = arrayLength.toLong * sizeOf( elemType ).toLong
    val tmp = kernelToMemUsageMaps( threadId )( currKernel )
    kernelToMemUsageMaps( threadId ) += currKernel -> ( tmp + arrayMemSize )
  }

  def aggregateMemAllocStatsFromAllThreads(): Map[String, Long] = {
    for (m <- kernelToMemUsageMaps) {
      for (kv <- m) {
        var kernel = kv._1
        if (!kernelToTotMemUsage.contains( kernel )) { 
          kernelToTotMemUsage += kernel -> 0
        }

        kernelToTotMemUsage += kernel -> ( kv._2 + kernelToTotMemUsage( kernel ) )
      }
    }

    kernelToTotMemUsage
  }

  def aggregateMemAccessStats() : Map[String, MemoryAccessStats] = {
    var res = Map[String, MemoryAccessStats]()
    kernelToMemAccessStats.foreach( kv => {
      val aggrStats = new MemoryAccessStats(0,0,0,0)
      for (s <- kv._2) {
        aggrStats.l2CacheHitRatio += s.l2CacheHitRatio
        aggrStats.l2CacheMisses += s.l2CacheMisses
        aggrStats.l3CacheHitRatio += s.l3CacheHitRatio
        aggrStats.l3CacheMisses += s.l3CacheMisses
      }

      val len = kv._2.length
      aggrStats.l2CacheHitRatio /= len
      aggrStats.l2CacheMisses /= len
      aggrStats.l3CacheHitRatio /= len
      aggrStats.l3CacheMisses /= len
      
      res += kv._1 -> aggrStats
    } )

    res
  } 

  def sizeOf(elemType: String) = elemType match {
    case "boolean" | "byte" => 1
    case "char" | "short" => 2
    case "int" | "float" => 4
    case "double" | "long" => 8
    case _ => 64
  }
}
