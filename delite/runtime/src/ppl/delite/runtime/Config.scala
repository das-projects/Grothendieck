package ppl.delite.runtime

import graph.targets.OS

/**
 * Author: Kevin J. Brown
 * Date: Oct 11, 2010
 * Time: 1:43:14 AM
 *
 * Pervasive Parallelism Laboratory (PPL)
 * Stanford University
 */

object Config {

  private def getProperty(prop: String, default: String) = {
    val p1 = System.getProperty(prop)
    val p2 = System.getProperty(prop.substring(1))
    if (p1 != null && p2 != null) {
      assert(p1 == p2, "ERROR: conflicting properties")
      p1
    }
    else if (p1 != null) p1 else if (p2 != null) p2 else default
  }

  var numThreads: Int = getProperty("delite.threads", "1").toInt  /* scala target threads */
  var numCpp: Int = getProperty("delite.cpp", "0").toInt         /* cpp target threads */
  var numCuda: Int = getProperty("delite.cuda", "0").toInt        /* cuda target threads */
  var numOpenCL: Int = getProperty("delite.opencl", "0").toInt    /* opencl target threads */
  val numSlaves: Int = getProperty("delite.slaves", "0").toInt
  val pinThreads: Boolean = getProperty("delite.pinThreads", "false") != "false"
  val clusterMode: Int = if (getProperty("delite.cluster.isSlave", "false") != "false") 2 else if (numSlaves > 0) 1 else 0
  val masterAddress: String = getProperty("delite.master", "")
  val messagePort: Int = getProperty("delite.message.port", "0").toInt
  var scheduler: String = getProperty("delite.scheduler", "dynamic")
  var executor: String = getProperty("delite.executor", "default")
  val numRuns: Int = getProperty("delite.runs", "1").toInt
  val deliteHome: String = getProperty("delite.home", sys.env.getOrElse("DELITE_HOME",System.getProperty("user.dir")))
  val codeCacheHome: String = getProperty("delite.code.cache.home", deliteHome + java.io.File.separator + "generatedCache") //+ (new java.util.Random).nextInt(100).toString
  val useFsc: Boolean = getProperty("delite.usefsc", "false") != "false"
  val tempCudaMemRate: Double = getProperty("delite.tempcudamem", "0.3").toDouble         /* proportions of the cuda device memory used for temporary allocations */
  val taskQueueSize: Int = getProperty("delite.task.queue.size", "1024").toInt
  var performWalk: Boolean = getProperty("delite.walk", "true") != "false"
  var performRun: Boolean = getProperty("delite.run", "true") != "false"
  val noJVM: Boolean = getProperty("delite.nojvm", "false") != "false"
  if (noJVM) {
    numThreads = 0
    performRun = false
  }

  // memory management for C++ (refcnt or gc)
  val cppMemMgr = getProperty("delite.cpp.memmgr","malloc")
  val cppHeapSize: Long = getProperty("delite.cpp.heap.size","0") match {
    case s if s.toLowerCase.endsWith("k") => s.dropRight(1).toLong * 1024
    case s if s.toLowerCase.endsWith("m") => s.dropRight(1).toLong * 1024 * 1024
    case s if s.toLowerCase.endsWith("g") => s.dropRight(1).toLong * 1024 * 1024 * 1024
    case s => s.toLong
  }

  /* GPU optimization */
  val gpuOptTrans: Boolean = getProperty("delite.gpu.opt.trans", "false") != "false"

  /* Debug options */
  val verbose: Boolean = getProperty("delite.verbose", "false") != "false"
  val queueSize: Int = getProperty("delite.debug.queue.size", "128").toInt
  val noRegenerate: Boolean = getProperty("delite.debug.noregenerate", "false") != "false"
  val alwaysKeepCache: Boolean = noRegenerate || (getProperty("delite.debug.alwaysKeepCache", "false") != "false")
  val gpuBlackList: Array[String] = { val p = getProperty("delite.debug.gpu.blacklist",""); if(p=="") Array() else p.split(",") }
  val gpuWhiteList: Array[String] = { val p = getProperty("delite.debug.gpu.whitelist",""); if(p=="") Array() else p.split(",") }
  val gpuPerformance: Boolean = getProperty("delite.debug.gpu.perf", "false") != "false"
  val profile: Boolean = getProperty("delite.debug.profile", "false") != "false"
  val printSources: Boolean = getProperty("delite.debug.print.sources", "false") != "false"
  val memSamplingInterval: Long = getProperty("delite.debug.memSamplingInterval", "10").toLong
  val printConnection: Boolean = getProperty("delite.debug.print.connection", "false") != "false"
  var testMode: Boolean = getProperty("delite.debug.test", "false") != "false" //hack to make native libs work differently under sbt, should be removed

  var degFilePath = ""

  /* For containers, used in distributed mode */
  val slaveImage: String = getProperty("delite.slave.image", "")

  /**
   * DEG specific, set after its parsed
   * TODO: handle this more rigorously
   */
  var deliteBuildHome: String = ""

  /***********
   *	Cost Modeling
   */

	val whileCostThreshold: Int = getProperty("delite.while.threshold", "-1").toInt
	val loopCostThreshold: Int = getProperty("delite.loop.threshold", "-1").toInt
  val enableTaskParallelism: Boolean = getProperty("delite.scheduler.enableTaskParallelism", "false") != "false"

  /***********
    * Statistics and Metrics Section
    */
   val dumpStats: Boolean = getProperty("stats.dump", "false") != "false"
   val dumpStatsComponent: String = getProperty("stats.dump.component", "all")
   val dumpStatsOverwrite: Boolean = getProperty("stats.dump.overwrite", "false") != "false"

   val statsOutputDirectory: String = getProperty("stats.output.dir", "")
   if(dumpStats && statsOutputDirectory == "") error("stats.dump option enabled but did not provide a statsOutputDirectory")

   val statsOutputFilename: String = getProperty("stats.output.filename", "")
   if(dumpStats && statsOutputFilename == "") error("stats.dump option enabled but did not provide a statsOutputFilename")

   val dumpProfile: Boolean = getProperty("profile.dump", "false") != "false"
   var profileOutputDirectory: String = getProperty("profile.output.dir", "")
   if(dumpProfile && profileOutputDirectory == "") error("profile.dump option enabled but did not provide a profileOutputDirectory")

   val enablePCM = dumpProfile && (getProperty("delite.enable.pcm", "false") != "false")
   val pcmHome: String = sys.env.getOrElse("PCM_HOME", "")
   if (enablePCM && (pcmHome == "")) {
     sys.error("$PCM_HOME has not been set. Please set it when pcm flag is specified.")
   }
}
