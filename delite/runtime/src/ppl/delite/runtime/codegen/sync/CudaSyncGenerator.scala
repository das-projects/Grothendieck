package ppl.delite.runtime.codegen.sync

import ppl.delite.runtime.graph.ops._
import collection.mutable.ArrayBuffer
import ppl.delite.runtime.graph.targets.{OS, Targets}
import ppl.delite.runtime.codegen._
import ppl.delite.runtime.scheduler.OpHelper._
import ppl.delite.runtime.graph.targets.Targets._
import ppl.delite.runtime.Config
import ppl.delite.runtime.graph._

trait CudaSyncProfiler extends CudaExecutableGenerator {
  protected def withProfile(sync: Sync)(emitSync: => Unit) {
    def getKernelName: String = this match {
      case n:NestedGenerator => n.nested.id
      case _ => "null"
    }

    val syncOpName = sync match {
      case s:Send => throw new RuntimeException("Only receiver is profiling the sync")
      case r:Receive => "__sync-ExecutionThread" + r.to.scheduledResource + "-" + getKernelName + "-" + r.sender.from.id + "-" + r.sender.from.scheduledResource
    }
    if (Config.profile) out.append("DeliteCudaTimerStart(" + Targets.getRelativeLocation(location) + ",\"" + syncOpName + "\");\n")
    emitSync
    if (Config.profile) out.append("DeliteCudaTimerStop(" + Targets.getRelativeLocation(location) + ",\"" + syncOpName + "\");\n")
  }
}

trait HostToScalaSync extends SyncGenerator with CudaExecutableGenerator {

  def hostSyncGen = hostGenerator.asInstanceOf[CppSyncGenerator]

  override protected def receiveData(s: ReceiveData) {
    (scheduledTarget(s.sender.from), scheduledTarget(s.to)) match {
      case (Targets.Scala, Targets.Cpp) => hostSyncGen.addSync(s)
      case _ => super.receiveData(s)
    }
  }

  override protected def receiveView(s: ReceiveView) {
    (scheduledTarget(s.sender.from), scheduledTarget(s.to)) match {
      case (Targets.Scala, Targets.Cpp) => hostSyncGen.addSync(s)
      case _ => super.receiveView(s)
    }
  }

  override protected def awaitSignal(s: Await) {
    (scheduledTarget(s.sender.from), scheduledTarget(s.to)) match {
      case (Targets.Scala, Targets.Cpp) => hostSyncGen.addSync(s)
      case _ => super.awaitSignal(s)
    }
  }

  override protected def receiveUpdate(s: ReceiveUpdate) {
    (scheduledTarget(s.sender.from), scheduledTarget(s.to)) match {
      case (Targets.Scala, Targets.Cpp) => hostSyncGen.addSync(s)
      case _ => super.receiveUpdate(s)
    }
  }

  override protected def sendData(s: SendData) {
    if ((scheduledTarget(s.from) == Targets.Cpp) && (s.receivers.map(_.to).filter(r => getHostTarget(scheduledTarget(r)) == Targets.Scala).nonEmpty))
      hostSyncGen.addSync(s)
    super.sendData(s)
  }

  override protected def sendView(s: SendView) {
    if ((scheduledTarget(s.from) == Targets.Cpp) && (s.receivers.map(_.to).filter(r => getHostTarget(scheduledTarget(r)) == Targets.Scala).nonEmpty))
      hostSyncGen.addSync(s)
    super.sendView(s)
  }

  override protected def sendSignal(s: Notify) {
    if ((scheduledTarget(s.from) == Targets.Cpp) && (s.receivers.map(_.to).filter(r => getHostTarget(scheduledTarget(r)) == Targets.Scala).nonEmpty))
      hostSyncGen.addSync(s)
    super.sendSignal(s)
  }

  override protected def sendUpdate(s: SendUpdate) {
    if ((scheduledTarget(s.from) == Targets.Cpp) && (s.receivers.map(_.to).filter(r => getHostTarget(scheduledTarget(r)) == Targets.Scala).nonEmpty))
      hostSyncGen.addSync(s)
    super.sendUpdate(s)
  }

  override protected[codegen] def writeSyncObject() {
    hostSyncGen.writeSyncObject()
    super.writeSyncObject()
  }
}

trait CudaToScalaSync extends SyncGenerator with CudaExecutableGenerator with JNIFuncs with CudaSyncProfiler {

