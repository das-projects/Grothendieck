import optiml.compiler._
import optiml.shared._
import optiml.library._
import ppl.tests.scalatest._

object SimpleDenseVectorArithmeticRunnerI extends OptiMLApplicationInterpreter with ForgeTestRunnerInterpreter with SimpleDenseVectorArithmetic
object SimpleDenseVectorArithmeticRunnerC extends OptiMLApplicationCompiler with ForgeTestRunnerCompiler with SimpleDenseVectorArithmetic
trait SimpleDenseVectorArithmetic extends ForgeTestModule with DenseLinearAlgebraTestsCommon {
  def main() = {
    // TODO: these can't be factored out right now because they throw an NPE when the test is being initialized
    val rowA = DenseVector(11.0, 22.0, 33.0)
    val rowB = DenseVector(-5.3, -17.2, -131.0)
    val rowD = DenseVector(-1.1, -6.2)
    val colC = DenseVector(7.0, 3.2, 13.3).t

    // A*B piecewise
    val ansVec = rowA*rowB
    collect(check(ansVec, DenseVector(-58.3, -378.4, -4323.0)))

    // dot product
    val ansDbl = rowA *:* colC
    collect(check(ansDbl, 586.3))

    // outer product
    val ansMat = colC ** rowA
    collect(check(ansMat, DenseMatrix(DenseVector(77.0, 154.0, 231.0), DenseVector(35.2, 70.4, 105.6), DenseVector(146.3, 292.6, 438.9))))

    mkReport
  }
}

object SimpleDenseMatrixArithmeticRunnerI extends OptiMLApplicationInterpreter with ForgeTestRunnerInterpreter with SimpleDenseMatrixArithmetic
object SimpleDenseMatrixArithmeticRunnerC extends OptiMLApplicationCompiler with ForgeTestRunnerCompiler with SimpleDenseMatrixArithmetic
trait SimpleDenseMatrixArithmetic extends ForgeTestModule with DenseLinearAlgebraTestsCommon {
  def main() = {
    val rowA = DenseVector(11.0, 22.0, 33.0)
    val rowB = DenseVector(-5.3, -17.2, -131.0)
    val colC = DenseVector(7.0, 3.2, 13.3).t
    val m33 = DenseMatrix(rowA, rowB, colC.t)
    val m23 = DenseMatrix(DenseVector(3.5, 7.5, 9.0), DenseVector(-5.6, 8.2, 17.3))
    val m32 = DenseMatrix(DenseVector(0.07, 0.91), DenseVector(17.0, -10.0), DenseVector(-99.0,0.023))

    // matrix square multiplication
    val ansMat = m33*m33
    collect(check(ansMat, DenseMatrix(DenseVector(235.4, -30.8, -2080.1), DenseVector(-884.14, -239.96, 336.0), DenseVector(153.14, 141.52, -11.31))))

    // inverse
    // val ansMat2 = m33.inv
    // collect(check(ansMat2, DenseMatrix(DenseVector(-.0145, 0.0143, 0.1765), DenseVector(0.0645, 0.0065, -0.0965), DenseVector(-0.0079, -0.0091, 0.0055))))

    // matrix transpose
    val ansMat3 = m33.t
    collect(check(ansMat3, DenseMatrix(DenseVector(11.0, -5.3, 7.0), DenseVector(22.0, -17.2, 3.2), DenseVector(33.0, -131.0, 13.3))))

    // matrix multiplication
    val ansMat4 = m33*m32
    collect(check(ansMat4, DenseMatrix(DenseVector(-2892.223, -209.2310), DenseVector(12676.229, 164.1640), DenseVector(-1261.81, -25.3241))))

    // chained matrix multiplication
    val ansMat5 = m23*m33*m32
    collect(check(ansMat5, DenseMatrix(DenseVector(73592.6225, 271.0046), DenseVector(98312.252799, 2079.73147))))

    // empty matrix multiplication (should not throw exception)
    val ansMat6 = DenseMatrix.zeros(0,0)*DenseMatrix.zeros(0,0)
    collect(ansMat6.numRows == 0 && ansMat6.numCols == 0)

    // summation
    val ans = m23.sum
    collect(check(ans, 39.9))

    mkReport
  }
}

