package sam.sceval

import org.specs2.mutable.Specification
import org.specs2.matcher.MatchResult

trait RoughlyUtil extends Specification {
  val epsilon = 0.00001

  implicit class RoughlyDouble(d: Double) {
    def ~=(other: Double): MatchResult[Double] = d must beCloseTo(other, epsilon)
  }
}