  private val syncList = new ArrayBuffer[Send]
  
  override protected def receiveData(s: ReceiveData) {
    getHostTarget(scheduledTarget(s.sender.from)) match {
      //case Targets.Scala => withProfile(s) { writeGetter(s.sender.from, s.sender.sym, s.to, false) }
      case Targets.Scala => writeGetter(s.sender.from, s.sender.sym, s.to, false)
      case _ => super.receiveData(s)
    }
  }

  override protected def receiveView(s: ReceiveView) {
    getHostTarget(scheduledTarget(s.sender.from)) match {
      case Targets.Scala => withProfile(s) { writeGetter(s.sender.from, s.sender.sym, s.to, true) }
      case _ => super.receiveView(s)
    }
  }

  override protected def awaitSignal(s: Await) {
    getHostTarget(scheduledTarget(s.sender.from)) match {
      case Targets.Scala => withProfile(s) { writeAwaiter(s.sender.from) }
      case _ => super.awaitSignal(s)
    }
  }

  override protected def receiveUpdate(s: ReceiveUpdate) {
    getHostTarget(scheduledTarget(s.sender.from)) match {
      case Targets.Scala =>
        withProfile(s) {
          s.sender.from.mutableInputsCondition.get(s.sender.sym) match {
            case Some(lst) =>
              out.append("if(")
              out.append(lst.map(c => c._1.id.split('_').head + "_cond=="+c._2).mkString("&&"))
              out.append(") {\n")
              writeAwaiter(s.sender.from, s.sender.sym); writeRecvUpdater(s.sender.from, s.sender.sym)
              out.append("}\n")
            case _ =>
              writeAwaiter(s.sender.from, s.sender.sym); writeRecvUpdater(s.sender.from, s.sender.sym)
          }
        } 
      case _ => super.receiveUpdate(s)
    }
  }

  override protected def sendData(s: SendData) {
    if ((scheduledTarget(s.from) == Targets.Cuda) && (s.receivers.map(_.to).filter(r => getHostTarget(scheduledTarget(r)) == Targets.Scala).nonEmpty)) {
      writeSetter(s.from, s.sym, false)
      syncList += s
    }
    super.sendData(s)
  }

  override protected def sendView(s: SendView) {
    if ((scheduledTarget(s.from) == Targets.Cuda) && (s.receivers.map(_.to).filter(r => getHostTarget(scheduledTarget(r)) == Targets.Scala).nonEmpty)) {
      writeSetter(s.from, s.sym, true)
      syncList += s
    }
    super.sendView(s)
  }

  override protected def sendSignal(s: Notify) {
    if ((scheduledTarget(s.from) == Targets.Cuda) && (s.receivers.map(_.to).filter(r => getHostTarget(scheduledTarget(r)) == Targets.Scala).nonEmpty)) {
      writeNotifier(s.from)
      syncList += s
    }
    super.sendSignal(s)
  }

  override protected def sendUpdate(s: SendUpdate) {
    if ((scheduledTarget(s.from) == Targets.Cuda) && (s.receivers.map(_.to).filter(r => getHostTarget(scheduledTarget(r)) == Targets.Scala).nonEmpty)) {
      s.from.mutableInputsCondition.get(s.sym) match {
        case Some(lst) =>
          out.append("if(")
          out.append(lst.map(c => c._1.id.split('_').head + "_cond=="+c._2).mkString("&&"))
          out.append(") {\n")
          writeSendUpdater(s.from, s.sym)
          writeNotifier(s.from, s.sym)
          out.append("}\n")
        case _ =>
          writeSendUpdater(s.from, s.sym)
          writeNotifier(s.from, s.sym)
      }
      syncList += s
    }
    super.sendUpdate(s)
  }