object CombinedVecMatArithmeticRunnerI extends OptiMLApplicationInterpreter with ForgeTestRunnerInterpreter with CombinedVecMatArithmetic
object CombinedVecMatArithmeticRunnerC extends OptiMLApplicationCompiler with ForgeTestRunnerCompiler with CombinedVecMatArithmetic
trait CombinedVecMatArithmetic extends ForgeTestModule with DenseLinearAlgebraTestsCommon {
  def main() = {
    val rowA = DenseVector(11.0, 22.0, 33.0)
    val rowB = DenseVector(-5.3, -17.2, -131.0)
    val rowD = DenseVector(-1.1, -6.2)
    val colC = DenseVector(7.0, 3.2, 13.3).t
    val colE = DenseVector(.05, 9.97).t
    val m33 = DenseMatrix(rowA, rowB, colC.t)
    val m23 = DenseMatrix(DenseVector(3.5, 7.5, 9.0), DenseVector(-5.6, 8.2, 17.3))
    val m32 = DenseMatrix(DenseVector(.07, .91), DenseVector(17.0, -10.0), DenseVector(-99.0,0.023))
    val alpha = 4.235
    val beta = -99.759

    val ansVec = m23*colC
    collect(check(ansVec, DenseVector(168.2, 217.13)))

    val ansVec2 = rowB*m32
    collect(check(ansVec2, DenseVector(12676.229, 164.1640)))

    // val a1 = m23*alpha
    // val a2 = a1 * (m33.t.inv)
    // val a3 = a2 * m32
    // val ansVec3 = a3 * (rowD.t*colE*beta)
    // collect(check(ansVec3, DenseVector(194179.526, 593097.843)))

    mkReport
  }
}

trait DenseLinearAlgebraTestsCommon extends OptiMLApplication with GenOverloadHack {
  this: ForgeTestModule =>

  ////////////////
  // helpers

  def approx(x: Rep[Double], y: Rep[Double]): Rep[Boolean] = {
    // be very generous w.r.t. precision, because the ground truth
    // answers have not all been entered with high precision
    abs(x - y) < .01
  }

  def check(x: Rep[DenseVector[Double]], y: Rep[DenseVector[Double]]): Rep[Boolean] = {
    if (x.length != y.length) {
      false
    }
    else {
      val res = x.zip(y) { (a,b) => approx(a,b) }
      ((res count { _ == false }) == 0)
    }
  }

  def check(x: Rep[DenseMatrix[Double]], y: Rep[DenseMatrix[Double]])(implicit o: ROverload11): Rep[Boolean] = {
    if ((x.numRows != y.numRows) || (x.numCols != y.numCols)) {
      false
    }
    else {
      val res = x.zip(y) { (a,b) => approx(a,b) }
      ((res count { _ == false }) == 0)
    }
  }

  def check(x: Rep[Double], y: Rep[Double])(implicit o: ROverload12): Rep[Boolean] = {
    approx(x,y)
  }
}


class DenseLinearAlgebraSuiteInterpreter extends ForgeSuiteInterpreter {
  def testSimpleDenseVector() { runTest(SimpleDenseVectorArithmeticRunnerI) }
  def testSimpleDenseMatrix() { runTest(SimpleDenseMatrixArithmeticRunnerI) }
  def testCombinedVecMat() { runTest(CombinedVecMatArithmeticRunnerI) }
}

class DenseLinearAlgebraSuiteCompiler extends ForgeSuiteCompiler {
  def testSimpleDenseVector() { runTest(SimpleDenseVectorArithmeticRunnerC) }
  def testSimpleDenseMatrix() { runTest(SimpleDenseMatrixArithmeticRunnerC) }
  def testCombinedVecMat() { runTest(CombinedVecMatArithmeticRunnerC) }
}

