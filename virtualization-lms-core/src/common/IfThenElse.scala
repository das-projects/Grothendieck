package scala.virtualization.lms
package common

import java.io.PrintWriter
import scala.reflect.SourceContext
import scala.virtualization.lms.internal.{FatBlockTraversal, GenericNestedCodegen, GenericFatCodegen, GenerationFailedException}

trait IfThenElse extends Base {
  def __ifThenElse[T:Manifest](cond: Rep[Boolean], thenp: => Rep[T], elsep: => Rep[T])(implicit pos: SourceContext): Rep[T]

  // HACK -- bug in scala-virtualized
  override def __ifThenElse[T](cond: =>Boolean, thenp: => T, elsep: => T) = cond match {
    case true => thenp
    case false => elsep
  }
}

// TODO: it would be nice if IfThenElseExp would extend IfThenElsePureExp
// but then we would need to use different names.

trait IfThenElsePureExp extends IfThenElse with BaseExp {

  case class IfThenElse[T:Manifest](cond: Exp[Boolean], thenp: Exp[T], elsep: Exp[T]) extends Def[T]

  def __ifThenElse[T:Manifest](cond: Rep[Boolean], thenp: => Rep[T], elsep: => Rep[T])(implicit pos: SourceContext) = IfThenElse(cond, thenp, elsep)
}


trait IfThenElseExp extends IfThenElse with EffectExp {

  abstract class AbstractIfThenElse[T] extends Def[T] with CanBeFused {
    val cond: Exp[Boolean]
    val thenp: Block[T]
    val elsep: Block[T]
  }
  
  case class IfThenElse[T:Manifest](cond: Exp[Boolean], thenp: Block[T], elsep: Block[T]) extends AbstractIfThenElse[T]

  override def __ifThenElse[T:Manifest](cond: Rep[Boolean], thenp: => Rep[T], elsep: => Rep[T])(implicit pos: SourceContext) = {
    val a = reifyEffectsHere(thenp)
    val b = reifyEffectsHere(elsep)

    ifThenElse(cond,a,b)
  }

  def ifThenElse[T:Manifest](cond: Rep[Boolean], thenp: Block[T], elsep: Block[T])(implicit pos: SourceContext) = {
    val ae = summarizeEffects(thenp)
    val be = summarizeEffects(elsep)
    
    // TODO: make a decision whether we should call reflect or reflectInternal.
    // the former will look for any read mutable effects in addition to the passed
    // summary whereas reflectInternal will take ae orElse be literally.
    // the case where this comes up is if (c) a else b, with a or b mutable.
    // (see TestMutation, for now sticking to old behavior)
    
    ////reflectEffect(IfThenElse(cond,thenp,elsep), ae orElse be)
    reflectEffectInternal(IfThenElse(cond,thenp,elsep), ae orElse be)
  }
  def __ifThenElseCopy[T:Manifest](cond: Rep[Boolean], thenp: => Rep[T], elsep: => Rep[T], oldDef: Def[Any])(implicit pos: SourceContext): Exp[T] = {
    val a = reifyEffectsHere(thenp)
    val b = reifyEffectsHere(elsep)

    val ae = summarizeEffects(a)
    val be = summarizeEffects(b)
    
    // TODO: make a decision whether we should call reflect or reflectInternal.
    // the former will look for any read mutable effects in addition to the passed
    // summary whereas reflectInternal will take ae orElse be literally.
    // the case where this comes up is if (c) a else b, with a or b mutable.
    // (see TestMutation, for now sticking to old behavior)
    
    ////reflectEffect(IfThenElse(cond,thenp,elsep), ae orElse be)
    reflectEffectInternal(IfThenElse(cond,a,b).copyCanBeFused(oldDef), ae orElse be)
  }
  
  override def mirrorDef[A:Manifest](e: Def[A], f: Transformer)(implicit pos: SourceContext): Def[A] = e match {
    case IfThenElse(c,a,b) => IfThenElse(f(c),f(a),f(b)).copyCanBeFused(e)
    case _ => super.mirrorDef(e,f)
  }
  
