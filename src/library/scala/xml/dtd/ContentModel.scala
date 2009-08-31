/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2009, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id$


package scala.xml
package dtd

import scala.util.regexp.WordExp
import scala.util.automata.{DetWordAutom, SubsetConstruction, WordBerrySethi}
import scala.collection.mutable.HashSet
import Utility.sbToString
import PartialFunction._

object ContentModel extends WordExp {
  type _labelT = ElemName
  type _regexpT = RegExp

  object Translator extends WordBerrySethi {
    override val lang: ContentModel.this.type = ContentModel.this
  }

  case class ElemName(name: String) extends Label {
    override def toString() = "ElemName(\""+name+"\")"
  }

  def isMixed(cm: ContentModel) = cond(cm) { case _: MIXED => true }
  def containsText(cm: ContentModel) = (cm == PCDATA) || isMixed(cm)
  def parse(s: String): ContentModel = ContentModelParser.parse(s)

  def getLabels(r: RegExp): scala.collection.Set[String] = {
    val s = new HashSet[String]()

    def traverse(r: RegExp): Unit = r match { // !!! check for match translation problem
      case Letter(ElemName(name)) => s += name
      case Star(  x @ _  ) => traverse( x ) // bug if x@_*
      case Sequ( xs @ _* ) => xs foreach traverse
      case Alt(  xs @ _* ) => xs foreach traverse
    }
    traverse(r)
    s
  }

  def buildString(r: RegExp): String = sbToString(buildString(r, _))

  /* precond: rs.length >= 1 */
  private def buildString(rs: Seq[RegExp], sb: StringBuilder, sep: Char) {
    val it = rs.iterator
    val fst = it.next
    buildString(fst, sb)
    for (z <- it) {
      sb.append(sep)
      buildString(z, sb)
    }
    sb
  }

  def buildString(c: ContentModel, sb: StringBuilder): StringBuilder = c match {
    case ANY                    => sb append "ANY"
    case EMPTY                  => sb append "EMPTY"
    case PCDATA                 => sb append "(#PCDATA)"
    case ELEMENTS(_) | MIXED(_) => c buildString sb
  }

  def buildString(r: RegExp, sb:StringBuilder): StringBuilder = r match {  // !!! check for match translation problem
    case Eps =>
      sb
    case Sequ(rs @ _*) =>
      sb.append( '(' ); buildString(rs, sb, ','); sb.append( ')' )
    case Alt(rs @ _*) =>
      sb.append( '(' ); buildString(rs, sb, '|');  sb.append( ')' )
    case Star(r: RegExp) =>
      sb.append( '(' ); buildString(r, sb); sb.append( ")*" )
    case Letter(ElemName(name)) =>
      sb.append(name)
  }

}

sealed abstract class ContentModel
{
  override def toString(): String = sbToString(buildString)
  def buildString(sb: StringBuilder): StringBuilder
}

case object PCDATA extends ContentModel {
  override def buildString(sb: StringBuilder): StringBuilder = sb.append("(#PCDATA)")
}
case object EMPTY extends ContentModel {
  override def buildString(sb: StringBuilder): StringBuilder = sb.append("EMPTY")
}
case object ANY extends ContentModel {
  override def buildString(sb: StringBuilder): StringBuilder = sb.append("ANY")
}
sealed abstract class DFAContentModel extends ContentModel {
  import ContentModel.ElemName
  def r: ContentModel.RegExp
  private var _dfa: DetWordAutom[ContentModel.ElemName] = null

  def dfa = {
    if (null == _dfa) {
      val nfa = ContentModel.Translator.automatonFrom(r, 1);
      _dfa = new SubsetConstruction(nfa).determinize;
    }
    _dfa
  }
}
case class MIXED(r: ContentModel.RegExp) extends DFAContentModel {
  import ContentModel.{Alt, Eps, RegExp}
  /*
  def getIterator(ns:NodeSeq) = new Iterator[String] {
    def cond(n:Node) =
      !n.isInstanceOf[Text] && !n.isInstanceOf[SpecialNode];
Console.println("ns = "+ns);
    val jt = ns.iterator;
    def hasNext = jt.hasNext;
    def next = {
      var r: Node = jt.next;
      while(!cond(r) && jt.hasNext)  {
        Console.println("skipping "+r);
        r = jt.next;
      }
      Console.println("MIXED, iterator.next, r = "+r);
      if(Text("") == r)
        null
      else
        r.label
    }
  }
  */
  override def buildString(sb: StringBuilder): StringBuilder =  {
    sb.append("(#PCDATA|")
    ContentModel.buildString(Alt(r.asInstanceOf[Alt].rs.toList.drop(1):_*):RegExp, sb);
    sb.append(")*");
  }
}

case class  ELEMENTS(r:ContentModel.RegExp) extends DFAContentModel {
  /*
  def getIterator(ns:NodeSeq) = new Iterator[String] {
    val jt = ns.iterator.buffered;
    def hasNext = jt.hasNext;
    def next = {
      var r: Node = jt.next;
      while(r.isInstanceOf[SpecialNode] && jt.hasNext) {
        r = jt.head;
        jt.next;
      }
      Console.println("MIXED, iterator.next, r = "+r);
      if(r.isInstanceOf[Text])
        throw ValidationException("Text not allowed here!")
      else
        r.label
    }
  }
  */
  override def buildString(sb: StringBuilder): StringBuilder =
    ContentModel.buildString(r, sb)
}