  private def writeGetter(dep: DeliteOP, sym: String, to: DeliteOP, view: Boolean) {
    val ref = if (isPrimitiveType(dep.outputType(sym))) "" else "*"
    val devType = dep.outputType(Targets.Cuda, sym)
    val hostType = dep.outputType(Targets.Cpp, sym)
    out.append("%s %s%s;\n".format(devType,ref,getSymDevice(dep,sym)))
    out.append("{\n")
    out.append(getJNIType(dep.outputType(sym)))
    out.append(' ')
    out.append(getSymCPU(sym))
    out.append(" = ")
    out.append("env")
    out.append(location)
    out.append("->CallStatic")
    out.append(getJNIFuncType(dep.outputType(sym)))
    out.append("Method(cls")
    out.append(dep.scheduledResource)
    out.append(",env")
    out.append(location)
    out.append("->GetStaticMethodID(cls")
    out.append(dep.scheduledResource)
    out.append(",\"get")
    out.append(location)
    out.append('_')
    out.append(getSym(dep,sym))
    out.append("\",\"()")
    out.append(getJNIOutputType(dep.outputType(Targets.Scala,sym)))
    out.append("\"));\n")
    if (view) {
      out.append("%s %s%s = recvViewCPPfromJVM_%s(env%s,%s);\n".format(hostType,ref,getSymHost(dep,sym),mangledName(hostType),location,getSymCPU(sym)))
      out.append("%s = sendCuda_%s(%s);\n".format(getSymDevice(dep,sym),mangledName(devType),getSymHost(dep,sym)))
    }
    else if(isPrimitiveType(dep.outputType(sym))) {
      out.append("%s %s = (%s)%s;\n".format(devType,getSymHost(dep,sym),devType,getSymCPU(sym)))
      out.append("%s = (%s)%s;\n".format(getSymDevice(dep,sym),devType,getSymHost(dep,sym)))
    }
    else {
      out.append("%s %s%s = recvCPPfromJVM_%s(env%s,%s);\n".format(hostType,ref,getSymHost(dep,sym),mangledName(hostType),location,getSymCPU(sym)))
      //FIXIT: Using the length symbol for transpose is not safe in general because the symbol may not be emitted yet
      if(Config.gpuOptTrans) {
        // Use transpose copy
        dep.stencilOrElse(sym)(Empty) match {
          case Interval(start,stride,length) => out.append("%s = sendCudaTrans_%s(%s,%s);\n".format(getSymDevice(dep,sym),mangledName(devType),getSymHost(dep,sym),getSymDevice(dep,length.trim)))
          case _ => out.append("%s = sendCuda_%s(%s);\n".format(getSymDevice(dep,sym),mangledName(devType),getSymHost(dep,sym)))
        }
      }
      else
        out.append("%s = sendCuda_%s(%s);\n".format(getSymDevice(dep,sym),mangledName(devType),getSymHost(dep,sym)))
      out.append("cudaMemoryMap->insert(std::pair<void*,std::list<void*>*>(")
      out.append(getSymDevice(dep,sym))
      out.append(",lastAlloc));\n")
      out.append("lastAlloc = new std::list<void*>();\n")
    }
    out.append("}\n")
  }

  private def writeAwaiter(dep: DeliteOP, sym: String = "") {
    out.append("env")
    out.append(location)
    out.append("->CallStaticVoid")
    out.append("Method(cls")
    out.append(dep.scheduledResource)
    out.append(",env")
    out.append(location)
    out.append("->GetStaticMethodID(cls")
    out.append(dep.scheduledResource)
    out.append(",\"get")
    out.append(location)
    out.append('_')
    if(sym == "") out.append(getOpSym(dep))
    else out.append(getOpSym(dep)+getSym(dep,sym))
    out.append("\",\"()V")
    out.append("\"));\n")
  }

  private def writeRecvUpdater(dep: DeliteOP, sym:String) {
    val hostType = dep.inputType(Targets.Cpp, sym)
    val devType = dep.inputType(Targets.Cuda, sym)
    assert(!isPrimitiveType(dep.inputType(sym)))
    out.append("recvUpdateCPPfromJVM_%s(env%s,%s,%s);\n".format(mangledName(hostType),location,getSymCPU(sym),getSymHost(dep,sym)))
    out.append("sendUpdateCuda_%s(%s, %s);\n".format(mangledName(devType),getSymDevice(dep,sym),getSymHost(dep,sym)))
  }

