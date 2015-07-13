package sam.sceval

import org.apache.spark.rdd.RDD
import org.scalacheck.{Arbitrary, Gen}
import org.specs2.ScalaCheck
import org.specs2.mutable.Specification

import scala.util.Try

class XValidatorSpecs extends Specification with ScalaCheck with IntellijHighlighingTrick {
  sequential
  val sc = StaticSparkContext.staticSc

  val folds = 5
  val xvalidator = XValidator(folds = folds, evalBins = Some(4))
  val _: Arbitrary[Int] = Arbitrary(Gen.choose(1, 10))

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
      // RDD[fold -> (score, label)]

      val xvalidator = XValidator(folds = 2, evalBins = Some(4))

      // Ten examples

      // Comments left in to show which have been held out for each fold
      // fold -> List[(exampleId, (score, label))]
      val foldToExampleIDScoreAndLabel =
        Map(
          0 -> List(
            (0, (0.0, false)),
            (1, (0.1, false)),
            (2, (0.2, true)),
            (3, (0.3, false)),
            (4, (0.4, false)),
            (5, (0.5, false)),
            (6, (0.6, false)),
            (7, (0.7, false))
            //          (8, (0.8, false)),
            //          (9, (0.9, false))
          ),
          1 -> List(
            (0, (0.0, false)),
            (1, (0.1, false)),
            (2, (0.2, false)),
            (3, (0.3, false)),
            (4, (0.4, false)),
            (5, (0.5, false)),
            //          (6, (0.6, false)),
            //          (7, (0.7, false)),
            (8, (0.8, false)),
            (9, (0.9, false))
          ),
          2 -> List(
            (0, (0.0, false)),
            (1, (0.1, false)),
            (2, (0.2, false)),
            (3, (0.3, false)),
            //          (4, (0.4, false)),
            //          (5, (0.5, false)),
            (6, (0.6, false)),
            (7, (0.7, false)),
            (8, (0.8, false)),
            (9, (0.9, false))
          ),
          3 -> List(
            (0, (0.0, false)),
            (1, (0.1, false)),
            //          (2, (0.2, false)),
            //          (3, (0.3, false)),
            (4, (0.4, false)),
            (5, (0.5, false)),
            (6, (0.6, false)),
            (7, (0.7, false)),
            (8, (0.8, false)),
            (9, (0.9, false))
          ),
          4 -> List(
            //          (0, (0.0, false)),
            //          (1, (0.1, false)),
            (2, (0.2, false)),
            (3, (0.3, false)),
            (4, (0.4, false)),
            (5, (0.5, false)),
            (6, (0.6, false)),
            (7, (0.7, false)),
            (8, (0.8, false)),
            (9, (0.9, false))
          )
        )

      val scoresAndLabelsByModel: RDD[Map[Int, (Double, Boolean)]] = sc.makeRDD(
        foldToExampleIDScoreAndLabel.flatMap {
          case (fold, examples) => examples.map {
            case (exampleId, (score, label)) => (exampleId, fold, score, label)
          }
        }
        .groupBy(_._1).mapValues(
            _.map {
              case (exampleId, fold, score, label) => (fold, (score, label))
            }
            .toMap)
        .values.toSeq, 4)

      // 4 x data points
      xvalidator.evaluate(scoresAndLabelsByModel) must_=== Array(
        BinaryConfusionMatrix()
      )
    }
  }
}
