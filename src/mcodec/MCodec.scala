package mcodec

// Phase-1 stub; real streaming contract arrives in Phase 2.
trait MCodec[T]:
  def encode(value: T): String
  def decode(repr: String): T

object MCodec:
  def stub[T](enc: T => String, dec: String => T): MCodec[T] = new MCodec[T]:
    def encode(value: T): String = enc(value)
    def decode(repr: String): T  = dec(repr)
