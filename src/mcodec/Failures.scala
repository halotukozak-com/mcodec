package mcodec

class ReadFailure(msg: String, cause: Throwable | Null) extends RuntimeException(msg, cause):
  def this(msg: String) = this(msg, null)

class WriteFailure(msg: String, cause: Throwable | Null) extends RuntimeException(msg, cause):
  def this(msg: String) = this(msg, null)