  override def mirror[A:Manifest](e: Def[A], f: Transformer)(implicit pos: SourceContext): Exp[A] = e match {
    case Reflect(IfThenElse(c,a,b), u, es) => 
      if (f.hasContext)
        __ifThenElseCopy(f(c),f.reflectBlock(a),f.reflectBlock(b), e)
      else
        reflectMirrored(Reflect(IfThenElse(f(c),f(a),f(b)).copyCanBeFused(e), mapOver(f,u), f(es)))(mtype(manifest[A]), pos)
    case IfThenElse(c,a,b) => 
      if (f.hasContext)
        __ifThenElseCopy(f(c),f.reflectBlock(a),f.reflectBlock(b), e)
      else
        IfThenElse(f(c),f(a),f(b)).copyCanBeFused(e) // FIXME: should apply pattern rewrites (ie call smart constructor)
    case _ => super.mirror(e,f)
  }

/*
  override def mirror[A:Manifest](e: Def[A], f: Transformer): Exp[A] = e match {
    case Reflect(IfThenElse(c,a,b), u, es) => mirror(IfThenElse(c,a,b)) // discard reflect
    case IfThenElse(c,a,b) => ifThenElse(f(c),f(a),f(b)) // f.apply[A](a: Block[A]): Exp[A] mirrors the block into the current context
    case _ => super.mirror(e,f)
  }  
*/



  override def aliasSyms(e: Any): List[Sym[Any]] = e match {
    case IfThenElse(c,a,b) => syms(a):::syms(b)
    case _ => super.aliasSyms(e)
  }

  override def containSyms(e: Any): List[Sym[Any]] = e match {
    case IfThenElse(c,a,b) => Nil
    case _ => super.containSyms(e)
  }

  override def extractSyms(e: Any): List[Sym[Any]] = e match {
    case IfThenElse(c,a,b) => Nil
    case _ => super.extractSyms(e)
  }

  override def copySyms(e: Any): List[Sym[Any]] = e match {
    case IfThenElse(c,a,b) => Nil // could return a,b but implied by aliasSyms
    case _ => super.copySyms(e)
  }


  override def symsFreq(e: Any): List[(Sym[Any], Double)] = e match {
    case IfThenElse(c, t, e) => freqNormal(c) ++ freqCold(t) ++ freqCold(e)
    case _ => super.symsFreq(e)
  }

/*
  override def coldSyms(e: Any): List[Sym[Any]] = e match {
    case IfThenElse(c, t, e) => syms(t) ++ syms(e)
    case _ => super.coldSyms(e)
  }
*/

  override def boundSyms(e: Any): List[Sym[Any]] = e match {
    case IfThenElse(c, t, e) => effectSyms(t):::effectSyms(e)
    case _ => super.boundSyms(e)
  }

}

trait IfThenElseFatExp extends IfThenElseExp with BaseFatExp {

  abstract class AbstractFatIfThenElse extends FatDef with CanBeFused {
    val cond: Exp[Boolean]
    val thenp: List[Block[Any]]
    val elsep: List[Block[Any]]
    
    var extradeps: List[Exp[Any]] = Nil //HACK
  }

  case class SimpleFatIfThenElse(cond: Exp[Boolean], thenp: List[Block[Any]], elsep: List[Block[Any]]) extends AbstractFatIfThenElse

/* HACK */

  override def syms(e: Any): List[Sym[Any]] = e match {
    case x@SimpleFatIfThenElse(c, t, e) => super.syms(x) ++ syms(x.extradeps)
    case _ => super.syms(e)
  }

/* END HACK */


  override def boundSyms(e: Any): List[Sym[Any]] = e match {
    case SimpleFatIfThenElse(c, t, e) => effectSyms(t):::effectSyms(e)
    case _ => super.boundSyms(e)
  }

  override def symsFreq(e: Any): List[(Sym[Any], Double)] = e match {
    case x@SimpleFatIfThenElse(c, t, e) => freqNormal(c) ++ freqCold(t) ++ freqCold(e)    ++ freqNormal(x.extradeps)
    case _ => super.symsFreq(e)
  }

  // aliasing / sharing
  
  override def aliasSyms(e: Any): List[Sym[Any]] = e match {
    case SimpleFatIfThenElse(c,a,b) => syms(a):::syms(b)
    case _ => super.aliasSyms(e)
  }