  private def writeSetter(op: DeliteOP, sym: String, view: Boolean) {
    val hostType = op.outputType(Targets.Cpp, sym) 
    val devType = op.outputType(Targets.Cuda, sym)
    out.append("{\n")
    if (view) {
      out.append("%s *%s = recvCuda_%s(%s);\n".format(hostType,getSymHost(op,sym),mangledName(devType),getSymDevice(op,sym)))
      out.append("%s %s = sendViewCPPtoJVM_%s(env%s,%s);\n".format(getJNIType(op.outputType(sym)),getSymCPU(sym),mangledName(hostType),location,getSymHost(op,sym)))
    }
    else if(isPrimitiveType(op.outputType(sym)) && op.isInstanceOf[OP_Nested]) {
      out.append("%s %s = (%s)%s;\n".format(hostType,getSymHost(op,sym),hostType,getSymDevice(op,sym)))
      out.append("%s %s = (%s)%s;\n".format(getJNIType(op.outputType(sym)),getSymCPU(sym),getJNIType(op.outputType(sym)),getSymHost(op,sym)))
    }
    else if(isPrimitiveType(op.outputType(sym))) {
      out.append("%s %s = recvCuda_%s(%s);\n".format(hostType,getSymHost(op,sym),mangledName(devType),getSymDevice(op,sym)))
      out.append("%s %s = (%s)%s;\n".format(getJNIType(op.outputType(sym)),getSymCPU(sym),getJNIType(op.outputType(sym)),getSymHost(op,sym)))      
    }
    else if(devType.startsWith("DeliteArray<")) {
      devType match { //TODO: Fix this for nested object types
        case "DeliteArray< bool >" | "DeliteArray< char >" | "DeliteArray< CHAR >" | "DeliteArray< short >" | "DeliteArray< int >" | "DeiteArray< long >" | "DeliteArray< float >" | "DeliteArray< double >" => 
          out.append("Host%s *%s = recvCuda_%s(%s);\n".format(devType,getSymHost(op,sym),mangledName(devType),getSymDevice(op,sym)))
        case _ => //DeliteArrayObject Type
          out.append("HostDeliteArray< Host%s  *%s = recvCuda_%s(%s);\n".format(devType.drop(13),getSymHost(op,sym),mangledName(devType),getSymDevice(op,sym)))
      }
      out.append("%s %s = sendCPPtoJVM_%s(env%s,%s);\n".format(getJNIType(op.outputType(sym)),getSymCPU(sym),mangledName(devType),location,getSymHost(op,sym)))
    }
    else {
      out.append("%s *%s = recvCuda_%s(%s);\n".format(hostType,getSymHost(op,sym),mangledName(devType),getSymDevice(op,sym)))
      out.append("%s %s = sendCPPtoJVM_%s(env%s,%s);\n".format(getJNIType(op.outputType(sym)),getSymCPU(sym),mangledName(hostType),location,getSymHost(op,sym)))
    }

    out.append("env")
    out.append(location)
    out.append("->CallStaticVoidMethod(cls")
    out.append(location)
    out.append(",env")
    out.append(location)
    out.append("->GetStaticMethodID(cls")
    out.append(location)
    out.append(",\"set_")
    out.append(getSym(op,sym))
    out.append("\",\"(")
    out.append(getJNIArgType(op.outputType(sym)))
    out.append(")V\"),")
    out.append(getSymCPU(sym))
    out.append(");\n")
    out.append("}\n")
  }

  private def writeNotifier(op: DeliteOP, sym: String = "") {
    out.append("env")
    out.append(location)
    out.append("->CallStaticVoidMethod(cls")
    out.append(location)
    out.append(",env")
    out.append(location)
    out.append("->GetStaticMethodID(cls")
    out.append(location)
    out.append(",\"set_")
    if(sym == "") out.append(getOpSym(op))
    else out.append(getOpSym(op)+getSym(op,sym))
    out.append("\",\"(")
    out.append(getJNIArgType("Unit"))
    out.append(")V\"),")
    out.append("boxedUnit")
    out.append(");\n")
  }

  private def writeSendUpdater(op: DeliteOP, sym: String) {
    val devType = op.inputType(Targets.Cuda, sym)
    val hostType = op.inputType(Targets.Cpp, sym)
    assert(!isPrimitiveType(op.inputType(sym)))
    out.append("recvUpdateCuda_%s(%s, %s);\n".format(mangledName(devType),getSymDevice(op,sym),getSymHost(op,sym)))
    out.append("sendUpdateCPPtoJVM_%s(env%s,%s,%s);\n".format(mangledName(hostType),location,getSymCPU(sym),getSymHost(op,sym)))
  }


  override protected[codegen] def writeSyncObject() {
    syncObjectGenerator(syncList, Targets.Scala).makeSyncObjects
    super.writeSyncObject()
  }
}

trait CudaToCppSync extends SyncGenerator with CudaExecutableGenerator with JNIFuncs {

  private val syncList = new ArrayBuffer[Send]

