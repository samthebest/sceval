package sam.sceval

import org.apache.spark.rdd.RDD
import org.scalacheck.{Arbitrary, Gen}
import org.specs2.ScalaCheck
import org.specs2.mutable.Specification
import Arbitrary.arbitrary

import scala.util.Try

class XValidatorSpecs extends Specification with ScalaCheck with IntellijHighlighingTrick {
  sequential
  val sc = StaticSparkContext.staticSc

  val folds = 5
  val xvalidator = XValidator(folds = folds, evalBins = Some(4))
  implicit val _: Arbitrary[Int] = Arbitrary(Gen.choose(1, 10))

  // TODO Dry this, write listOfNs method

  implicit val arbitraryListIntBool: Arbitrary[List[(Int, Boolean)]] = Arbitrary(Gen.frequency(
    (1, Gen.listOfN(1, arbitrary[(Int, Boolean)])),
    (1, Gen.listOfN(2, arbitrary[(Int, Boolean)])),
    (1, Gen.listOfN(4, arbitrary[(Int, Boolean)])),
    (1, Gen.listOfN(8, arbitrary[(Int, Boolean)])),
    (1, Gen.listOfN(10, arbitrary[(Int, Boolean)])),
    (1, Gen.listOfN(15, arbitrary[(Int, Boolean)])),
    (1, Gen.listOfN(20, arbitrary[(Int, Boolean)])),
    (1, Gen.listOfN(50, arbitrary[(Int, Boolean)])),
    (1, Gen.listOfN(100, arbitrary[(Int, Boolean)]))
  ))

  "XValidator" should {
    // FIXME the List size needs to be limited other we get OOM problems
    "split into fold folds and are same size (with possible off by ones)" ! check(prop(
      (featuresAndLabels: List[(Int, Boolean)], partitions: Int) => {
        val trySplit = Try(xvalidator.split(sc.makeRDD(featuresAndLabels, partitions)))
        trySplit.isSuccess ==> {
          val foldSizeMap = trySplit.get.map(p => (p._1, 1)).reduceByKey(_ + _).collect().toMap
          foldSizeMap.size must_== folds

          val total = foldSizeMap.values.sum
          foldSizeMap.values.toSet must_===
            (if (total % folds == 0) Set(total / folds) else Set(total / folds, total / folds + 1))
        }
      }))

    "compute correct BinaryConfusionMatricies" in {
      val xvalidator2Folds = XValidator(folds = 2, evalBins = Some(2))

      // Comments left in to show which have been held out for each fold
      // fold -> List[(exampleId, (score, label))]
      val foldToExampleIDScoreAndLabel =
        Map(
          0 -> List(
            (0.0, false),
            (0.2, false),
            (0.4, true),

            (0.6, false),
            (0.8, true)
          ),
          1 -> List(
            (0.1, true),
            (0.3, true),
            (0.7, false),

            (0.9, true),
            (1.0, true)
          ))

//      val scoresAndLabelsByModel: RDD[Map[Int, (Double, Boolean)]] = sc.makeRDD(
//        foldToExampleIDScoreAndLabel.flatMap {
//          case (fold, examples) => examples.map {
//            case (score, label) => Map(fold -> (score, label))
//          }
//        }
//        .toSeq, 4)

      val scoresAndLabelsByModel: RDD[(Int, Double, Boolean)] = sc.makeRDD(
        foldToExampleIDScoreAndLabel.flatMap {
          case (fold, examples) => examples.map {
            case (score, label) => (fold, score, label)
          }
        }
        .toSeq
        , 4)

      // 4 x data points
      xvalidator2Folds.evaluate(scoresAndLabelsByModel) must_=== Array(
        BinaryConfusionMatrix(tp = 1, fp = 1, tn = 2, fn = 1) + BinaryConfusionMatrix(tp = 2, fp = 0, tn = 1, fn = 2),
          BinaryConfusionMatrix(tp = 2, fp = 3, tn = 0, fn = 0) + BinaryConfusionMatrix(tp = 4, fp = 1, tn = 0, fn = 0)
      )
    }

    // TODO Property based test to ensure they go largest to smallest (corresponds to lowest thresh to highest)
    // Need this for both EvaluationPimps and XValidator

  }
}
