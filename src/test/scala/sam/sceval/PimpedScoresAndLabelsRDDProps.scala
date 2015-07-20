package sam.sceval

import org.scalacheck.{Arbitrary, Gen}
import org.specs2.ScalaCheck
import org.specs2.matcher.Parameters
import org.specs2.mutable.Specification
import sam.sceval.EvaluationPimps._
import Arbitrary.arbitrary

class PimpedScoresAndLabelsRDDProps extends Specification with ScalaCheck with IntellijHighlighingTrick {
  sequential

  val sc = StaticSparkContext.staticSc

  implicit val params = Parameters(minTestsOk = 50)

  case class TestCase(scoresAndLabels: List[(Double, Boolean)], bins: Int, partitions: Int)

  implicit val int: Arbitrary[Int] = Arbitrary(Gen.choose(1, 10))

  val scoreLabelGen = for {
    score <- Gen.choose(0.0, 1.0)
    label <- arbitrary[Boolean]
  } yield (score, label)

  implicit val _: Arbitrary[TestCase] = Arbitrary(for {
    elemPerBin <- Gen.choose(1, 10)
    bins <- Gen.choose(1, 10)
    partitions <- Gen.choose(1, 10)
    scoresAndLabels <- Gen.listOfN(elemPerBin * bins, scoreLabelGen)
  } yield TestCase(scoresAndLabels, bins, partitions))

  implicit val modelToScoresAndLabels: Arbitrary[List[Map[Int, (Double, Boolean)]]] = Arbitrary(Gen.nonEmptyListOf(for {
    scoresAndLables <- Gen.listOfN(10, scoreLabelGen)
  } yield scoresAndLables.zipWithIndex.map(_.swap).toMap))

  "Confusion methods" should {
    // Essentially hijaks the MLLib implementation as a correct single threaded version then uses this for CDD
    "agree for uniformly distributed scores with 1 partition" ! check(prop(
      (_: TestCase) match {
        case TestCase(scoresAndLabels, bins, partitions) =>
          (bins > 1 && scoresAndLabels.nonEmpty && scoresAndLabels.map(_._1).distinct.size == scoresAndLabels.size) ==> {
            sc.makeRDD(scoresAndLabels, 1).scoresAndConfusions(bins).map(_._2).collect().toList must_===
              sc.makeRDD(scoresAndLabels, partitions).confusions(bins = Some(bins)).toList.sortBy(_.predictedPositives)
          }
      }))

    "Returns all confusions from largest to smallest" ! check(prop(
      (modelToScoresAndLabels: List[Map[Int, (Double, Boolean)]], bins: Int, partitions: Int) => (bins > 1) ==> {
        sc.makeRDD(modelToScoresAndLabels).confusionsByModel(bins = Some(bins)).collect().foreach {
          case (_, confusions) => confusions.dropRight(1).zip(confusions.drop(1)).foreach {
            case (larger, smaller) => larger.tp must beGreaterThanOrEqualTo(smaller.tp)
          }
        }
      }))
  }
}
