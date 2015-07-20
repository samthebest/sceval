package sam.sceval

import org.scalacheck.Prop
import org.specs2.ScalaCheck
import org.specs2.matcher.MatchResult
import org.specs2.mutable.Specification

// Trick to make Intellij highlight correctly, doesn't change behaviour, just reduces scope for implicit search.
trait IntellijHighlighingTrick extends Specification with ScalaCheck {
  implicit def toProp(m: MatchResult[Any]): Prop = resultProp(m)
}
