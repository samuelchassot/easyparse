// EasyParse libary
// (c) 2022 Gidon Ernst <gidonernst@gmail.com>
// This code is licensed under MIT license (see LICENSE for details)

import scala.language.implicitConversions
import scala.util.DynamicVariable

package object easyparse {
  type ~[+A, +B] = Tuple2[A, B]
  val ~ = Tuple2

  type Input[T] = Seq[T]
  type Result[+A, T] = (A, Input[T])
  type Scope[K,V] = Parser.Scope[K,V]

  case class Error(msg: String, in: Input[_], trace: List[String] = Nil) extends Exception {
    override def toString = msg + "\nat '" + (in take 32).mkString(" ") + " ...'\n" + "from grammar rules " + trace.mkString(" -> ")
  }

  def fail(msg: String, in: Input[_], cm: Boolean, cause: Throwable = null) = {
    if (cm) {
      throw Error(msg, in).initCause(cause)
    } else {
      backtrack(msg)
    }
  }

  def ret[A, T](a: A) =
    new Parser.Accept[A, T](a)
  def tok[T] =
    new Parser.Shift[T]()
  def just[A, T](p: Parser[A, T]) =
    new Scanner.Just[A, T](p)

  def map[T, A](f: T => A) =
    tok map f
  def collect[T, A](f: PartialFunction[T, A]) =
    tok collect f

  def KW(name: String) =
    new Scanner.Keyword(name)
  def KW(implicit name: sourcecode.Name) =
    new Scanner.Keyword(name.value)

  def S[K, V](init: Map[K, V] = Map.empty) =
    new Parser.Scope[K, V](init)
  def S[A, B, T](p: Parser[A, T], v: DynamicVariable[B]) =
    new Parser.Scoped[A, B, T](p, v)

  def V[A](name: String) =
    new Parser.Value[A](name)
  def V[A](implicit name: sourcecode.Name) =
    new Parser.Value[A](name.value)

  def P[A, T](
      p: => Parser[A, T]
  )(implicit name: sourcecode.Name): Parser[A, T] = {
    new Parser.Recursive(name.value, () => p)
  }

  def M[Op, Expr, T](
      p: => Parser[Expr, T],
      op: Parser[Op, T],
      ap: (Op, List[Expr]) => Expr,
      s: Syntax[Op, T],
      min: Int = Int.MinValue,
      max: Int = Int.MaxValue
  )(implicit name: sourcecode.Name) = {
    Mixfix[Op, Expr, T](
      name.value,
      () => p,
      ap,
      s prefix_op op,
      s postfix_op op,
      s infix_op op,
      min,
      max
    )
  }

  trait NoStackTrace {
    this: Throwable =>
    override def fillInStackTrace = this
    override val getStackTrace = Array[StackTraceElement]()
  }

  def backtrack(message: String) = {
    throw Backtrack(message)
  }

  case class Backtrack(message: String)
      extends Throwable /* with NoStackTrace */ {
    override def toString = message
  }

  implicit class Control[A](first: => A) {
    def or[B <: A](second: => B) = {
      try {
        first
      } catch {
        case _: Backtrack =>
          second
      }
    }
  }

  // def L[T](tokens: T*) = tok[T] filter tokens.contains

  /* def int = S("[+-]?[0-9]+") map {
    str => str.toInt
  }

  def bigint = S("[+-]?[0-9]+") map {
    str => BigInt(str)
  }

  def double = S("[+-]?[0-9]+[.]?[0-9]*") map {
    str => str.toDouble
  }

  def char = S("\'([^\']|\\')\'") map {
    str =>
      str.substring(1, str.length - 1)
  }

  def string = S("\"[^\"]*\"") map {
    str =>
      str.substring(1, str.length - 1)
  }

  def S(pattern: String)(implicit name: sourcecode.Name): Parser[String] = {
    new Regex(name.value, pattern)
  } */
}