  override def containSyms(e: Any): List[Sym[Any]] = e match {
    case SimpleFatIfThenElse(c,a,b) => Nil
    case _ => super.containSyms(e)
  }

  override def extractSyms(e: Any): List[Sym[Any]] = e match {
    case SimpleFatIfThenElse(c,a,b) => Nil
    case _ => super.extractSyms(e)
  }

  override def copySyms(e: Any): List[Sym[Any]] = e match {
    case SimpleFatIfThenElse(c,a,b) => Nil // could return a,b but implied by aliasSyms
    case _ => super.copySyms(e)
  }
}


trait IfThenElseExpOpt extends IfThenElseExp { this: BooleanOpsExp with EqualExpBridge =>
  
  //TODO: eliminate conditional if both branches return same value!

  // it would be nice to handle rewrites in method ifThenElse but we'll need to
  // 'de-reify' blocks in case we rewrite if(true) to thenp. 
  // TODO: make reflect(Reify(..)) do the right thing
  
  override def __ifThenElse[T:Manifest](cond: Rep[Boolean], thenp: => Rep[T], elsep: => Rep[T])(implicit pos: SourceContext) = cond match {
    case Const(true) => thenp
    case Const(false) => elsep
    case Def(BooleanNegate(a)) => __ifThenElse(a, elsep, thenp)
    case Def(NotEqual(a,b)) => __ifThenElse(equals(a,b), elsep, thenp)
    case _ =>
      super.__ifThenElse(cond, thenp, elsep)
  }

  override def __ifThenElseCopy[T:Manifest](cond: Rep[Boolean], thenp: => Rep[T], elsep: => Rep[T], oldDef: Def[Any])(implicit pos: SourceContext) = cond match {
    case Const(true) => thenp
    case Const(false) => elsep
    case Def(BooleanNegate(a)) => __ifThenElseCopy(a, elsep, thenp, oldDef)
    case Def(NotEqual(a,b)) => __ifThenElseCopy(equals(a,b), elsep, thenp, oldDef)
    case _ =>
      super.__ifThenElseCopy(cond, thenp, elsep, oldDef)
  }
}

trait BaseGenIfThenElse extends GenericNestedCodegen {
  val IR: IfThenElseExp
  import IR._

}

trait BaseIfThenElseTraversalFat extends FatBlockTraversal {
  val IR: IfThenElseFatExp
  import IR._

  override def fatten(e: Stm): Stm = e match {
    case TP(sym, o: AbstractIfThenElse[_]) => 
      TTP(List(sym), List(o), SimpleFatIfThenElse(o.cond, List(o.thenp), List(o.elsep)).copyCanBeFused(o))
    case TP(sym, p @ Reflect(o: AbstractIfThenElse[_], u, es)) => //if !u.maySimple && !u.mayGlobal =>  // contrary, fusing will not change observable order
      // assume body will reflect, too...
      if (!shouldFattenEffectfulLoops()) // Old loop fusion legacy mode
        printdbg("-- fatten effectful if/then/else " + e)
      val e2 = SimpleFatIfThenElse(o.cond, List(o.thenp), List(o.elsep)).copyCanBeFused(o) 
      e2.extradeps = es //HACK
      TTP(List(sym), List(p), e2)
    case _ => super.fatten(e)
  }

  override def combineFat(list: List[TTP]): TTP = list(0) match {
    case TTP(_, _, firstIte: AbstractFatIfThenElse) => 
      val cond = firstIte.cond
      val (lmhs, rhs) = list.map({ 
        case TTP(lhs, mhs, ite: AbstractFatIfThenElse) => 
          if (cond != ite.cond) {
            printlog("FAILED: combineFat of two ifs with different conditions: " + firstIte + " and " + ite)
            return super.combineFat(list)
          } else if (!firstIte.isFusedWith(ite)) {
            printlog("FAILED: combineFat of two ifs that haven't been fused (call CanBeFused.registerFusion first): " + firstIte + " and " + ite)
            return super.combineFat(list)
          }
          ((lhs, mhs), (ite.thenp, ite.elsep))
        case s => 
          printlog("FAILED: combineFat of an if with something else: " + firstIte + " and " + s)
          return super.combineFat(list)
      }).unzip
      val (lhs, mhs) = lmhs.unzip
      val (thenps, elseps) = rhs.unzip
      // The mhs lists the original statements including their effects
      TTP(lhs.flatten, mhs.flatten, SimpleFatIfThenElse(cond, thenps.flatten, elseps.flatten)) 
    case _ =>
      super.combineFat(list)
  }
}

