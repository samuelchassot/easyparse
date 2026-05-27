EasyParse Library
=================

LL parsing for case classes made simple.

Author: Gidon Ernst <gidonernst@gmail.com>

Feedback is welcome! I'd appreciate to hear whether anyone found this library useful.

Motivation & Overview
---------------------

Scala's case classes are a fine way to represent syntax trees. For example,
consider a simple Lisp-like language for expressions with identifiers, numbers
and function application:

    trait Expr
    case class Id(name: String) extends Expr
    case class App(args: List[Expr]) extends Expr

This definition corresponds (almost) directly to the following attributed grammar:

    expr := id | app
    id   := string        { Id(_)  }
    app  := "(" expr* ")" { App(_) }

The aim of this library is to minimize the fuss to create a parser for such a
language. The main idea is that, given a parser for the arguments of a case
class constructor, the parser for the case class is instantiated automatically.
With *arse* you can specify this as follows:

    val expr: Parser[Expr] = P(app | id)
    val id = Id(name)
    val app = App("(" ~ (expr *) ~ ")")

This example demonstrates a few features of the library:

-   Sequential composition `p ~ q`, which is strict in `q` if `p` succeeds
-   Ordered choice `p | q`, which backtracks if `p` fails and tries `q`
-   Recursive parsers through `P(p)`, which evaluates `p` lazily
-   Implicit lifting of functions to parsers, where `f(p1 ~ ... ~ pn)` parses `p1 ~ ... ~ pn` and applies `f(a1, ..., an)` to the results `a1, ..., an`

Installation
------------

Dependencies

- Scala 3.8.3
- Mill 1.1.6
- [sourcecode](https://github.com/lihaoyi/sourcecode) 0.4.4

Compile & Install

    mill easyparse.jar

Use
---

Parsers `p: Parser[A]` parse a piece of text and return a result `a: A`.

### Using parsers

-   `p.parse(in)` parse `p` on `in: String` or fail with an error
-   `p.parse(in, cm=false)` try to parse `p` and fail with `backtrack()`
    (do not commit, internally used for backtracking, see also [bk](https://github.com/gernst/bk))
-   `p.parseAt(in, pos, cm)` parse `in` at position `pos`

### Constructing parsers

-   `int`, `double`, `char`, `string`: predefined scanners for numbers, character literals `'x'` and strings `"..."` (support escaping `\'` resp `\"`)
-   `val n: Parser[T] = P(p)` declares a recursive parser with name `n` that defers evaluation of `p` to allow forward declaration of `p`;
     works in class/object scope when `p` refers to fields, but not when `p` refers to local variables defined later
-   `ret(a)` succeeds without consuming input and returns `a`
-   `p ~ q` parses `p` and returns a pair `(a,b)` of the results
-   `p ~> q` and `p <~ q` are variants of sequential composition that discard the right resp. left result
-   `p | q` tries `p` and if it fails parses `q`
-   `"lit" ~ p` matches a literal "lit", parses `p` and returns it's result, similarly `p ~ "lit"`
-   `p $` parses `p` but causes an error if there is remaining input after `p`
-   `p ?`, `p *`, `p +`: optional and sequence parsers, returning `Option[_]` resp. `List[_]`
-   `p ~* q` and `p ~+ q`: parse `p ~ q ~ ... ~ q ~ p` and return the results of `p` as `List[_]`; the latter requires at least one `p` result
-   `p.filter(f)`, `p.filterNot(f)`, `p.map(f)`: filter/map a result by a predicate/function `f`
-   `p.reduceLeft(f)`, `p.reduceRight(f)`: parse `p+` and reduce the result with `f`
-   `p.foldLeft(z)(f)`, `p.foldRight(z)(f)`, parse `z ~ p+` resp. `p+ ~ z` and fold the result with `f`

### Strictness

Beware that this library takes a nuanced view on backtracking:
Ordered choice `p | q` permits `p` to fail and tries `q` *only* if `p` has 1) not consumed input and 2) not produced a result (which includes `ret(a)`).
Similarly, `p*` returns an error when `p` can be parsed partially.

This leads to somewhat reasonable automatic error reporting and it is usually the behavior that one needs.
However, it means that you cannot backtrack over arbitrary complex pieces of syntax, which can sometimes be a nuisance.
For example, `(int ~ "a") | (int ~ "b")` fails on the input `"0b"` because it commits to the first choice after successfully parsing `"0"` with `int`.


### Mixfix parsing

-   `M(p, op, ap, s)` parses mixfix expressions

    - `p` is used to parse inner expressions
    - `op`: parse an operator such as "+"
    - `ap`: apply subexpressions as arguments to an operator previously returned by `op`
    - `s: Syntax`: a database of mixfix operators

`trait Syntax` is parametrized by

    def prefix_ops: Map[Op, Int]
    def postfix_ops: Map[Op, Int]
    def infix_ops: Map[Op, (Assoc, Int)]

which classify an operator returned by `op` into prefix, postfix, and infix
and return the respective precedence.
If you want a dynamic set of mixfix operators, just implement these as `var`s
in your `Syntax` object, no need to re-create the `M(...)` parser everytime you change the set of operators.

Note that it is possible to overload operators in the different categories,
the parsing algorithm can discern e.g. between unary and binary `-` (minus).
Infix takes precedence over postfix,
i.e., parsing repetition `*` will precede binary multiplication
*only* if the postfix priority of `*` is high enough to prohibit a right
argument (at the given source location).

See also:

- <http://javascript.crockford.com/tdop/tdop.html>
- <http://www.engr.mun.ca/~theo/Misc/exp_parsing.htm>
- <http://www.cs.nott.ac.uk/~pszgmh/monparsing.pdf> (about the Parser Monad)
- Project [ulang](https://github.com/gernst/ulang) for a larger example
  (`source/Parser.scala` in branch *master*
   resp. `source/Grammar.scala` in *refactor-parser*)

Limitations
-----------

The library is probably slow and would suffer from backtracking on ambiguous grammars.

The [combinator](https://github.com/scala/scala-parser-combinators)
library (no longer) shipped with Scala is much more feature complete and supports
left recursion by packrat parsing.

The [fastparse](http://www.lihaoyi.com/fastparse)
library for Scala provides a similar interface, but has many additional (cool!) features
and it is designed for speed.

The [parseback](https://github.com/djspiewak/parseback)
handles *all* context free grammars through
[parsing with derivatives](http://matt.might.net/articles/parsing-with-derivatives/).
Give this library a try if you need a more flexible grammar
or run into limitations of `arse`s naive backtracking.

If you need guaranteed linear runtime complexity, try a LALR parser generator instead, 
such as [beaver](http://beaver.sourceforge.net).

TODOs/Wishlist
--------------

- line and position tracking
- default implementation of syntax trees that store positioning information
- mixfix pretty printer
