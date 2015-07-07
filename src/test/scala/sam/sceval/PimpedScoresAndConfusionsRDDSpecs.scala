package sam.sceval

import EvaluationPimps._
import org.specs2.matcher.MatchResult
import org.specs2.mutable.Specification

class PimpedScoresAndConfusionsRDDSpecs extends Specification {
  sequential
  val sc = StaticSparkContext.staticSc

  val epsilon = 0.00001

  implicit class RoughlyDouble(d: Double) {
    def ~=(other: Double): MatchResult[Double] = d must beCloseTo(other, epsilon)
  }

  def sequencesMatch(left: Seq[Double], right: Seq[Double]) = left.zip(right).forall {
    case (l, r) => l ~= r
  }

  def tupleSequencesMatch(left: Seq[(Double, Double)],
                          right: Seq[(Double, Double)]) = left.zip(right).forall {
    case ((ll, lr), (rl, rr)) => (ll ~= rl) and (lr ~= rr)
  }

  def validateMetrics(scoreAndLabels: Seq[(Double, Boolean)],
                      expectedThresholds: Seq[Double],
                      expectedROCCurve: Seq[(Double, Double)],
                      expectedPRCurve: Seq[(Double, Double)],
                      expectedFMeasures1: Seq[Double],
                      expectedFMeasures2: Seq[Double],
                      expectedPrecisions: Seq[Double],
                      expectedRecalls: Seq[Double]) = {
    val metrics = sc.parallelize(scoreAndLabels, 2).scoresAndConfusions()

    "Compute thresholds correctly" in {
      sequencesMatch(metrics.thresholds().collect(), expectedThresholds)
    }

    "Compute ROC correctly" in {
      tupleSequencesMatch(metrics.roc().collect(), expectedROCCurve)
    }

    "Compute AUC correctly" in {
      metrics.areaUnderROC() must beCloseTo(AreaUnderCurve(expectedROCCurve), epsilon)
    }

    "Compute Precision by threshold curve correctly" in {
      tupleSequencesMatch(metrics.precisionRecallCurve().collect().toList.sortBy(_._1), expectedPRCurve.sortBy(_._1))
    }

    "Compute Area under PR correctly" in {
      metrics.areaUnderPR() must beCloseTo(AreaUnderCurve(expectedPRCurve), epsilon)
    }

    "Compute f1Measure by threshold correctly" in {
      tupleSequencesMatch(metrics.f1MeasureByThreshold().collect(), expectedThresholds.zip(expectedFMeasures1))
    }

    "Compute f1Measure(2.0) by threshold correctly" in {
      tupleSequencesMatch(metrics.f1MeasureByThreshold(2.0).collect(), expectedThresholds.zip(expectedFMeasures2))
    }

    "Compute precision by threshold correctly" in {
      tupleSequencesMatch(metrics.precisionByThreshold().collect(), expectedThresholds.zip(expectedPrecisions))
    }

    "Compute recall by threshold correctly" in {
      tupleSequencesMatch(metrics.recallByThreshold().collect(), expectedThresholds.zip(expectedRecalls))
    }
  }

  "binary evaluation metrics" should {
    val numTruePositives = Seq(1, 3, 3, 4)
    val numFalsePositives = Seq(0, 1, 2, 3)
    val precisions = numTruePositives.zip(numFalsePositives).map {
      case (t, f) => t.toDouble / (t + f)
    }
    val recalls = numTruePositives.map(t => t.toDouble / 4)
    val pr = recalls.zip(precisions)
    validateMetrics(
      scoreAndLabels = Seq((0.1, false), (0.1, true), (0.4, false), (0.6, false), (0.6, true), (0.6, true), (0.8, true)),
      expectedThresholds = Seq(0.8, 0.6, 0.4, 0.1),
      expectedROCCurve = Seq((0.0, 0.0)) ++ numFalsePositives.map(f => f.toDouble / 3).zip(recalls) ++ Seq((1.0, 1.0)),
      expectedPRCurve = Seq((0.0, 1.0)) ++ pr,
      expectedFMeasures1 = pr.map {
        case (r, p) => 2.0 * (p * r) / (p + r)
      },
      expectedFMeasures2 = pr.map {
        case (r, p) => 5.0 * (p * r) / (4.0 * p + r)
      },
      expectedPrecisions = precisions,
      expectedRecalls = recalls
    )
  }

