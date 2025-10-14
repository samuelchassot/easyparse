// EasyParse libary
// (c) 2022 Gidon Ernst <gidonernst@gmail.com>
// This code is licensed under MIT license (see LICENSE for details)

package easyparse

import scala.io.AnsiColor

trait Token

trait Scanner[T] {
  p =>

  def scan(in: Input[T], cm: Boolean): Input[T]

  def |(that: Scanner[T]) =
    new Scanner.Choice(this, that)

  def ~[B](q: Parser[B, T]): Parser[B, T] =
    new Sequence.ScannerParser(p, q, strict = true)
  def ~(q: Scanner[T]): Scanner[T] =
    new Sequence.ScannerScanner(p, q, strict = true)

  def ?~[B](q: Parser[B, T]): Parser[B, T] =
    new Sequence.ScannerParser(p, q, strict = false)
  def ?~(q: Scanner[T]): Scanner[T] =
    new Sequence.ScannerScanner(p, q, strict = false)
}

object Scanner {
  case class Eof[T]() extends Scanner[T] {
    def scan(in: Input[T], cm: Boolean) = if(in.isEmpty) {
      in
    } else {
      fail("unexpected input", in, cm)
    }

    override def toString = "$"
  }

  case class Keyword(name: String) extends Token with Scanner[Token] {
    override def toString = name

    def scan(in: Input[Token], cm: Boolean) = {
      if (in.nonEmpty) {
        val head = in.head
        if (head == this)
          in.tail
        else
          fail("expected keyword: '" + name + "'", in, cm)
      } else {
        fail("unexpected end of input", in, cm)
      }
    }
  }

  case class Just[A,T](p: Parser[A,T]) extends Scanner[T] {
    def scan(in0: Input[T], cm: Boolean) = {
      val (_, in1) = p.parse(in0, cm)
      in1
    }
  }

  case class Choice[T](p: Scanner[T], q: Scanner[T]) extends Scanner[T] {
    def scan(in: Input[T], cm: Boolean) = {
      {
        p scan (in, false)
      } or {
        q scan (in, cm)
      }
    }

    override def toString = {
      "(" + p + " | " + q + ")"
    }
  }
}
