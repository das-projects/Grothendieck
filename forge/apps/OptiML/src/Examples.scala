/**
 * This file contains the examples published on the OptiML github pages
 * http://stanford-ppl.github.com/Delite/optiml/examples.html
 *
 * Each example can be executed using the delitec/delite scripts located in <DELITE_HOME>/bin, e.g.
 *   delitec Example1Interpreter
 *   delite Example1Interpreter
 */

import scala.virtualization.lms.common.Record
import scala.reflect.SourceContext

import optiml.compiler._
import optiml.library._
import optiml.shared._

import ppl.delite.framework.{BeginScopes,EndScopes}
import ppl.delite.framework.ScopeCommunication._

// DenseVector, DenseMatrix
object Example1Compiler extends OptiMLApplicationCompiler with Example1
object Example1Interpreter extends OptiMLApplicationInterpreter with Example1
trait Example1 extends OptiMLApplication {
  def main() = {
    // 10000x1 DenseVector
    val v1 = DenseVector.rand(10000)
    // 1000x1000 DenseMatrix
    val m = DenseMatrix.rand(1000,1000)

    // perform some simple infix operations
    val v2 = (v1+10)*2-5

    // take the pointwise natural log of v2 and sum results
    val logv2Sum = log(v2).sum

    // slice elems 1000-2000 of v2 and multiply by m
    val v3 = v2(1000::2000)*m // 1x1000 DenseVector result

    // print the first 10 elements to the screen
    v3(0::10).pprint
  }
}

// DenseVector, DenseMatrix construction
object Example2Compiler extends OptiMLApplicationCompiler with Example2
object Example2Interpreter extends OptiMLApplicationInterpreter with Example2
trait Example2 extends OptiMLApplication {
  def main() = {
    /* various ways of constructing a DenseVector */
    val v0 = DenseVector[Int](100) // 100x1 all zeros, ints
    val v1 = DenseVector[Int](100,false) // 1x100 all zeros, ints
    val v3 = DenseVector.rand(100) // 100x1 random doubles
    val v4 = DenseVector.zeros(100) // 100x1 all zeros, doubles
    val v5 = DenseVector(1,2,3,4,5) // [1,2,3,4,5]
    val v6 = DenseVector(1.0,2.0,3.0,4.0,5.0) // [1.0,2.0,3.0,4.0,5.0]
    val v7 = (0::100) { e => random[Int] } // 100x1, random ints

    /* various ways of constructing a DenseMatrix */
    val m0 = DenseMatrix[Int](100,50) // 100x50 zeros, ints
    val m1 = DenseMatrix.rand(100,50) // 100x50 random doubles
    val m2 = DenseMatrix.zeros(100,50) // 100x50 zeros, doubles
    val m3 = DenseMatrix(DenseVector(1,2,3),DenseVector(4,5,6)) // [1,2,3]
                                          // [4,5,6]
    val m4 = (0::2, *) { i => DenseVector(2,3,4) }  // [2,3,4]
                                                    // [2,3,4]
    val m5 = (0::2, 0::2) { (i,j) => i*j }  // [0,0]
                                            // [0,1]

    // print first row
    m5(0).pprint
  }
}

// using functional operators (map, zip, filter)
object Example3Compiler extends OptiMLApplicationCompiler with Example3
object Example3Interpreter extends OptiMLApplicationInterpreter with Example3
trait Example3 extends OptiMLApplication {
  def main() = {
    val v = DenseVector.rand(1000)

    // filter selects all the elements matching a predicate
    // map constructs a new vector by applying a function to each element
    val v2 = (v*1000).filter(e => e < 500).map(e=>e*e*random[Double])
    println(v2.length)

    // reduce produces a scalar by successively applying a function to pairs
    val logmin = v2.reduce((a,b) => if (log(a) < log(b)) a else b)
    println(logmin)

    // partition splits the vector into two based on a predicate
    val (v2small, v2large) = unpack(v2.partition(e => e < 1000))
    println("v2small size: " + v2small.length)
    println("v2large size: " + v2large.length)
  }
}

