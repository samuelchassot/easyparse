// EasyParse libary
// (c) 2022 Gidon Ernst <gidonernst@gmail.com>
// This code is licensed under MIT license (see LICENSE for details)

package easyparse

import java.util.regex.Pattern

import implicits.ListParser
import implicits.Parser2
import scala.util.DynamicVariable
import scala.collection.IterableFactory
import scala.io.AnsiColor

trait Parser[+A, T] {
  p =>

  def parse(in: Input[T], cm: Boolean): Result[A, T]

  def parse(in: Input[T]): Result[A, T] = {
    parse(in, true)
  }

  def parseAll(in0: Input[T]): A = {
    val (a, in1) = p.parse(in0)
    if (in1 != Nil) fail("expected end of input", in1, true)
    else a
  }

  def $ =
    new Sequence.ParserScanner(p, Scanner.Eof(), strict = true)

  def ~[B](q: Parser[B, T]): Parser[A ~ B, T] =
    new Sequence.ParserParser(p, q, strict = true)
  def ~(q: Scanner[T]): Parser[A, T] =
    new Sequence.ParserScanner(p, q, strict = true)

  def ?~[B](q: Parser[B, T]): Parser[A ~ B, T] =
    new Sequence.ParserParser(p, q, strict = false)
  def ?~(q: Scanner[T]): Parser[A, T] =
    new Sequence.ParserScanner(p, q, strict = false)

  def ~@[B](q: A => Parser[B, T]): Parser[A ~ B, T] =
    new Sequence.ParserParserFun(p, q, strict = true)

  def ~>@[B](q: A => Parser[B, T]): Parser[B, T] = (p ~@ q)._2()

  def ::@[B >: A](q: A => Parser[List[B], T]): Parser[List[B], T] = (p ~@ q) map { case (a, as) =>
    a :: as
  }

  def <~[B](q: Parser[B, T]): Parser[A, T] = (p ~ q)._1()
  def ~>[B](q: Parser[B, T]): Parser[B, T] = (p ~ q)._2()
  def ?<~[B](q: Parser[B, T]): Parser[A, T] = (p ?~ q)._1()
  def ?~>[B](q: Parser[B, T]): Parser[B, T] = (p ?~ q)._2()

  def |[B >: A](q: Parser[B, T]): Parser[B, T] = Parser.Choice(p, q)
  def map[B](f: A => B): Parser[B, T] = Parser.Reduce(p, f, partial = false)
  def collect[B](f: PartialFunction[A, B]): Parser[B, T] =
    Parser.Reduce(p, f, partial = true)

  def ?(): Parser[Option[A], T] = Parser.Repeat(p, 0, 1) map {
    case List()  => None
    case List(a) => Some(a)
  }

  def *(): Parser[List[A], T] = Parser.Repeat(p, 0, Int.MaxValue)
  def +(): Parser[List[A], T] = Parser.Repeat(p, 1, Int.MaxValue)
  def ~*(sep: Scanner[T]): Parser[List[A], T] = p :: (sep ~ p).*() | ret(Nil)
  def ~+(sep: Scanner[T]): Parser[List[A], T] = p :: (sep ~ p).*()

  def filter(f: A => Boolean): Parser[A, T] = Parser.Filter(p, f)
  def filterNot(f: A => Boolean): Parser[A, T] =
    Parser.Filter(p, (a: A) => !f(a))

  def reduceLeft[B >: A](f: (B, A) => B): Parser[B, T] =
    p.+().map(_ reduceLeft f)
  def reduceRight[B >: A](f: (A, B) => B): Parser[B, T] =
    p.+().map(_ reduceRight f)
  def foldLeft[B](z: => Parser[B, T])(f: (B, A) => B): Parser[B, T] =
    (z ~ p.*()) map { case b ~ as => as.foldLeft(b)(f) }
  def foldRight[B](z: => Parser[B, T])(f: (A, B) => B): Parser[B, T] =
    (p.*() ~ z) map { case as ~ b => as.foldRight(b)(f) }

  def unfold[CC[+_]](factory: IterableFactory[CC], in: Input[T]): CC[A] = {
    val p = this.?()

    val b = factory.newBuilder[(A, Input[T])]

    factory.unfold(in) { in0 =>
      val (a, in1) = p parse in0
      a map { (_, in1) }
    }
  }

  def stream(in: Input[T]) = {
    unfold(LazyList, in)
  }

  def iterator(in: Input[T]): Iterator[A] = {
    unfold(Iterator, in)
  }
}

