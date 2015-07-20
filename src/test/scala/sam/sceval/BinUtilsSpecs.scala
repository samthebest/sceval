package sam.sceval

import BinUtils._
import org.scalacheck.{Arbitrary, Gen}
import org.specs2.ScalaCheck
import org.specs2.matcher.Parameters
import org.specs2.mutable.Specification

class BinUtilsSpecs extends Specification with ScalaCheck {
  sequential

  implicit val params = Parameters(minTestsOk = 500)

  "Binner factory" should {
    "Return a binner that bins correctly" in {
      val (modelA, modelB) = (0, 1)

      val partitionLastIndexes: List[Map[Int, Int]] = List(
        Map(modelA -> 4, modelB -> 2),
        Map(modelA -> 0),
        Map(modelB -> 3),
        Map(modelA -> 2, modelB -> 2),
        Map(modelA -> 5, modelB -> 5),
        Map(modelA -> 4, modelB -> 4),
        Map(modelA -> 1, modelB -> 0)
      )

      val binner = binnerFac[Int](partitionLastIndexes.map(_.mapValues(_.toLong)).toArray, numRecodsPerBin = 2)

      (for {
        (modelAndIndexes, partition) <- partitionLastIndexes.map(_.mapValues(0 to).mapValues(_.toList)).zipWithIndex
        (model, indexes) <- modelAndIndexes.toList
        index <- indexes.toList
      } yield (model, (index, partition, binner(model, index, partition))))
      .groupBy(_._1).mapValues(_.map(_._2).sortBy(x => (x._1, x._2).swap)) must_=== Map(
        modelA -> List(
          (0, 0, 0), (1, 0, 0), (2, 0, 1), (3, 0, 1), (4, 0, 2),
          (0, 1, 2),
          (0, 3, 3), (1, 3, 3), (2, 3, 4),
          (0, 4, 4), (1, 4, 5), (2, 4, 5), (3, 4, 6), (4, 4, 6), (5, 4, 7),
          (0, 5, 7), (1, 5, 8), (2, 5, 8), (3, 5, 9), (4, 5, 9),
          (0, 6, 10), (1, 6, 10)
        ),
        modelB -> List(
          (0, 0, 0), (1, 0, 0), (2, 0, 1),
          (0, 2, 1), (1, 2, 2), (2, 2, 2), (3, 2, 3),
          (0, 3, 3), (1, 3, 4), (2, 3, 4),
          (0, 4, 5), (1, 4, 5), (2, 4, 6), (3, 4, 6), (4, 4, 7), (5, 4, 7),
          (0, 5, 8), (1, 5, 8), (2, 5, 9), (3, 5, 9), (4, 5, 10),
          (0, 6, 10)
        )
      )
    }

    implicit val _: Arbitrary[Int] = Arbitrary(Gen.choose(1, 20))

    "binner always evenly bins except for last bin, and each bin correct size" ! check(prop(
      (partitionLastIndexes: List[Map[Int, Int]], recordsPerBin: Int) => {
        val binner = binnerFac[Int](partitionLastIndexes.map(_.mapValues(_.toLong)).toArray, recordsPerBin)

        (for {
          (modelAndIndexes, partition) <- partitionLastIndexes.map(_.mapValues(0 to).mapValues(_.toList)).zipWithIndex
          (model, indexes) <- modelAndIndexes.toList
          index <- indexes.toList
        } yield (model, binner(model, index, partition)))
        .groupBy(_._1).forall(_._2.map(_._2).groupBy(identity).mapValues(_.size).toList.sortBy(-_._1) match {
          case (_, lastBinCount) :: rest =>
            val commonBinCounts = rest.map(_._2).distinct
            commonBinCounts.size <= 1 && (lastBinCount +: commonBinCounts).forall(_ <= recordsPerBin)
        }) must beTrue
      }))
  }

  "viable bin optimizer" should {
    def bruteForce(totalRecords: Int, desiredBins: Int): Int =
      (1 to math.max(totalRecords, desiredBins)).toList.minBy(recordsPerBin =>
        math.abs(resultingBinNumber(recordsPerBin, totalRecords) - desiredBins))

    implicit val _: Arbitrary[Int] = Arbitrary(Gen.choose(1, 500))

    "Always perform as well as brute force search" ! check(prop((totalRecods: Int, desiredBins: Int) => {
      optimizeRecordsPerBin(totalRecods, desiredBins) must_== bruteForce(totalRecods, desiredBins)
    }))

    def test(totalRecords: Int, desiredBins: Int, expectedBinSize: Int) = {
      s"select closest viable bin ($totalRecords, $desiredBins) == $expectedBinSize" in {
        optimizeRecordsPerBin(totalRecords, desiredBins) must_== expectedBinSize
      }
    }

    test(7, 1, 7)
    test(7, 2, 4)
    test(7, 3, 3)
    test(7, 4, 2)
    test(7, 5, 2)
    test(7, 6, 1)
    test(7, 7, 1)
    test(8, 1, 8)
    test(8, 2, 4)
    test(8, 3, 3)
    test(8, 4, 2)
    test(8, 5, 2)
    test(8, 6, 1)
    test(8, 7, 1)
    test(8, 8, 1)
  }
}
