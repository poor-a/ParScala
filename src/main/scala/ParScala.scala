import parscala._
import parscala.callgraph.CallGraphBuilder
import parscala.controlflow.{CFGraph, CFGPrinter}
import parscala.df.{UseDefinition, DFGraph}
import parscala.file.DirectoryTraverser
import parscala.tree.{Decl, Node}

import scala.collection.JavaConverters

import java.util.stream.{Stream, Collectors}
import java.io.{File,PrintWriter}
import java.nio.file._

case class Config (
  val method : Option[String],
  val showCfg : Boolean,
  val showCallGraph : Boolean,
  val showDataflowGraph : Boolean,
  val dotOutput : Option[String],
  val files : List[String],
  val directories : List[String],
  val classpath : Option[String],
  val showHelp : Boolean
)

object ParScala {
  private def dumpDot(path : String, g : dot.DotGraph) : Unit = {
    val p : Path = Paths.get(path)
    try {
      val out = new PrintWriter(Files.newBufferedWriter(p, StandardOpenOption.CREATE_NEW))
      out.print(g)
      out.flush()
      out.close()
    } catch {
        case _ : FileAlreadyExistsException =>
          println("The file \"" + p + "\" already exists!")
    }
  }

  private def expandPath(path : String) : String = 
    if (path startsWith "~")
      new File(System.getProperty("user.home"), path.tail).getPath()
    else
      path

  def main(args : Array[String]) : Unit = {
    val cli = new Cli
    cli.parse(args) match {
      case Left(err) => {
        println(err)
        cli.printHelp()
      }
      case Right(c) => {
        if (c.showHelp)
          cli.printHelp()
        else {
          if (c.files.isEmpty && c.directories.isEmpty)
            Console.err.println("No Scala source is given.")
          else {
            val existingFiles : List[String] = c.files filter {new File(_).exists()}
            val scalaSourceFilesInDirs : Stream[String] = c.directories.foldLeft(Stream.empty[String])((files, dir) => Stream.concat(DirectoryTraverser.getScalaSources(dir), files))
            val scalaSourceFiles = (JavaConverters.asScalaBuffer(scalaSourceFilesInDirs.collect(Collectors.toList[String])).toList) ++ existingFiles
            if (scalaSourceFiles.isEmpty) {
              Console.err.println("No Scala files are found.")
            } else {
              val pgraph : ProgramGraph = parscala.ParScala.analyse(scalaSourceFiles, c.classpath)
              println("decls: " + pgraph.declarations)
              val pkgs : List[parscala.tree.Package] = pgraph.packages
              println(pkgs.head.decls)
              val pDotGraph = pkgs.foldLeft(dot.DotGraph("", List(), List())){(g, pkg) => g + Decl.toDot(pkg) }
              MainWindow.showDotWithTitle(pDotGraph, "")
              if (c.showCallGraph) {
                MainWindow.showCallGraph(CallGraphBuilder.fullCallGraph(pgraph))
              }
              val classes : List[Defn.Class] = pgraph.packages flatMap (_.classes)
              println(s"classes (${classes.size}): ${classes.mkString(", ")}")
              val methods : List[Either Decl.Method Defn.Method] = classes flatMap (_.methods)
              println(s"methods: ${methods.mkString(", ")}")
              scalaz.std.option.cata(c.method)(
                  mName => {
                    val oMethod : Option[Method] = methods.find(_.symbol.fullName == mName)
                    oMethod match {
                      case Some(method) => {
                        println("found method")
                        if (c.showCfg || c.showDataflowGraph || !c.dotOutput.isEmpty) {
                          val bodyAndCfg : Option[(Node, CFGraph)] = for (body <- method.body) yield (body, CFGraph.fromExpression(body, pgraph))
                          scalaz.std.option.cata(bodyAndCfg)(
                          { case (body, cfg) => {
                              if (c.showCfg)
                                MainWindow.showDotWithTitle(CFGPrinter.formatGraph(cfg), "Control flow graph of %s".format(method.name))
                              if (c.showDataflowGraph) {
                                   val usedef : UseDefinition = UseDefinition.fromCFGraph(cfg)
                                   val dataflow : DFGraph = DFGraph(body, usedef)
                                   MainWindow.showDotWithTitle(Node.toDot(body).addEdges(dataflow.toDotEdges), 
                                                               "Data flow graph of %s".format(method.name))
                              }
                              if (!c.dotOutput.isEmpty)
                                dumpDot(c.dotOutput.get, CFGPrinter.formatGraph(cfg))
                            }
                          }
                          , { val what : String = 
                                if (c.showCfg || !c.dotOutput.isEmpty)
                                  "The body of %s is not available, could not generate the control flow graph.".format(method.name)
                                else if (c.showDataflowGraph)
                                  "The body of %s is not available, could not generate the data flow graph.".format(method.name)
                                else
                                  "The body of %s is not available."
                              Console.err.println(what)
                            }
                          )
                        }
                      }
                      case None =>
                        println("Method %s is not found.".format(mName))
                    }
                  }
                , Console.err.println("No method is specified. Try using -m")
                )
            }
          } 
        }
      }
    }
  }
}