trait BaseGenIfThenElseFat extends BaseGenIfThenElse with GenericFatCodegen with BaseIfThenElseTraversalFat

trait ScalaGenIfThenElse extends ScalaGenEffect with BaseGenIfThenElse {
  import IR._
 
  override def emitNode(sym: Sym[Any], rhs: Def[Any]) = rhs match {
    case IfThenElse(c,a,b) =>
      stream.println("val " + quote(sym) + " = if (" + quote(c) + ") {")
      emitBlock(a)
      stream.println(quote(getBlockResult(a)))
      stream.println("} else {")
      emitBlock(b)
      stream.println(quote(getBlockResult(b)))
      stream.println("}")
    case _ => super.emitNode(sym, rhs)
  }
}

trait ScalaGenIfThenElseFat extends ScalaGenIfThenElse with ScalaGenFat with BaseGenIfThenElseFat {
  import IR._

  override def emitFatNode(symList: List[Sym[Any]], rhs: FatDef) = rhs match {
    case SimpleFatIfThenElse(c,as,bs) => 
      def quoteList[T](xs: List[Exp[T]]) = if (xs.length > 1) xs.map(quote).mkString("(",",",")") else xs.map(quote).mkString(",")
      if (symList.length > 1) stream.println("// TODO: use vars instead of tuples to return multiple values")
      stream.println("val " + quoteList(symList) + " = if (" + quote(c) + ") {")
      emitFatBlock(as)
      stream.println(quoteList(as.map(getBlockResult)))
      stream.println("} else {")
      emitFatBlock(bs)
      stream.println(quoteList(bs.map(getBlockResult)))
      stream.println("}")
    case _ => super.emitFatNode(symList, rhs)
  }

}

trait CudaGenIfThenElse extends CudaGenEffect with BaseGenIfThenElse {
  import IR._

  override def emitNode(sym: Sym[Any], rhs: Def[Any]) = {
      rhs match {
        case IfThenElse(c,a,b) =>
          // TODO: Not GPUable if the result is not primitive types.
          // TODO: Changing the reference of the output is dangerous in general.
          // TODO: In the future, consider passing the object references to the GPU kernels rather than copying by value.
          // Below is a safety check related to changing the output reference of the kernel.
          // This is going to be changed when above TODOs are done.
          //if( (sym==kernelSymbol) && (isObjectType(sym.tp)) ) throw new RuntimeException("CudaGen: Changing the reference of output is not allowed within GPU kernel.")

          val objRetType = (!isVoidType(sym.tp)) && (!isPrimitiveType(sym.tp))
          objRetType match {
            case true => throw new GenerationFailedException("CudaGen: If-Else cannot return object type.")
            case _ =>
          }
          isVoidType(sym.tp) match {
            case true =>
              stream.println(addTab() + "if (" + quote(c) + ") {")
              tabWidth += 1
              emitBlock(a)
              tabWidth -= 1
              stream.println(addTab() + "} else {")
              tabWidth += 1
              emitBlock(b)
              tabWidth -= 1
              stream.println(addTab()+"}")
            case false =>
              stream.println("%s %s;".format(remap(sym.tp),quote(sym)))
              stream.println(addTab() + "if (" + quote(c) + ") {")
              tabWidth += 1
              emitBlock(a)
              stream.println(addTab() + "%s = %s;".format(quote(sym),quote(getBlockResult(a))))
              tabWidth -= 1
              stream.println(addTab() + "} else {")
              tabWidth += 1
              emitBlock(b)
              stream.println(addTab() + "%s = %s;".format(quote(sym),quote(getBlockResult(b))))
              tabWidth -= 1
              stream.println(addTab()+"}")
          }

        case _ => super.emitNode(sym, rhs)
      }
    }
}

trait CudaGenIfThenElseFat extends CudaGenIfThenElse with CudaGenFat with BaseGenIfThenElseFat {
  import IR._