  override protected def receiveData(s: ReceiveData) {
    (scheduledTarget(s.sender.from), scheduledTarget(s.to)) match {
      case (Targets.Cpp, Targets.Cuda) => writeGetter(s.sender.from, s.sender.sym, s.to, false)
      case (Targets.Cuda, Targets.Cpp) => //
      case _ => super.receiveData(s)
    }
  }

  override protected def receiveView(s: ReceiveView) {
    (scheduledTarget(s.sender.from), scheduledTarget(s.to)) match {
      case (Targets.Cpp, Targets.Cuda) => //
      case (Targets.Cuda, Targets.Cpp) => //
      case _ => super.receiveView(s)
    }
  }

  override protected def awaitSignal(s: Await) {
    (scheduledTarget(s.sender.from), scheduledTarget(s.to)) match {
      case (Targets.Cpp, Targets.Cuda) => //
      case (Targets.Cuda, Targets.Cpp) => //
      case _ => super.awaitSignal(s)
    }
  }

  override protected def receiveUpdate(s: ReceiveUpdate) {
    (scheduledTarget(s.sender.from), scheduledTarget(s.to)) match {
      case (Targets.Cpp, Targets.Cuda) => //
        s.sender.from.mutableInputsCondition.get(s.sender.sym) match {
          case Some(lst) =>
            out.append("if(")
            out.append(lst.map(c => c._1.id.split('_').head + "_cond=="+c._2).mkString("&&"))
            out.append(") {\n")
            writeRecvUpdater(s.sender.from, s.sender.sym);
            out.append("}\n")
          case _ =>
            writeRecvUpdater(s.sender.from, s.sender.sym);
        }
      case (Targets.Cuda, Targets.Cpp) => //
      case _ => super.receiveUpdate(s)
    }
  }

  override protected def sendData(s: SendData) {
    if ((scheduledTarget(s.from) == Targets.Cuda) && (s.receivers.map(_.to).filter(r => getHostTarget(scheduledTarget(r)) == Targets.Cpp).nonEmpty)) {
      writeSetter(s.from, s.sym, false)
    }
    super.sendData(s)
  }

  override protected def sendView(s: SendView) {
    super.sendView(s)
  }

  override protected def sendSignal(s: Notify) {
    super.sendSignal(s)
  }

  override protected def sendUpdate(s: SendUpdate) {
    if ((scheduledTarget(s.from) == Targets.Cuda) && (s.receivers.map(_.to).filter(r => getHostTarget(scheduledTarget(r)) == Targets.Cpp).nonEmpty)) {
      s.from.mutableInputsCondition.get(s.sym) match {
        case Some(lst) =>
          out.append("if(")
          out.append(lst.map(c => c._1.id.split('_').head + "_cond=="+c._2).mkString("&&"))
          out.append(") {\n")
          writeSendUpdater(s.from, s.sym)
          out.append("}\n")
        case _ =>
          writeSendUpdater(s.from, s.sym)
      }
    }
    super.sendUpdate(s)
  }

  private def writeGetter(dep: DeliteOP, sym: String, to: DeliteOP, view: Boolean) {
    assert(view == false)
    val ref = if (isPrimitiveType(dep.outputType(sym))) "" else "*"
    val devType = dep.outputType(Targets.Cuda, sym)
    val hostType = dep.outputType(Targets.Cpp, sym)
    if(isPrimitiveType(dep.outputType(sym))) {
      out.append("%s %s = (%s)%s;\n".format(devType,getSymDevice(dep,sym),devType,getSymHost(dep,sym)))
    }
    else {
      out.append("%s %s%s = sendCuda_%s(%s);\n".format(devType,ref,getSymDevice(dep,sym),mangledName(devType),getSymHost(dep,sym)))
      out.append("cudaMemoryMap->insert(std::pair<void*,std::list<void*>*>(")
      out.append(getSymDevice(dep,sym))
      out.append(",lastAlloc));\n")
      out.append("lastAlloc = new std::list<void*>();\n")
    }
  }

  private def writeRecvUpdater(dep: DeliteOP, sym:String) {
    val hostType = dep.inputType(Targets.Cpp, sym)
    val devType = dep.inputType(Targets.Cuda, sym)
    assert(!isPrimitiveType(dep.inputType(sym)))
    out.append("sendUpdateCuda_%s(%s, %s);\n".format(mangledName(devType),getSymDevice(dep,sym),getSymHost(dep,sym)))
  }

