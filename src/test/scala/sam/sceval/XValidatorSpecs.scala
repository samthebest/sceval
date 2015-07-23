package sam.sceval

import org.apache.spark.rdd.RDD
import org.scalacheck.{Arbitrary, Gen}
import org.specs2.ScalaCheck
import org.specs2.matcher.MatchResult
import org.specs2.mutable.Specification
import Arbitrary.arbitrary

import scala.util.{Random, Success, Failure, Try}

class XValidatorSpecs extends Specification with ScalaCheck with IntellijHighlighingTrick {
  sequential
  val sc = StaticSparkContext.staticSc

  val folds = 5
  val xvalidator = XValidator(folds = folds, evalBins = Some(4))
  implicit val _: Arbitrary[Int] = Arbitrary(Gen.choose(1, 10))

  def listOfNs[T: Arbitrary](Ns: List[Int]): Gen[List[T]] = Gen.frequency(Ns.map(n => (1, Gen.listOfN(n, arbitrary[T]))): _*)

  implicit val arbitraryListIntBool: Arbitrary[List[(Int, Boolean)]] =
    Arbitrary(listOfNs[(Int, Boolean)](List(1, 2, 4, 8, 10, 15, 20, 50, 100)))

  "XValidator" should {
    "split into fold folds and are same size (with possible off by ones)" ! check(prop(
      (featuresAndLabels: List[(Int, Boolean)], partitions: Int) =>
        Try(xvalidator.split(sc.makeRDD(featuresAndLabels, partitions))) match {
          case Failure(e) => e.isInstanceOf[IllegalArgumentException] must beTrue
          case Success(rdd) =>
            val foldSizeMap = rdd.map(p => (p._1, 1)).reduceByKey(_ + _).collect().toMap
            foldSizeMap.size must_== folds

            val total = foldSizeMap.values.sum
            foldSizeMap.values.toSet must_===
              (if (total % folds == 0) Set(total / folds) else Set(total / folds, total / folds + 1))
        }))

    "Not lose any records in full cross validation" ! check(prop(
      (featuresAndLabels: List[(Int, Boolean)], partitions: Int) => Try(xvalidator.xval[Int](
        trainAndScoreByModel = _.map {
          case (fold, feature, label) => (fold, new Random().nextDouble(), label)
        },
        featuresAndLabel = sc.makeRDD(featuresAndLabels, partitions))
      ) match {
        case Failure(e) => e.isInstanceOf[IllegalArgumentException] must beTrue
        case Success(confusions) => confusions.head.total must_=== featuresAndLabels.size.toLong
      }))

    "compute correct BinaryConfusionMatricies" in {
      val scoresAndLabelsByModel: RDD[(Int, Double, Boolean)] = sc.makeRDD(
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
          )).flatMap {
          case (fold, examples) => examples.map {
            case (score, label) => (fold, score, label)
          }
        }
        .toSeq, 4)

      XValidator(folds = 2, evalBins = Some(2)).evaluate(scoresAndLabelsByModel) must_=== Array(
        BinaryConfusionMatrix(tp = 2, fp = 3, tn = 0, fn = 0) + BinaryConfusionMatrix(tp = 4, fp = 1, tn = 0, fn = 0),
        BinaryConfusionMatrix(tp = 1, fp = 1, tn = 2, fn = 1) + BinaryConfusionMatrix(tp = 2, fp = 0, tn = 1, fn = 2)
      )
    }
  }
}