object Parser {
  case class Recursive[A, T](name: String, p: () => Parser[A, T]) extends Parser[A, T] {
    def parse(in0: Input[T], cm: Boolean) = {
      try {
        p() parse (in0, cm)
      } catch {
        case Error(msg, in1, trace) =>
          throw Error(msg, in1, name :: trace)
      }
    }

    override def toString = name
  }

  case class Fail[T](hard: Boolean) extends Parser[Nothing, T] {
    def parse(in: Input[T], cm: Boolean) = fail("unexpected input", in, cm || hard)
    override def toString = "fail"
  }

  case class Accept[+A, T](a: A) extends Parser[A, T] {
    def parse(in: Input[T], cm: Boolean) = (a, in)
    override def toString = "accept"
  }

  case class Value[A](name: String) extends Parser[A, Token] {
    case class Result(a: A) extends Token {
      override def toString = AnsiColor.UNDERLINED + a.toString + AnsiColor.RESET // ".Result(" + a + ")"
    }

    def apply(a: A) = Result(a)

    def parse(in: Input[Token], cm: Boolean) = {
      if (in.nonEmpty) {
        in.head match {
          case Result(a) =>
            (a, in.tail)
          case _ =>
            fail("expected value-token: '" + name + "'", in, cm)
        }
      } else {
        fail("unexpected end of input", in, cm)
      }
    }
  }

  case class Shift[T]() extends Parser[T, T] {
    def parse(in: Input[T], cm: Boolean) = {
      if (in.nonEmpty) {
        (in.head, in.tail)
      } else {
        fail("unexpected end of input", in, cm)
      }
    }

    override def toString = "token"
  }

  case class Choice[+A, T](p: Parser[A, T], q: Parser[A, T]) extends Parser[A, T] {
    def parse(in: Input[T], cm: Boolean) = {
      {
        p parse (in, false)
      } or {
        q parse (in, cm)
      }
    }

    override def toString = {
      "(" + p + " | " + q + ")"
    }
  }

  case class Repeat[+A, T](p: Parser[A, T], min: Int, max: Int) extends Parser[List[A], T] {
    def parse(in: Input[T], cm: Boolean): Result[List[A], T] = {
      parse(0, in, cm)
    }

    def parse(done: Int, in0: Input[T], cm: Boolean): Result[List[A], T] = {
      if (done < min) {
        val (a, in1) = p parse (in0, cm)
        val (as, in2) = this parse (done + 1, in1, cm)
        (a :: as, in2)
      } else if (done < max && in0.nonEmpty) {
        val (a, in1) = p parse (in0, false)
        val (as, in2) = this parse (done + 1, in1, cm)
        (a :: as, in2)
      } or {
        (Nil, in0)
      }
      else {
        (Nil, in0)
      }
    }

    override def toString = {
      if (min == 0 && max == 1) "(" + p + ") ?"
      else if (min == 0 && max == Int.MaxValue) "(" + p + ") *"
      else if (min == 1 && max == Int.MaxValue) "(" + p + ") +"
      else "(" + p + ") {" + min + "," + max + "}"
    }
  }

  case class Reduce[A, +B, T](p: Parser[A, T], f: A => B, partial: Boolean) extends Parser[B, T] {
    def parse(in0: Input[T], cm: Boolean) = f match {
      case f: PartialFunction[A, B] if partial =>
        val (a, in1) = p parse (in0, cm)
        val b = f.applyOrElse(
          a,
          fail("expected " + p + " (partial attribute)", in1, cm)
        )
        (b, in1)
      case _ =>
        val (a, in1) = p parse (in0, cm)
        val b = f(a)
        (b, in1)
    }

    override def toString = {
      p.toString
    }
  }

  case class Filter[A, T](p: Parser[A, T], f: A => Boolean) extends Parser[A, T] {
    def parse(in0: Input[T], cm: Boolean) = {
      val (a, in1) = p parse (in0, cm)
      if (!f(a)) fail("expected " + p + " (test failed)", in0, cm)
      else (a, in1)
    }

    override def toString = {
      p.toString
    }
  }

  class Scope[K, V](init: Map[K, V] = Map.empty[K, V]) extends DynamicVariable(init) with (K => V) {
    def apply(k: K) =
      value(k)

    def contains(k: K) =
      value contains k

    def +=(kv: (K, V)) =
      value = value + kv

    def update(k: K, v: V) =
      value = value + (k -> v)

    def within[A, T](p: Parser[A, T]) =
      Scoped(p, this)
  }

  case class Scoped[A, B, T](p: Parser[A, T], state: DynamicVariable[B]) extends Parser[A, T] {
    def parse(in0: Input[T], cm: Boolean) = {
      state.withValue(state.value) {
        p.parse(in0, cm)
      }
    }

    override def toString = {
      p.toString
    }
  }
}