  private def writeSetter(op: DeliteOP, sym: String, view: Boolean) {
    assert(view == false)
    val hostType = op.outputType(Targets.Cpp, sym)
    val devType = op.outputType(Targets.Cuda, sym)
    if(isPrimitiveType(op.outputType(sym)) && op.isInstanceOf[OP_Nested]) {
      out.append("%s %s = (%s)%s;\n".format(hostType,getSymHost(op,sym),hostType,getSymDevice(op,sym)))
    }
    else if(isPrimitiveType(op.outputType(sym))) {
      out.append("%s %s = recvCuda_%s(%s);\n".format(hostType,getSymHost(op,sym),mangledName(devType),getSymDevice(op,sym)))
    }
    else {
      out.append("%s *%s = recvCuda_%s(%s);\n".format(hostType,getSymHost(op,sym),mangledName(devType),getSymDevice(op,sym)))
    }
  }

  private def writeSendUpdater(op: DeliteOP, sym: String) {
    val devType = op.inputType(Targets.Cuda, sym)
    val hostType = op.inputType(Targets.Cpp, sym)
    assert(!isPrimitiveType(op.inputType(sym)))
    out.append("recvUpdateCuda_%s(%s, %s);\n".format(mangledName(devType),getSymDevice(op,sym),getSymHost(op,sym)))
  }

  override protected[codegen] def writeSyncObject() {
    super.writeSyncObject()
  }
}

trait CudaSyncGenerator extends CudaToCppSync with CudaToScalaSync with HostToScalaSync {

  override protected def makeNestedFunction(op: DeliteOP) = op match {
    //case r: Receive if (getHostTarget(scheduledTarget(r.sender.from)) == Targets.Scala) => addSync(r)
    //case s: Send if (s.receivers.map(_.to).filter(r => getHostTarget(scheduledTarget(r)) == Targets.Scala).nonEmpty) => addSync(s)
    case s: Sync => addSync(s) //TODO: if sync companion also Scala
    case m: Free if m.op.scheduledOn(Targets.Cuda) => addFree(m)
    case m: Free => //TODO: enable memory management in general
    case _ => super.makeNestedFunction(op)
  }

  //TODO: Factor out to memory management features
  private def addFree(m: Free) {
    val op = m.op
    val freeItem = "freeItem_" + op.id

    def writeCudaFreeInit() {
      out.append("FreeItem ")
      out.append(freeItem)
      out.append(";\n")
      out.append(freeItem)
      out.append(".keys = new std::list< std::pair<void*,bool> >();\n")
    }

    def writeCudaFree(sym: String, isPrim: Boolean) {
      out.append("std::pair<void*,bool> ")
      out.append(getSymDevice(op,sym))
      out.append("_pair(")
      out.append(getSymDevice(op,sym))
      out.append(",")
      out.append(isPrim) //Do not free this ptr using free() : primitive type pointers points to device memory
      out.append(");\n")
      out.append(freeItem)
      out.append(".keys->push_back(")
      out.append(getSymDevice(op,sym))
      out.append("_pair);\n")
    }

    def writeJVMRelease(sym: String) {
      out.append("env")
      out.append(location)
      out.append("->DeleteLocalRef(")
      out.append(getSymCPU(sym))
      out.append(");\n")
    }

    def writeHostRelease(fop: DeliteOP, sym: String) {
      //out.append(getSymHost(fop,sym))
      //out.append("->release();\n")
    }

    //TODO: Separate JVM/Host/Device frees
    writeCudaFreeInit()
    for (f <- m.items if f._1.scheduledOn(Targets.Cuda)) {
      writeCudaFree(f._2, isPrimitiveType(f._1.outputType(f._2)))
      //TODO: enable memory management for host data structures
      /*
      if ( (f._1.scheduledResource != location) || (f._1.getConsumers.filter(c => c.scheduledResource!=location && c.getInputs.map(_._2).contains(f._2)).nonEmpty) ) {
        if (!isPrimitiveType(f._1.outputType(f._2)) && f._1.outputType(f._2)!="Unit") {
          writeJVMRelease(f._2)
          writeHostRelease(f._1,f._2)
        }
      }
      */
    }

    //sync on kernel stream (if copied back guaranteed to have completed, so don't need sync on d2h stream)
    out.append(freeItem)
    out.append(".event = addHostEvent(kernelStream);\n")
    out.append("freeList->push(")
    out.append(freeItem)
    out.append(");\n")
  }
}
