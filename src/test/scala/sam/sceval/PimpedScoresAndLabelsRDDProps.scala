package sam.sceval

import EvaluationPimps._
import org.scalacheck.{Arbitrary, Gen, Prop}
import org.specs2.ScalaCheck
import org.specs2.matcher.{MatchResult, Parameters}
import org.specs2.mutable.Specification

import scala.util.Random

class PimpedScoresAndLabelsRDDProps extends Specification with ScalaCheck {
  sequential

  val sc = StaticSparkContext.staticSc
  val rand = new Random()
  // Trick to make Intellij highlight correctly
  implicit def toProp(m: MatchResult[Any]): Prop = resultProp(m)
  implicit val params = Parameters(minTestsOk = 50)
  implicit val _: Arbitrary[Int] = Arbitrary(Gen.choose(1, 10))

  // Essentially hijaks the MLLib implementation as a correct single threaded version then uses this for CDD
  "Confusion methods for uniformly distributed scores with 1 partition" should {
    "agree" ! check(prop((elemPerBin: Int, bins: Int, partitions: Int) => {
      val scoresAndLabels = (1 to elemPerBin * bins).map(_ => (rand.nextDouble(), rand.nextBoolean()))
      (bins > 1 && scoresAndLabels.nonEmpty && scoresAndLabels.map(_._1).distinct.size == scoresAndLabels.size) ==> {
        sc.makeRDD(scoresAndLabels, 1).scoresAndConfusions(bins).map(_._2).collect().toList must_===
          sc.makeRDD(scoresAndLabels, partitions).confusions(bins = Some(bins)).toList.sortBy(_.predictedPositives)
      }
    }))
  }
}
