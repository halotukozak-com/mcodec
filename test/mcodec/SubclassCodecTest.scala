package mcodec

object SubclassCodecFixtures:
  sealed trait Animal derives MCodec
  case class Dog(name: String) extends Animal
  case class Cat(name: String) extends Animal

  val dogViaAnimal: MCodec[Dog] =
    MCodec.subclassCodec[Dog, Animal](
      narrow = {
        case d: Dog => Some(d)
        case _ => None
      },
      widen = identity,
    )(using MCodec.derived[Animal])

  // ClassTag variant (GenCodec parity: SubclassCodec[T: ClassTag, S >: T]) — no narrow/widen.
  given MCodec[Animal] = MCodec.derived[Animal]
  val dogAuto: MCodec[Dog] = MCodec.subclassCodec[Dog, Animal]

class SubclassCodecTest extends munit.FunSuite, JsonConv:
  import SubclassCodecFixtures.*

  test("subclass codec round-trips through parent shape"):
    val d = Dog("Rex")
    assertEquals(fromJson[Dog](d.toJson[Dog](using dogViaAnimal))(using dogViaAnimal), d)

  test("narrow failure raises ReadFailure not ClassCastException"):
    val catJson = (Cat("Mia"): Animal).toJson[Animal](using MCodec.derived[Animal])
    intercept[ReadFailure](fromJson[Dog](catJson)(using dogViaAnimal))

  test("ClassTag variant round-trips with no narrow/widen functions"):
    val d = Dog("Rex")
    assertEquals(fromJson[Dog](d.toJson[Dog](using dogAuto))(using dogAuto), d)

  test("ClassTag variant: wrong subclass raises ReadFailure not ClassCastException"):
    val catJson = (Cat("Mia"): Animal).toJson[Animal](using MCodec.derived[Animal])
    intercept[ReadFailure](fromJson[Dog](catJson)(using dogAuto))
