package ppl.delite.runtime.codegen

import kernels.cpp.CppMultiLoopHeaderGenerator
import ppl.delite.runtime.graph.DeliteTaskGraph
import ppl.delite.runtime.Config
import ppl.delite.runtime.executor.DeliteExecutable
import ppl.delite.runtime.graph.ops.Sync
import ppl.delite.runtime.graph.targets.Targets
import ppl.delite.runtime.scheduler.{OpHelper, StaticSchedule, PartialSchedule}
import ppl.delite.runtime.DeliteMesosExecutor

/**
 * Author: Kevin J. Brown
 * Date: Dec 2, 2010
 * Time: 9:41:08 PM
 *
 * Pervasive Parallelism Laboratory (PPL)
 * Stanford University
 */

object Compilers {

  def apply(target: Targets.Value): CodeCache = {
    target match {
      case Targets.Scala => ScalaCompile
      case Targets.Cuda => CudaCompile
      case Targets.OpenCL => OpenCLCompile
      case Targets.Cpp => CppCompile
      case _ => throw new RuntimeException("Undefined target for runtime compilers")
    }
  }

  def compileSchedule(graph: DeliteTaskGraph): StaticSchedule = {
    CodeCache.clearChecksum() //about to create new code so temporarily invalidate the cache

    //generate executable(s) for all the ops in each proc
    //TODO: this is a poor method of separating CPU from GPU, should be encoded - essentially need to loop over all nodes
    val schedule = graph.schedule

    val scalaSchedule = schedule.slice(0, Config.numThreads)

    SavedEnvironmentGenerator.generateEnvironment(graph)

    //TODO: Fix this!
    if(Config.clusterMode != 2 || Config.numCuda == 0)
      ScalaExecutableGenerator.makeExecutables(scalaSchedule, graph)
    else
      new ScalaMainExecutableGenerator(0, graph).makeExecutable(PartialSchedule(1).apply(0))

    if (Config.numCpp > 0) {
      val cppSchedule = schedule.slice(Config.numThreads, Config.numThreads+Config.numCpp)
      CppExecutableGenerator.makeExecutables(cppSchedule, graph)
    }

    if (Config.numCuda > 0) {
      val cudaSchedule = schedule.slice(Config.numThreads+Config.numCpp, Config.numThreads+Config.numCpp+Config.numCuda)
      if (cudaSchedule.map(_.size).foldLeft(0)(_ + _) == 0)
        System.err.println("[WARNING]: no kernels scheduled on cuda")
      CudaExecutableGenerator.makeExecutables(cudaSchedule, graph)
    }

    if (Config.printSources) { //DEBUG option
      ScalaCompile.printSources()
      CppCompile.printSources()
      CudaCompile.printSources()
      OpenCLCompile.printSources()
    }

    if (Config.numCpp>0 && graph.targets(Targets.Cpp)) CppCompile.compile()
    if (Config.numCuda>0 && graph.targets(Targets.Cuda)) CudaCompile.compile()
    if (Config.numOpenCL>0 && graph.targets(Targets.OpenCL)) OpenCLCompile.compile()

    val classLoader = ScalaCompile.compile()
    DeliteMesosExecutor.classLoader = classLoader

    // clear for the next run (required to run tests correctly)
    if (Config.numCpp > 0) {
      CppExecutableGenerator.clear()
      CppMultiLoopHeaderGenerator.clear()
    }
    if (Config.numCuda > 0) {
      CudaExecutableGenerator.clear()
    }
    
    val expectedResources = for (i <- 0 until schedule.numResources if !schedule(i).isEmpty) yield i
    val executables = createSchedule(classLoader, ScalaExecutableGenerator.getPackage(graph), schedule.numResources, expectedResources)

    CodeCache.addChecksum() //if we've reached this point everything has compiled and loaded successfully
    executables
  }

  def createSchedule(classLoader: ClassLoader, path: String, numResources: Int, expectedResources: Seq[Int]) = {
    val queues = StaticSchedule(numResources)
    val prefix = if (path == "") "" else path+"."
    for (i <- expectedResources) {
      val cls = classLoader.loadClass(prefix+"Executable"+i) //load the Executable class
      val executable = cls.getMethod("self").invoke(null).asInstanceOf[DeliteExecutable] //retrieve the singleton instance
      queues(i) += executable
    }
    queues
  }
}