  override def emitFatNode(symList: List[Sym[Any]], rhs: FatDef) = rhs match {
    case SimpleFatIfThenElse(c,a,b) => sys.error("TODO: implement fat if CUDA codegen")
    case _ => super.emitFatNode(symList, rhs)
  }
}

trait OpenCLGenIfThenElse extends OpenCLGenEffect with BaseGenIfThenElse {
  import IR._

  override def emitNode(sym: Sym[Any], rhs: Def[Any]) = {
      rhs match {
        case IfThenElse(c,a,b) =>

          val objRetType = (!isVoidType(sym.tp)) && (!isPrimitiveType(sym.tp))
          objRetType match {
            case true => throw new GenerationFailedException("OpenCLGen: If-Else cannot return object type.")
            case _ =>
          }
          isVoidType(sym.tp) match {
            case true =>
              stream.println(addTab() + "if (" + quote(c) + ") {")
              tabWidth += 1
              emitBlock(a)
              tabWidth -= 1
              stream.println(addTab() + "} else {")
              tabWidth += 1
              emitBlock(b)
              tabWidth -= 1
              stream.println(addTab()+"}")
            case false =>
              stream.println("%s %s;".format(remap(sym.tp),quote(sym)))
              stream.println(addTab() + "if (" + quote(c) + ") {")
              tabWidth += 1
              emitBlock(a)
              stream.println(addTab() + "%s = %s;".format(quote(sym),quote(getBlockResult(a))))
              tabWidth -= 1
              stream.println(addTab() + "} else {")
              tabWidth += 1
              emitBlock(b)
              stream.println(addTab() + "%s = %s;".format(quote(sym),quote(getBlockResult(b))))
              tabWidth -= 1
              stream.println(addTab()+"}")
          }

        case _ => super.emitNode(sym, rhs)
      }
    }
}

trait OpenCLGenIfThenElseFat extends OpenCLGenIfThenElse with OpenCLGenFat with BaseGenIfThenElseFat {
  import IR._

  override def emitFatNode(symList: List[Sym[Any]], rhs: FatDef) = rhs match {
    case SimpleFatIfThenElse(c,a,b) => sys.error("TODO: implement fat if OpenCL codegen")
    case _ => super.emitFatNode(symList, rhs)
  }
}

trait CGenIfThenElse extends CGenEffect with BaseGenIfThenElse {
  import IR._

  override def emitNode(sym: Sym[Any], rhs: Def[Any]) = {
    rhs match {
      case IfThenElse(c,a,b) =>
        //TODO: using if-else does not work 
        remap(sym.tp) match {
          case "void" =>
            stream.println("if (" + quote(c) + ") {")
            emitBlock(a)
            stream.println("} else {")
            emitBlock(b)
            stream.println("}")
          case _ =>
            stream.println("%s %s;".format(remap(sym.tp),quote(sym)))
            stream.println("if (" + quote(c) + ") {")
            emitBlock(a)
            stream.println("%s = %s;".format(quote(sym),quote(getBlockResult(a))))
            stream.println("} else {")
            emitBlock(b)
            stream.println("%s = %s;".format(quote(sym),quote(getBlockResult(b))))
            stream.println("}")
        }
        /*
        val booll = remap(sym.tp).equals("void")
        if(booll) {
          stream.println("%s %s;".format(remap(sym.tp),quote(sym)))
          stream.println("if (" + quote(c) + ") {")
          emitBlock(a)
          stream.println("%s = %s;".format(quote(sym),quote(getBlockResult(a))))
          stream.println("} else {")
          emitBlock(b)
          stream.println("%s = %s;".format(quote(sym),quote(getBlockResult(b))))
          stream.println("}")
        }
        else {
          stream.println("if (" + quote(c) + ") {")
          emitBlock(a)
          stream.println("} else {")
          emitBlock(b)
          stream.println("}")
        }
        */
      case _ => super.emitNode(sym, rhs)
    }
  }
}

trait CGenIfThenElseFat extends CGenIfThenElse with CGenFat with BaseGenIfThenElseFat {
  import IR._

  override def emitFatNode(symList: List[Sym[Any]], rhs: FatDef) = rhs match {
    case SimpleFatIfThenElse(c,a,b) => sys.error("TODO: implement fat if C codegen")
    case _ => super.emitFatNode(symList, rhs)
  }
}

