package sam.sceval

import org.specs2.mutable.Specification

class BinaryConfusionMatrixSpecs extends Specification with RoughlyUtil {
  val confusionMatrix = BinaryConfusionMatrix(tp = 20, fp = 180, tn = 1820, fn = 10)

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

  "ConfusionMatrix class" should {
    "compute correct precision" in {
      confusionMatrix.precision ~= precision
    }
    "compute correct recall" in {
      confusionMatrix.recall ~= recall
    }
    "compute correct prior" in {
      confusionMatrix.prior ~= prior
    }
    "compute correct specificity" in {
      confusionMatrix.specificity ~= specificity
    }
    "compute correct negative predictive value" in {
      confusionMatrix.negativePredictiveValue ~= negativePredictiveValue
    }
    "compute correct fallout" in {
      confusionMatrix.fallOut ~= fallout
    }
    "compute correct false discovery rate" in {
      confusionMatrix.falseDiscoveryRate ~= falseDiscoveryRate
    }
    "compute correct accuracy" in {
      confusionMatrix.accuracy ~= accuracy
    }
    "compute correct uplift" in {
      confusionMatrix.uplift ~= uplift
    }
  }
}