package sam.sceval

import org.specs2.mutable.Specification

class AreaUnderCurveSpecs extends Specification {
  val sc = StaticSparkContext.staticSc

  def testCurve(expected: Double, curve: Seq[(Double, Double)], description: String) =
    "Compute " + description + " correctly" in {
      AreaUnderCurve(curve) must beCloseTo(expected, 0.0001)
      val rddCurve = sc.parallelize(curve, 2)
      AreaUnderCurve(rddCurve) must beCloseTo(expected, 0.0001)
    }

  "AreaUnderCurve" should {
    testCurve(4.0, Seq((0.0, 0.0), (1.0, 1.0), (2.0, 3.0), (3.0, 0.0)), "auc of 4 point curve")
    testCurve(0.0, Nil, "auc of an empty curve")
    testCurve(0.0, Seq((1.0, 1.0)), "auc of a curve with a single point")
  }
}
