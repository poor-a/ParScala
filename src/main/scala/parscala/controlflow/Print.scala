package parscala
package controlflow

import parscala.dot._

object CFGPrinter {
  private def topoSort(graph : CFGraph) : List[BLabel] = {
    def sort(from : BLabel, marked : Set[BLabel], sorted : List[BLabel]) : (Set[BLabel],List[BLabel]) = {
      if (marked(from)) {
          (marked, sorted)
      } else {
        if (from == graph.done.entryLabel) {
          (marked + from, from :: sorted)
        } else {
          graph.get(from) map (_.successors) match {
            case Some(succs : List[(BLabel, EdgeLabel.TagType)]) => 
              succs.foldLeft((marked + from, from :: sorted)){(acc, succ) =>
                val (l, _) = succ
                val (m, s) = acc
                sort(l, m, s)
              }
            case None => (marked, sorted)
          }
        }
      }
    }

    sort(graph.start.entryLabel, Set(), List())._2.reverse
  }

  private def formatLabels(graph : CFGraph, labels : List[BLabel]) : (List[DotNode], List[DotEdge]) = {
    def formatLabel(l : BLabel) : Option[(DotNode, List[DotEdge])] = {
      if (l == graph.start.entryLabel)
          graph.get(l) match {
            case Some(b) => {
              val edges : List[DotEdge] = for ((succ, _) <- b.successors)
                                         yield edge(node(l), node(succ))
              Some((DotNode(l.toString) !! DotAttr.label("Start"), edges))
            }
            case None =>
              None
          }
      else if (l == graph.done.entryLabel)
          if (!graph.get(l).isEmpty)
            Some((DotNode(l.toString) !! DotAttr.label("Done"), List.empty))
          else
            None
      else {
          def contents(block : Block[Node,_,_]) : List[String] = block match {
            case BFirst(node) => List(formatNode(node, graph.pgraph.expressions))
            case BMiddle(node) => List(formatNode(node, graph.pgraph.expressions))
            case BLast(node) => List(formatNode(node, graph.pgraph.expressions))
            case BCat(node1, node2) => contents(node1) ++ contents(node2)
          }
          
          graph.get(l) match {
            case Some(b : Block[Node,C,C]) => {
              val header :: stmts = contents(b)
              val content : String = "{ %s | %s }".format(header, stmts.mkString("\\l"))

              Some((DotNode(l.toString) !! DotAttr.shape(Shape.Record) !! DotAttr.labelWithPorts(content), formatEdges(b)))
            }
            case None => None
          }
        }
      }

    labels.foldLeft((List[DotNode](), List[DotEdge]())) {(acc, x) =>
      val (nodes, edges) = acc
      formatLabel(x) map (x => (x._1 :: nodes, x._2 ++ edges)) getOrElse acc
    }
  }

  private def formatEdges(block : Block[Node, C, C]) : List[DotEdge] = {
    val l : BLabel = block.entryLabel
    for (succ <- block.successors)
    yield {
      val (label, edgeLabel) = succ
      EdgeLabel.cata(
          edgeP(node(l), pTrue, node(label))   // T
        , edgeP(node(l), pFalse, node(label))  // F
        , edge(node(l), node(label))           // NoLabel
        , edgeLabel
      )
    }
  }

  private def formatNode(n : Node[_,_], nodes : ExprMap) : String = {
    def showExpr(expr : SLabel) : String = 
      "%3s: %s".format(expr, scalaz.std.option.cata(nodes.get(expr))(node => Dot.dotEscape(node.toString()), "##Err##"))

    def blockHeader(l : BLabel) : String =
      "Block " + l.toString

    Node.cata(
        blockHeader // label
      , (pat, _, _) => // pattern
          Dot.dotEscape(pat.toString)
      , showExpr // expr
      , (expr, _, _) => // call
          showExpr(expr)
      , (l, _, _) => // return
          blockHeader(l) + " (return)"
      , (_, _, _) => // cond
          " | {<%s> T | <%s> F}".format(pTrue, pFalse)
      , (_, _) => // branch
          ""
      , (_) => // jump
          ""
      , (_) => // done
          "(done)"
      , n
      )
  }

  /**
   * Creates the dot representation of a control flow graph,
   * so that it can be drawn on the screen.
   */
  def formatGraph(graph : CFGraph) : DotGraph = {
    val labels : List[BLabel] = topoSort(graph)
    val (nodes, edges) = formatLabels(graph, labels)
    DotGraph("CFG", nodes, edges)
  }

  private val pTrue : String = "p0"
  private val pFalse : String = "p1"

  private def node(l : BLabel) : DotNode = 
    DotNode(l.toString)
  private def edge(source : DotNode, target : DotNode) : DotEdge = 
    DotEdge(source, target)
  private def edgeP(source : DotNode, port : String, target : DotNode) : DotEdge = 
    edge(source, target) sourcePort port
}
