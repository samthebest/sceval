package sam.sceval

import org.specs2.mutable.Specification

class BinaryConfusionMatrixSpecs extends Specification {
  val tps = 20
  val fps = 180
  val tns = 1820
  val fns = 10
  val all = tps + fps + tns + fns

  // Calculated manually
  val precision = 0.1
  val recall = 0.6666666666666666
  val prior = 0.014778325123152709
  val specificity = 0.91
  val negativePredictiveValue = 0.994535519125683
  val fallout = 0.09
  val falseDiscoveryRate = 0.9
  val accuracy = 0.9064039408866995
  val uplift = 6.766666666666667

  val confusionMatrix = BinaryConfusionMatrix(tps, fps, tns, fns)

  "ConfusionMatrix class" should {
    "compute correct precision" in {
      confusionMatrix.precision must_== precision
    }
    "compute correct recall" in {
      confusionMatrix.recall must_== recall
    }
    "compute correct prior" in {
      confusionMatrix.prior must_== prior
    }
    "compute correct specificity" in {
      confusionMatrix.specificity must_== specificity
    }
    "compute correct negative predictive value" in {
      confusionMatrix.negativePredictiveValue must_== negativePredictiveValue
    }
    "compute correct fallout" in {
      confusionMatrix.fallOut must_== fallout
    }
    "compute correct false discovery rate" in {
      confusionMatrix.falseDiscoveryRate must_== falseDiscoveryRate
    }
    "compute correct accuracy" in {
      confusionMatrix.accuracy must_== accuracy
    }
    "compute correct uplift" in {
      confusionMatrix.uplift must_== uplift
    }
  }
}