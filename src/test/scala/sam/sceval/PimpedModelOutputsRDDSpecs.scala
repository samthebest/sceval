package sam.sceval

import EvaluationPimps._
import org.specs2.mutable.Specification

class PimpedModelOutputsRDDSpecs extends Specification {
  sequential
  val sc = StaticSparkContext.staticSc

  "confusions by model" should {
    "Compute correct Model to (volume, confusion) map with score collisions" in {
      val (modelA, modelB) = (0, 1)
      val scoreAndLabelsByModel: Seq[Map[Int, (Double, Boolean)]] = Seq(
        Map(
          modelA ->(0.1, true),
          modelB ->(0.8, true)
        ),
        Map(
          modelA ->(0.0, false),
          modelB ->(0.6, false)
        ),
        Map(
          modelA ->(0.05, false),
          modelB ->(0.65, true)
        ),
        Map(
          modelA ->(0.1, true),
          modelB ->(0.6, false)
        ),
        Map(
          modelA ->(0.1, false),
          modelB ->(0.3, false)
        ),
        Map(
          modelA ->(0.4, true),
          modelB ->(0.7, true)
        ),
        Map(
          modelA ->(0.3, true),
          modelB ->(0.3, false)
        )
      )

      // ModelA sorted
      // List((0.0,false), (0.05,false), (0.1,true), (0.1,true), (0.1,false), (0.3,true), (0.4,true))
      // ModelB sorted
      // List((0.3,false), (0.3,false), (0.6,false), (0.6,false), (0.65,true), (0.7,true), (0.8,true))

      val totalCountA = BinaryLabelCount(numPositives = 4, numNegatives = 3)
      val totalCountB = BinaryLabelCount(numPositives = 3, numNegatives = 4)

      val confusions: List[(Int, List[BinaryConfusionMatrix])] =
        sc.makeRDD(scoreAndLabelsByModel, numSlices = 2).confusionsByModel(bins = Some(4)).collect().toList
        .map(p => (p._1, p._2.toList.sortBy(_.predictedPositives).reverse))

      confusions must_=== List(
        modelA -> List(
          BinaryConfusionMatrix(totalCountA, totalCountA),
          BinaryConfusionMatrix(BinaryLabelCount(numPositives = 4, numNegatives = 1), totalCountA),
          BinaryConfusionMatrix(BinaryLabelCount(numPositives = 2, numNegatives = 1), totalCountA),
          BinaryConfusionMatrix(BinaryLabelCount(numPositives = 1, numNegatives = 0), totalCountA)
        ),
        modelB -> List(
          BinaryConfusionMatrix(totalCountB, totalCountB),
          BinaryConfusionMatrix(BinaryLabelCount(numPositives = 3, numNegatives = 2), totalCountB),
          BinaryConfusionMatrix(BinaryLabelCount(numPositives = 3, numNegatives = 0), totalCountB),
          BinaryConfusionMatrix(BinaryLabelCount(numPositives = 1, numNegatives = 0), totalCountB)
        )
      )
    }

    // TODO Edge cases:
    // missing model label
    // partitions greater than number of data points
    // partitions greater than number of bins

    // TODO Unit test BinaryConfusionMatrix
  }
}