// iterating
object Example4Compiler extends OptiMLApplicationCompiler with Example4
object Example4Interpreter extends OptiMLApplicationInterpreter with Example4
trait Example4 extends OptiMLApplication {
  def main() = {
    // a DenseVector[DenseVector[Double]]
    // 100 vectors, each containing 1000 random doubles
    val v = DenseVector.zeros(100).map(e => DenseVector.rand(1000))

    // iterate using for (parallel)
    for (vec <- v) {
      // prints can happen in any order!
      if (vec(0) > .9) println("found > .9")
    }

    // iterate using while (sequential)
    var i = 0
    while (i < v.length) {
      val vi = v(i)
      // always prints in order
      println("first element of vector " + i + ": " + vi(0))
      i += 1
    }
  }
}

// file i/o

// myvector.dat
// 0.1
// 0.5
// 1.2
// 1.3
// 0.6
//
// myvector2.dat
// 1;blue;-1
// 16;green;3
// 3;red;55
//
// mymatrix.dat
// 3 12 5
// 17 32 1
// -6 1 0
object Example5Compiler extends OptiMLApplicationCompiler with Example5
object Example5Interpreter extends OptiMLApplicationInterpreter with Example5
trait Example5 extends OptiMLApplication {
  def main() = {
    // simple i/o
    val v = readVector("myvector.dat")
    v.pprint

    val m = readMatrix("mymatrix.dat")
    m.pprint

    // i/o with custom parser
    // second argument is a function from a DenseVector[String] to a Double
    // third argument is the delimeter used to split the line
    val v2 = readVectorAndParse[Double]("myvector2.dat", line => line(0).toDouble, ";")
    v2.pprint
  }
}

// untilconverged
object Example6Compiler extends OptiMLApplicationCompiler with Example6
object Example6Interpreter extends OptiMLApplicationInterpreter with Example6
trait Example6 extends OptiMLApplication {
  def main() = {
    // newton descent

    // arbitrary initial values
    val c0 = 0.0
    val c1 = 1.2
    val c2 = 9.7
    val linit = 5.5
    val lambda =
      untilconverged(linit, .001) { (lambda, iter) =>
        val l2 = lambda*lambda
        val b = (l2 + c2)*lambda
        val a = b + c1
        lambda - (a * lambda + c0) / (2.0*lambda*l2 + b + a)
      }
    println("lambda: " + lambda)
  }
}

// sum
object Example7Compiler extends OptiMLApplicationCompiler with Example7
object Example7Interpreter extends OptiMLApplicationInterpreter with Example7
trait Example7 extends OptiMLApplication {
  def main() = {
    val simpleSeries = sum(0, 100) { i => i } // sum(0,1,2,3,...99)
    println("simpleSeries: " + simpleSeries)

    val m = DenseMatrix.rand(10,100)
    // sum first 10 rows of m
    val rowSum = sumRows(0,10) { i => m(i) }
    println("rowSum:")
    rowSum.pprint

    // sum(0,2,4,8...98)
    val conditionalSeries = sumIf(0,100)(i => i % 2 == 0) { i => i }
    println("conditionalSeries: " + conditionalSeries)

    // conditional sum over rows of a matrix
    val conditionalRowSum = sumRowsIf(0,10)(i => m(i).min > .01) { i => m(i) }
    println("conditionalRowSum:")
    conditionalRowSum.pprint
  }
}

// SparseVector, SparseMatrix
object Example8Compiler extends OptiMLApplicationCompiler with Example8
object Example8Interpreter extends OptiMLApplicationInterpreter with Example8
trait Example8 extends OptiMLApplication {
  def main() = {
    val v = SparseVector[Double](100,true)
    v(5) = 10
    v(75) = 20

    // mapping a sparse vector's non-zero elements returns another sparse vector
    val t1 = v mapnz { e => e/(exp(e)+1) }

    // nnz (number of non-zeros is a field only available on sparse structures)
    println("t1 nnz: " + t1.nnz)
    println("t1.length: " + t1.length)
    t1.pprint

    // constructing a new sparse matrix
    // sparse matrices are split into separates phases for construction and use
    // they can only be mutated before 'finish' is called, and can only be
    // used in other operations (e.g. math) after 'finish' is called
    val mBuilder = SparseMatrix[Double](1000,1000)
    mBuilder(10,100) = 5
    mBuilder(9,100) = 1
    mBuilder(9,722) = 722
    val m = mBuilder.finish
    println("m numRows: " + m.numRows)
    println("m numCols: " + m.numCols)
    println("m nnz: " + m.nnz)

    val m2 = m+m
    println("m2 nnz: " + m2.nnz)
  }
}

