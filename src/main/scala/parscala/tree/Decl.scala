package parscala
package tree
package decl

import parscala.dot
import scala.meta

/**
 * Superclass of declarations and definitions.
 */
sealed abstract class Decl extends Statement with SymbolTree

object Decl {

case class Var(val l : DLabel, pats : List[meta.Pat], symbols : Set[Symbol], sugared : meta.Decl.Var) extends Decl {
  def label : DLabel = l

  override def toString : String = sugared.toString
}

case class Val(val l : DLabel, pats : List[meta.Pat], symbols : Set[Symbol], sugared : meta.Decl.Val) extends Decl {
  def label : DLabel = l

  override def toString : String = sugared.toString
}

case class Method(val l : DLabel, symbol : Symbol, name : meta.Term.Name, argss : List[List[meta.Term.Param]], sugared : meta.Decl.Def) extends Defn {
  def label : DLabel = l

  override def toString : String = symbol.toString
}

case class Type(l : DLabel, symbol : Symbol, name : meta.Type.Name, params : List[meta.Type.Param], bounds : meta.Type.Bounds, sugared : meta.Decl.Type) extends Decl {

  override def label : DLabel = l

  override def toString : String = sugared.toString
}

  def cata[A]( fVal : (DLabel, List[meta.Pat], Set[Symbol], meta.Decl.Val) => A
             , fVar : (DLabel, List[meta.Pat], Set[Symbol], meta.Decl.Var) => A
             , fMethod : (DLabel, Symbol, meta.Term.Name, List[List[meta.Term.Param]], meta.Decl.Def) => A
             , fType : (DLabel, Symbol, meta.Type.Name, List[meta.Type.Param], meta.Type.Bounds, meta.Decl.Type) => A
             , decl : Decl
             ) : A =
    decl match {
      case Var(l, pats, symbols, desugared) => fVar(l, pats, symbols, desugared)
      case Val(l, pats, symbols, desugared) => fVal(l, pats, symbols, desugared)
      case Method(l, sym, name, argss, desugared) => fMethod(l, sym, name, argss, desugared)
      case Type(l, sym, name, params, bounds, sugared) => fType(l, sym, name, params, bounds, sugared)
    }

  def kindCata[A]( val_ : Val => A
                 , var_ : Var => A
                 , method_ : Method => A
                 , type_ : Type => A
                 , d : Decl
                 ) : A =
    d match {
      v : Val => val_(v)
      v : Var => var_(v)
      m : Method => method_(m)
      t : Type => type_(t)
    }

  /** Converts a declaration into a graph.
   */
  def toDot(d : Decl) : dot.DotGraph = {
    val (_, tree) = toTree(d)
    dot.DotGraph("Program graph", List(), List()) + tree
  }

  /** Helper function of toDot for generating dot nodes and edges
   *  from a declaration.
   *
   *  @returns root and its children with edges of the tree
   */
  private def toTree(decl : Decl) : (dot.DotNode, dot.DotGraph) =
    cata( (l, pats, symbols, mRhs, desugared) => { // value
            val root : dot.DotNode = dot.DotNode(l.toString) !! dot.DotAttr.label(desugared.toString)
            (root, dot.DotGraph("", List(root), List()))
          }
        , (l, pats, symbols, mRhs, desugared) => { // variable
            val root : dot.DotNode = dot.DotNode(l.toString) !! dot.DotAttr.label(desugared.toString)
            (root, dot.DotGraph("", List(root), List()))
          }
        , (l, symbol, name, argss, body, desugared) => { // method
            val root : dot.DotNode = dot.DotNode(l.toString) !! dot.DotAttr.label(symbol.toString)
            (root, dot.DotGraph("", List(root), List()))
          }
        , (l, symbol, name, decls, desugared) => topLevel(l, symbol, decls) // class
        , (l, symbol, name, decls, desugared) => topLevel(l, symbol, decls) // object
        , (l, symbol, name, decls, desugared) => topLevel(l, symbol, decls) // package object
        , (l, symbol, name, decls, desugared) => topLevel(l, symbol, decls) // package
        , decl
        )

  /** Helper function for top-level declarations,
   *  such as class, object, package object and package
   */
  private def topLevel(l : DLabel, symbol : Symbol, decls : List[Decl]) : (dot.DotNode, dot.DotGraph) = {
      val (children, subtrees) : (List[dot.DotNode], List[dot.DotGraph]) = decls.map(toTree).unzip
      val subtree : dot.DotGraph = subtrees.foldLeft(dot.DotGraph.empty(""))(_ + _)
      val current = dot.DotNode(l.toString) !! dot.DotAttr.label(symbol.toString)
      val tree : dot.DotGraph = dot.DotGraph("", current :: children, children.map{child => dot.DotEdge(current, child)}) + subtree
      (current, tree)
    }

  def isClass(d : Decl) : Boolean = {
    val c5False : (Any, Any, Any, Any, Any) => Boolean = Function.const5(false)
    val c6False : (Any, Any, Any, Any, Any, Any) => Boolean = Function.const6(false)
    val c5True : (Any, Any, Any, Any, Any) => Boolean = Function.const5(true)
    Decl.cata(
        c5False // value
      , c5False // variable
      , c6False // method
      , c5True  // class
      , c5False // object
      , c5False // package object
      , c5False // package
      , d
      )
  }

  def isMethod(d : Decl) : Boolean = {
    val c5False : (Any, Any, Any, Any, Any) => Boolean = Function.const5(false)
    val c6True : (Any, Any, Any, Any, Any, Any) => Boolean = Function.const6(true)
    Decl.cata(
        c5False
      , c5False
      , c6True
      , c5False
      , c5False
      , c5False
      , c5False
      , d
      )
  }

  def asClass(d : Decl) : Option[Class] = {
    val c5None : (Any, Any, Any, Any, Any) => Option[Class] = Function.const5(None)
    val c6None : (Any, Any, Any, Any, Any, Any) => Option[Class] = Function.const6(None)
    Decl.cata(
        c5None // value
      , c5None // variable
      , c6None // method
      , (_, _, _, _, _) => Some(d.asInstanceOf[Class]) // class 
      , c5None // object
      , c5None // package object
      , c5None // package
      , d
      )
  }

  def asMethod(d : Decl) : Option[Method] = {
    val c5None : (Any, Any, Any, Any, Any) => Option[Method] = Function.const5(None)
    Decl.cata(
        c5None // value
      , c5None // variable
      , (_, _, _, _, _, _) => Some(d.asInstanceOf[Method]) // method
      , c5None // class 
      , c5None // object
      , c5None // package object
      , c5None // package
      , d
      )
  }
}
