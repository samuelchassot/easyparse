// EasyParse libary
// (c) 2022 Gidon Ernst <gidonernst@gmail.com>
// This code is licensed under MIT license (see LICENSE for details)

package easyparse

import scala.util.DynamicVariable

object Test {
  sealed trait Type
  case class Sort(name: String) extends Type

  case class Fun(name: String, args: List[Type], res: Type)

  sealed trait Quant
  case object Exists extends Quant
  case object Forall extends Quant

  sealed trait Expr
  case class Var(name: String, typ: Type) extends Expr
  case class App(fun: Fun, args: List[Expr]) extends Expr
  case class Bind(quant: Quant, bound: List[Var], body: Expr) extends Expr

  object App {
    val from: ((String, Option[List[Expr]]) => Expr) = {
      case (id, None) =>
        scope(id)
      case (id, Some(args)) =>
        App(sig(id), args)
    }
  }

  import implicits._

  def parens[A](p: Parser[A, Token]) = "(" ~ p ~ ")"

  object sig extends Scope[String, Fun] {}

  object scope extends Scope[String, Var] {
    val declare: ((String, Type) => Var) = { case (name, typ) =>
      val x = Var(name, typ)
      scope update (name, x)
      x
    }
  }

  val id = V[String]
  val typ = P(id.map(Sort(_)))

  val expr: Parser[Expr, Token] =
    P(parens(expr) | bind | app)

  val args = P(parens(expr ~* ","))
  val app = P((id ~ args.?()).map { case name ~ maybeArgs => App.from(name, maybeArgs) })

  val quant = P(Forall("forall") | Exists("exists"))
  val formal = P((id ~ ":" ~ typ).map { case name ~ tpe => scope.declare(name, tpe) })
  val formals = P(formal ~* ",")
  val bind = P(scope.within(quant ~ formals ~ "." ~ expr).map {
    case (q ~ vars) ~ body => Bind(q, vars, body)
  })

  def main(args: Array[String]): Unit = {
    val x = id.Result("x")
    val y = id.Result("y")
    val int = id.Result("int")

    val in = List[Token](
      "forall",
      x,
      ":",
      int,
      ".",
      x
    )

    val e = expr.parseAll(in)
    println(e)
  }
}