// Graph
// NOT YET IMPLEMENTED
/*
object Example9Compiler extends OptiMLApplicationCompiler with Example9
object Example9Interpreter extends OptiMLApplicationInterpreter with Example9
trait Example9 extends OptiMLApplication {
  def main() = {
    // simple diamond-shaped graph
    // the first type parameter is the vertex data type
    // the second type parameter is the edge data type
    val g = Graph[Int,Double]()

    // vertices have a single field to store a data value
    // for this vertex, 0 is the data value
    val a = Vertex(g, 0)
    g.addVertex(a)
    val b = Vertex(g, 1)
    g.addVertex(b)
    val c = Vertex(g, 2)
    g.addVertex(c)
    val d = Vertex(g, 3)
    g.addVertex(d)

    // edges have two fields to store data (in and out), designed for message-passing
    // for this edge, 1.0 is the in data and -1.0 is the out data
    val ab = Edge(g, 1.0, -1.0, a, b)
    g.addEdge(ab,a,b)

    val ac = Edge(g, 2.0, -5.0, a, c)
    g.addEdge(ac,a,c)

    val bd = Edge(g, 3.7, 1.1, b, d)
    g.addEdge(bd,b,d)

    val cd = Edge(g, 0.0, 9.0, c, d)
    g.addEdge(cd,c,d)

    // graphs have separate phases for construction and operation
    // they should not be used until 'freeze' is called
    g.freeze()

    for (v <- g.vertices) {
      println("vertex " + v.data)
      println("  has edges: ")
      for (e <- v.edges) {
        println("    " + e.inData + " / " + e.outData)
      }
    }
  }
}
*/

// mutation
object Example10Compiler extends OptiMLApplicationCompiler with Example10
object Example10Interpreter extends OptiMLApplicationInterpreter with Example10
trait Example10 extends OptiMLApplication {
  def main() = {
    val vMut = DenseVector[Double](1000, true) // mutable vector initialized to all zeros
    val vImm = DenseVector.rand(1000) // immutable vector initialized to random values

    val vImm2 = vMut+5 // mutability is not inherited! the new vector is immutable
    val vMut2 = vImm2.mutable // but we can always ask for a mutable copy if we need one

    var i = 0
    while (i < vMut.length) {
      if (i % 10 == 0) {
        vMut(i) = 1 // ok
        // vImm(i) = 1 // would cause a stage-time error!
      }
      i += 1
    }

    println("vMut(10): " + vMut(10))

    // nested mutable objects are not allowed!
    val vNestedMut = DenseVector[DenseVector[Double]](10, true)
    vNestedMut(0) = vImm2 // ok
    // vNestedMut(0) = vMut // would cause a stage-time error!
  }
}

// using types
object Example11Compiler extends OptiMLApplicationCompiler with Example11
object Example11Interpreter extends OptiMLApplicationInterpreter with Example11
trait Example11 extends OptiMLApplication {
  def main() = {
    // an explicitly-typed result
    // Rep[T] is a type constructor representing staged values
    val v: Rep[DenseVector[Double]] = DenseVector.rand(1000)

    // values that are not wrapped in Rep[] are evaluated at compile time, e.g.
    val scalar: Double = 5*12/3.3+1 // this is executed immediately when we are staging
                                    // no code will be generated for these statements

    // you can still use unstaged values in staged computations
    val v2: Rep[DenseVector[Double]] = v*scalar

    // all values returned by the DSL are staged
    val scalar2: Rep[Double] = v2.sum

    println(scalar2)
  }
}

// code organization
object Example12Compiler extends OptiMLApplicationCompiler with Example12
object Example12Interpreter extends OptiMLApplicationInterpreter with Example12
trait Example12 extends OptiMLApplication with Example12work {
  def main() = {
    // code can be organized into different methods and traits
    // these methods get inlined during staging
    val v = DenseVector.rand(1000)
    doWork(v) // defined in Example12work
    doWork2(v)
    val a = doWork3(v)
    println(a)
  }
}
trait Example12work extends OptiMLApplication {
  // methods signatures require types; return types are optional
  def doWork(v: Rep[DenseVector[Double]]) = {
    println("v length is: " + v.length)
  }

  // methods can use generic types, too
  // but you have to include this ":Manifest" boilerplate
  // Manifest is a Scala object that stores type information for T
  def doWork2[T:Manifest](v: Rep[DenseVector[T]]) = {
    println("v(0) is: " + v(0))
  }

