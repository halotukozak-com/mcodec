package mcodec

/**
 * Manual codecs for BSON-native types with no natural JSON/CBOR representation. Each detects
 * whether it is running against the BSON backend (via the `BsonExtOutput`/`BsonExtInput`
 * opportunistic capability the BSON value-position reader/writer implements) and uses the
 * native BSON type tag there; on every other backend it falls back to a plain string (or, for
 * `BsonRegex`, a two-field object) encoding.
 */
trait BsonCodecs:
  this: MCodec.type =>

  given MCodec[ObjectId] = create(
    {
      case b: BsonExtInput => ObjectId(b.readObjectId())
      case in => ObjectId.fromHex(MCodec.read[String](in))
    },
    {
      case (b: BsonExtOutput, v) => b.writeObjectId(v.bytes)
      case (out, v) => MCodec.write(out, v.toHexString)
    },
  )

  given MCodec[BsonRegex] = create(
    {
      case b: BsonExtInput =>
        val (pattern, options) = b.readRegexValue()
        BsonRegex(pattern, options)
      case in =>
        val oi = in.readObject()
        var pattern: String | Null = null
        var options: String | Null = null
        while oi.hasNext do
          val f = oi.nextField()
          f.fieldName match
            case "pattern" => pattern = MCodec.read[String](f)
            case "options" => options = MCodec.read[String](f)
            case _ => f.skip()
        (pattern, options) match
          case (p: String, o: String) => BsonRegex(p, o)
          case _ => throw ReadFailure("BsonRegex requires both 'pattern' and 'options' fields")
    },
    {
      case (b: BsonExtOutput, v) => b.writeRegexValue(v.pattern, v.options)
      case (out, v) =>
        val oo = out.writeObject()
        MCodec.write(oo.writeField("pattern"), v.pattern)
        MCodec.write(oo.writeField("options"), v.options)
        oo.finish()
    },
  )

  given MCodec[JsCode] = create(
    {
      case b: BsonExtInput => JsCode(b.readJsCode())
      case in => JsCode(MCodec.read[String](in))
    },
    {
      case (b: BsonExtOutput, v) => b.writeJsCode(v.code)
      case (out, v) => MCodec.write(out, v.code)
    },
  )

  given MCodec[MinKey.type] = create(
    {
      case b: BsonExtInput =>
        b.readMinKey()
        MinKey
      case in =>
        if MCodec.read[String](in) != "MinKey" then throw ReadFailure("expected \"MinKey\"")
        MinKey
    },
    {
      case (b: BsonExtOutput, _) => b.writeMinKey()
      case (out, _) => MCodec.write(out, "MinKey")
    },
  )

  given MCodec[MaxKey.type] = create(
    {
      case b: BsonExtInput =>
        b.readMaxKey()
        MaxKey
      case in =>
        if MCodec.read[String](in) != "MaxKey" then throw ReadFailure("expected \"MaxKey\"")
        MaxKey
    },
    {
      case (b: BsonExtOutput, _) => b.writeMaxKey()
      case (out, _) => MCodec.write(out, "MaxKey")
    },
  )