  "binary evaluation metrics for RDD where all examples have positive label" should {
    val precisions = Seq(1.0)
    val recalls = Seq(1.0)
    val pr = recalls.zip(precisions)

    validateMetrics(
      scoreAndLabels = Seq((0.5, true), (0.5, true)),
      expectedThresholds = Seq(0.5),
      expectedROCCurve = Seq((0.0, 0.0)) ++ Seq(0.0).zip(recalls) ++ Seq((1.0, 1.0)),
      expectedPRCurve = Seq((0.0, 1.0)) ++ pr,
      expectedFMeasures1 = pr.map {
        case (r, p) => 2.0 * (p * r) / (p + r)
      },
      expectedFMeasures2 = pr.map {
        case (r, p) => 5.0 * (p * r) / (4.0 * p + r)
      },
      expectedPrecisions = precisions,
      expectedRecalls = recalls
    )
  }

  "binary evaluation metrics for RDD where all examples have negative label" should {
    val precisions = Seq(0.0)
    val recalls = Seq(0.0)
    val pr = recalls.zip(precisions)

    validateMetrics(
      scoreAndLabels = Seq((0.5, false), (0.5, false)),
      expectedThresholds = Seq(0.5),
      expectedROCCurve = Seq((0.0, 0.0)) ++ Seq(1.0).zip(recalls) ++ Seq((1.0, 1.0)),
      expectedPRCurve = Seq((0.0, 1.0)) ++ pr,
      expectedFMeasures1 = pr.map {
        case (0, 0) => 0.0
        case (r, p) => 2.0 * (p * r) / (p + r)
      },
      expectedFMeasures2 = pr.map {
        case (0, 0) => 0.0
        case (r, p) => 5.0 * (p * r) / (4.0 * p + r)
      },
      expectedPrecisions = precisions,
      expectedRecalls = recalls
    )
  }

  "binary evaluation metrics with downsampling" should {
    val scoreAndLabels = Seq(
      (0.1, false), (0.2, false), (0.3, true), (0.4, false), (0.5, false), (0.6, true), (0.7, true), (0.8, false),
      (0.9, true)
    )

    val scoreAndLabelsRDD = sc.parallelize(scoreAndLabels, 1)

    val original = scoreAndLabelsRDD.scoresAndConfusions()
    val originalROC = original.roc().collect().sorted.toList

    "have correct size" in {
      2 + scoreAndLabels.size must_== originalROC.size
    }

    "Have correct roc curve" in {
      List(
        (0.0, 0.0), (0.0, 0.25), (0.2, 0.25), (0.2, 0.5), (0.2, 0.75), (0.4, 0.75), (0.6, 0.75), (0.6, 1.0), (0.8, 1.0),
        (1.0, 1.0), (1.0, 1.0)
      ) must_== originalROC
    }

    val numBins = 4

    val downsampledROC = scoreAndLabelsRDD.scoresAndConfusions(numBins).roc().collect().sorted.toList

    "Have correct downsampled size" in {
      2 + (numBins + (if (scoreAndLabels.size % numBins == 0) 0 else 1)) must_== downsampledROC.size
    }

    "Have correct downsampled curve" in {
      List((0.0, 0.0), (0.2, 0.25), (0.2, 0.75), (0.6, 0.75), (0.8, 1.0), (1.0, 1.0), (1.0, 1.0)) must_== downsampledROC
    }
  }
}