  // an example method returning a value with an explicit return type
  def doWork3(v: Rep[DenseVector[Double]]): Rep[Double] = {
    if (v.length > 10) {
      v(10)
    }
    else {
      0.0
    }
  }
}


// user-defined type
object Example13Compiler extends OptiMLApplicationCompiler with Example13
object Example13Interpreter extends OptiMLApplicationInterpreter with Example13
trait Example13 extends OptiMLApplication {
  def main() = {
    // type alias is for convenience
    type MyStruct = Record{val data: Int; val name: String}

    // method to construct a new instance of MyStruct, also for convenience
    def newMyStruct(_data: Rep[Int], _name: Rep[String]) =
      // a user-defined struct instance is declared as a new Record
      new Record {
        val data = _data
        val name = _name
      }

    // we can use our struct with normal OptiML data types
    val v1 = (0::100) { i => newMyStruct(i, "struct " + i) }

    // we can even use math operators by defining how arithmetic works with MyStruct
    implicit def myStructArith: Arith[MyStruct] = new Arith[MyStruct] {
      def +=(a: Rep[MyStruct], b: Rep[MyStruct])(implicit ctx: SourceContext) =
        newMyStruct(a.data+b.data,a.name)
      def +(a: Rep[MyStruct], b: Rep[MyStruct])(implicit ctx: SourceContext) =
        newMyStruct(a.data+b.data,a.name+" pl "+b.name)
      def -(a: Rep[MyStruct], b: Rep[MyStruct])(implicit ctx: SourceContext) =
        newMyStruct(a.data-b.data,a.name+" mi "+b.name)
      def *(a: Rep[MyStruct], b: Rep[MyStruct])(implicit ctx: SourceContext) =
        newMyStruct(a.data*b.data,a.name+" ti "+b.name)
      def /(a: Rep[MyStruct], b: Rep[MyStruct])(implicit ctx: SourceContext) =
        newMyStruct(a.data/b.data,a.name+" di "+b.name)
      def abs(a: Rep[MyStruct])(implicit ctx: SourceContext) =
        newMyStruct(arith_abs(a.data),"abs "+a.name)
      def exp(a: Rep[MyStruct])(implicit ctx: SourceContext) =
        newMyStruct(arith_exp(a.data).AsInstanceOf[Int],"exp "+a.name)
      def log(a: Rep[MyStruct])(implicit ctx: SourceContext) =
        newMyStruct(arith_log(a.data).AsInstanceOf[Int],"log "+a.name)
      def empty(implicit ctx: SourceContext) =
        newMyStruct(0,"empty")
      def zero(a: Rep[MyStruct])(implicit ctx: SourceContext) =
        newMyStruct(0,"zero")
    }

    val v2 = (0::100) { i => newMyStruct(i*i, "struct2 " + i) }

    val result = v1+v2
    println("result(10) with name " + result(10).name + " has data " + result(10).data)
  }
}

// Scopes #1: In this example, we invoke an OptiML program from within an ordinary Scala program
object Example14 {
  def main(args: Array[String]) {
    // ordinary Scala
    println("scala 1")

    // storage for the answer
    var ans: Double = 0.0

    // executes immediately in OptiML
    OptiML {
      println("inside OptiML!")
      val v = DenseVector.rand(100)
      ans = v.sum
      ()
    }

    // ordinary Scala again
    println("computed sum: " + ans)
  }
}

// Scopes #2: In this example, we stage two OptiML snippets back-to-back to generate a combined program,
// which can then be re-staged (run with delitec again). We could write multi-DSL programs this way.
//
// to run:
//   1. delitec Example15
//   2. mv restage-scopes.scala apps/src/
//   3. sbt compile
//   4. delitec RestageApplicationRunner
//   5. delite RestageApplicationRunner
object Example15 {
  def main(args: Array[String]) {
    // ordinary Scala
    println("scala 1")

    BeginScopes() // marker to begin scope file

    val a =
      OptiML_ {
        println("inside OptiML!")
        val v = (0::10) { i => 1.0 }
        DRef(v.sum)
      }

    OptiML_ {
      val in = a.get
      println("in scope b, got input: ")
      println(in)
      println("final answer is: ")
      println(in+100)
      ()
    }

    EndScopes() // marker to complete the scope file
  }
}
