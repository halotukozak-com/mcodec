package mcodec

import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.arbitrary

class DerivationTest extends RoundTrip:
  def backend = JsonBackend

  case class Person(name: String, age: Int) derives MCodec
  case class Account(owner: Person, balance: BigDecimal) derives MCodec

  given Arbitrary[Person] = Arbitrary(for
    n <- arbitrary[String]
    a <- arbitrary[Int]
  yield Person(n, a))

  given Arbitrary[Account] = Arbitrary(for
    p <- arbitrary[Person]
    b <- arbitrary[BigDecimal]
  yield Account(p, b))

  roundTrip[Person]("Person")
  roundTrip[Account]("Account (nested product)")
