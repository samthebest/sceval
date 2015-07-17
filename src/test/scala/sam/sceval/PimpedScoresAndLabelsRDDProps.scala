package sam.sceval

import org.scalacheck.{Arbitrary, Gen}
import org.specs2.ScalaCheck
import org.specs2.matcher.Parameters
import org.specs2.mutable.Specification
import sam.sceval.EvaluationPimps._
import Arbitrary.arbitrary

import scala.collection.immutable.IndexedSeq
import scala.util.Random

class PimpedScoresAndLabelsRDDProps extends Specification with ScalaCheck with IntellijHighlighingTrick {
  sequential

  val sc = StaticSparkContext.staticSc

  case class TestCase(scoresAndLabels: List[(Double, Boolean)], bins: Int, partitions: Int)

  implicit val params = Parameters(minTestsOk = 50)
//  implicit val arbInt: Arbitrary[Int] = Arbitrary(Gen.choose(1, 10))
  implicit val _: Arbitrary[TestCase] = Arbitrary(for {
    elemPerBin <- Gen.choose(1, 10)
    bins <- Gen.choose(1, 10)
    partitions <- Gen.choose(1, 10)
    scoresAndLabels <- Gen.listOfN(elemPerBin * bins, for {
      score <- Gen.choose(0.0, 1.0)
      label <- arbitrary[Boolean]
    } yield (score, label))
  } yield TestCase(scoresAndLabels, bins, partitions))

  // TODO Refactor this to have a nice arbitrary

  // Essentially hijaks the MLLib implementation as a correct single threaded version then uses this for CDD
  "Confusion methods" should {
    "agree for uniformly distributed scores with 1 partition" ! check(prop(
      (_: TestCase) match {
        case TestCase(scoresAndLabels, bins, partitions) =>
          (bins > 1 && scoresAndLabels.nonEmpty && scoresAndLabels.map(_._1).distinct.size == scoresAndLabels.size) ==> {
            sc.makeRDD(scoresAndLabels, 1).scoresAndConfusions(bins).map(_._2).collect().toList must_===
              sc.makeRDD(scoresAndLabels, partitions).confusions(bins = Some(bins)).toList.sortBy(_.predictedPositives)
          }
      }))


    //    "agree for uniformly distributed scores with 1 partition" ! check(prop(
    //      (elemPerBin: Int, bins: Int, partitions: Int) => {
    //        val scoresAndLabels: IndexedSeq[(Double, Boolean)] = (1 to elemPerBin * bins).map(_ => (rand.nextDouble(), rand.nextBoolean()))
    //        (bins > 1 && scoresAndLabels.nonEmpty && scoresAndLabels.map(_._1).distinct.size == scoresAndLabels.size) ==> {
    //          sc.makeRDD(scoresAndLabels, 1).scoresAndConfusions(bins).map(_._2).collect().toList must_===
    //            sc.makeRDD(scoresAndLabels, partitions).confusions(bins = Some(bins)).toList.sortBy(_.predictedPositives)
    //        }
    //      }))
  }
}
