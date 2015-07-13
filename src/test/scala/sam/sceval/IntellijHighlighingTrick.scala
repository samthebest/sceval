package sam.sceval

import org.scalacheck.Prop
import org.specs2.ScalaCheck
import org.specs2.matcher.MatchResult
import org.specs2.mutable.Specification

trait IntellijHighlighingTrick extends Specification with ScalaCheck {
  // Trick to make Intellij highlight correctly
  implicit def toProp(m: MatchResult[Any]): Prop = resultProp(m)
}
