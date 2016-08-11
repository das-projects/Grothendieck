package ppl.delite.framework.ops

import scala.virtualization.lms.internal.GenerationFailedException
import scala.virtualization.lms.common._
import scala.reflect.{SourceContext, RefinedManifest}
import ppl.delite.framework.datastructures._
import ppl.delite.framework.Config

trait DeliteFileOutputStream

trait DeliteFileWriterOps extends Base with DeliteArrayBufferOps {

  object DeliteFileWriter {
    def writeLines(path: Rep[String], numLines: Rep[Int], append: Rep[Boolean] = unit(false))(f: Rep[Int] => Rep[String])(implicit pos: SourceContext) = dfw_writeLines(path, numLines, append, f)
  }

  def dfw_writeLines(path: Rep[String], numLines: Rep[Int], append: Rep[Boolean], f: Rep[Int] => Rep[String])(implicit pos: SourceContext): Rep[Unit]

  def dfos_new(path: Rep[String], sequential: Rep[Boolean] = unit(true), append: Rep[Boolean] = unit(false))(implicit pos: SourceContext): Rep[DeliteFileOutputStream]
  def dfos_writeLine(stream: Rep[DeliteFileOutputStream], line: Rep[String])(implicit pos: SourceContext): Rep[Unit]
  def dfos_close(stream: Rep[DeliteFileOutputStream])(implicit pos: SourceContext): Rep[Unit]
}

trait DeliteFileWriterOpsExp extends DeliteFileWriterOps with RuntimeServiceOpsExp with DeliteArrayOpsExpOpt with DeliteArrayBufferOpsExp with DeliteOpsExp with DeliteMapOpsExp {

  case class DeliteFileOutputStreamNew(path: Exp[String], sequential: Exp[Boolean], append: Exp[Boolean]) extends Def[DeliteFileOutputStream]

  case class DeliteOpFileWriteLines(stream: Exp[DeliteFileOutputStream], numLines: Exp[Int], f: Exp[Int] => Exp[String])(implicit pos: SourceContext) extends DeliteOpIndexedLoop {
    // dynamicChunks should default to 0, but we are explicit here, since static chunking is assumed by the implementation
    override val numDynamicChunks = 0

    val size = copyTransformedOrElse(_.size)(numLines)
    def func = idx => dfos_writeLine(stream,f(idx))
  }

  case class DeliteFileOutputStreamWriteLine(stream: Exp[DeliteFileOutputStream], line: Exp[String]) extends Def[Unit]

  case class DeliteFileOutputStreamClose(stream: Exp[DeliteFileOutputStream]) extends Def[Unit]

  def dfos_new(path: Exp[String], sequential: Exp[Boolean], append: Exp[Boolean])(implicit pos: SourceContext) = reflectMutable(DeliteFileOutputStreamNew(path,sequential,append))

  def dfw_writeLines(path: Exp[String], numLines: Exp[Int], append: Rep[Boolean], f: Exp[Int] => Exp[String])(implicit pos: SourceContext) = {
    // We pass in the runtime resourceInfo during code generation to the output stream,
    // which it uses to allocate 1 file per thread and to detect the threadId when performing a write
    val stream = dfos_new(path, unit(false), append)
    reflectWrite(stream)(DeliteOpFileWriteLines(stream, numLines, f))
    // Close is handled per-thread in DeliteOpsBaseGenericGen, to account for the distributed case where we have multiple wrapper instances
  }

  def dfos_writeLine(stream: Exp[DeliteFileOutputStream], line: Exp[String])(implicit pos: SourceContext): Exp[Unit] = {
    reflectWrite(stream)(DeliteFileOutputStreamWriteLine(stream, line))
  }

  def dfos_close(stream: Exp[DeliteFileOutputStream])(implicit pos: SourceContext) = reflectWrite(stream)(DeliteFileOutputStreamClose(stream))

  override def mirror[A:Manifest](e: Def[A], f: Transformer)(implicit ctx: SourceContext): Exp[A] = (e match {
    case Reflect(e@DeliteOpFileWriteLines(path,numLines,func), u, es) => reflectMirrored(Reflect(new { override val original = Some(f,e) } with DeliteOpFileWriteLines(f(path),f(numLines),f(func))(ctx), mapOver(f,u), f(es)))(mtype(manifest[A]), ctx)
    case Reflect(DeliteFileOutputStreamNew(path,s,app), u, es) => reflectMirrored(Reflect(DeliteFileOutputStreamNew(f(path),f(s),f(app)), mapOver(f,u), f(es)))(mtype(manifest[A]), ctx)
    case Reflect(DeliteFileOutputStreamWriteLine(stream,line), u, es) => reflectMirrored(Reflect(DeliteFileOutputStreamWriteLine(f(stream), f(line)), mapOver(f,u), f(es)))(mtype(manifest[A]), ctx)
    case Reflect(DeliteFileOutputStreamClose(stream), u, es) => reflectMirrored(Reflect(DeliteFileOutputStreamClose(f(stream)), mapOver(f,u), f(es)))(mtype(manifest[A]), ctx)
    case _ => super.mirror(e,f)
  }).asInstanceOf[Exp[A]]

}

trait ScalaGenDeliteFileWriterOps extends ScalaGenFat with GenericGenDeliteOps with ScalaGenRuntimeServiceOps {
  val IR: DeliteFileWriterOpsExp
  import IR._

  override def emitNode(sym: Sym[Any], rhs: Def[Any]) = rhs match {
    case DeliteFileOutputStreamNew(path, s, app) =>
      emitValDef(sym, "generated.scala.io.DeliteFileOutputStream("+quote(path)+","+quote(s)+","+quote(app)+","+resourceInfoSym+")")
    case DeliteFileOutputStreamWriteLine(stream, line) =>
      emitValDef(sym, quote(stream) + ".writeLine("+resourceInfoSym+","+quote(line)+")")
    case DeliteFileOutputStreamClose(stream) =>
      emitValDef(sym, quote(stream) + ".close()")
    case _ => super.emitNode(sym, rhs)
  }

  override def remap[A](m: Manifest[A]): String = m.erasure.getSimpleName match {
    case "DeliteFileOutputStream" => "generated.scala.io.DeliteFileOutputStream"
    case _ => super.remap(m)
  }

}

trait CGenDeliteFileWriterOps extends CGenFat {
  val IR: DeliteFileWriterOpsExp
  import IR._

  override def emitNode(sym: Sym[Any], rhs: Def[Any]) = rhs match {
    case DeliteFileOutputStreamNew(path, s, app) =>
      emitValDef(sym, "new DeliteFileOutputStream("+quote(path)+","+quote(s)+","+quote(app)+","+resourceInfoSym+")")
    case DeliteFileOutputStreamWriteLine(s, line) =>
      stream.println(quote(s) + "->writeLine("+resourceInfoSym+","+quote(line)+");")
    case DeliteFileOutputStreamClose(s) =>
      stream.println(quote(s) + "->close();")
    case _ => super.emitNode(sym, rhs)
  }

  override def remap[A](m: Manifest[A]): String = m.erasure.getSimpleName match {
    case "DeliteFileOutputStream" => "DeliteFileOutputStream"
    case _ => super.remap(m)
  }

  override def getDataStructureHeaders(): String = {
    val out = new StringBuilder
    out.append("#include \"DeliteFileOutputStream.h\"\n")
    super.getDataStructureHeaders() + out.toString
  }
}

