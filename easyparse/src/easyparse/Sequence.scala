// EasyParse libary
// (c) 2022 Gidon Ernst <gidonernst@gmail.com>
// This code is licensed under MIT license (see LICENSE for details)

package easyparse

object Sequence {
  case class ParserParser[+A, +B, T](
      p: Parser[A, T],
      q: Parser[B, T],
      strict: Boolean
  ) extends Parser[A ~ B, T] {
    def parse(in0: Input[T], cm: Boolean) = {
      val (a, in1) = p parse (in0, cm)
      val (b, in2) = q parse (in1, cm || strict)
      ((a, b), in2)
    }

    override def toString = {
      if (strict) "(" + p + " ~ " + q + ")"
      else "(" + p + " ~? " + q + ")"
    }
  }

  case class ParserParserFun[A, +B, T](
      p: Parser[A, T],
      q: A => Parser[B, T],
      strict: Boolean
  ) extends Parser[A ~ B, T] {
    def parse(in0: Input[T], cm: Boolean) = {
      val (a, in1) = p parse (in0, cm)
      val (b, in2) = q(a) parse (in1, strict)
      ((a, b), in2)
    }

    override def toString = {
      if (strict) "(" + p + " ~ " + q + ")"
      else "(" + p + " ~? " + q + ")"
    }
  }

  case class ScannerParser[+B, T](
      p: Scanner[T],
      q: Parser[B, T],
      strict: Boolean
  ) extends Parser[B, T] {
    def parse(in0: Input[T], cm: Boolean) = {
      val in1 = p scan (in0, cm)
      val (b, in2) = q parse (in1, strict)
      (b, in2)
    }

    override def toString = {
      if (strict) "(" + p + " ~ " + q + ")"
      else "(" + p + " ~? " + q + ")"
    }
  }

  case class ParserScanner[+A, T](
      p: Parser[A, T],
      q: Scanner[T],
      strict: Boolean
  ) extends Parser[A, T] {
    def parse(in0: Input[T], cm: Boolean) = {
      val (a, in1) = p parse (in0, cm)
      val in2 = q scan (in1, strict)
      (a, in2)
    }

    override def toString = {
      if (strict) "(" + p + " ~ " + q + ")"
      else "(" + p + " ~? " + q + ")"
    }
  }

  case class ScannerScanner[T](
      p: Scanner[T],
      q: Scanner[T],
      strict: Boolean
  ) extends Scanner[T] {
    def scan(in0: Input[T], cm: Boolean) = {
      val in1 = p scan (in0, cm)
      val in2 = q scan (in1, strict)
      in2
    }

    override def toString = {
      if (strict) "(" + p + " ~ " + q + ")"
      else "(" + p + " ~? " + q + ")"
    }
  }
}
