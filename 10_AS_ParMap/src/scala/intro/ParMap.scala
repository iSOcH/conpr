package scala.intro
import java.util.concurrent.Executors
import java.util.concurrent.Callable
import java.util.concurrent.Future
import scala.annotation.tailrec
import java.util.concurrent.ExecutorService

object ParMap extends App {
  
  def slowFunction(i:Int):Int = {
    var c = 0
    while (c<100000000) {
      c = c+i
    }
    while (c>0) {
      c = c-i
    }
    while (c<100000000) {
      c = c+i
    }
    while (c>0) {
      c = c-i
    }
    while (c<100000000) {
      c = c+i
    }
    while (c>0) {
      c = c-i
    }
    i + c
  }
  
  def mapSeq[A,B] (l:List[A], f:A=>B):List[B] = l match {
    case Nil =>		Nil
    case x :: xs =>	f(x) :: mapSeq(xs, f)
  }
  
  def mapPar[A,B] (l:List[A], f:A=>B):List[B] = {
    val ex = Executors.newFixedThreadPool(8)
    val futures = mapSeq[A, Future[B]] (l, a => ex.submit(new Callable[B]() { def call:B = f(a)}))
    val results = mapSeq[Future[B],B] (futures, b => b.get())
    ex.shutdown()
    results
  }
  
  def filterSeq[A](l:List[A], p:A=>Boolean):List[A] = l match {
    case Nil =>				Nil
    case x::xs if p(x) =>	x :: filterSeq(xs, p)
    case x::xs =>			filterSeq(xs, p)
  }
  
  def filterPar[A](l:List[A], p:A=>Boolean):List[A] = {
    val ex = Executors.newFixedThreadPool(8)
    
    // futures enthält den Wert der Liste und das Future welches den Boolean liefert der aussagt, ob
    // der Wert durch den filter gekommen ist (true) oder nicht (false)
    val futures = mapSeq[A, (A, Future[Boolean])] (l, a => (a,ex.submit(new Callable[Boolean]() { def call:Boolean = p(a)})))
    
    def par(l: List[(A, Future[Boolean])]): List[A] = {
      l match {
        case Nil => Nil
        case (x, b) :: xs if (b.get) => x :: par(xs)
        case (x, b) :: xs => par(xs)
      }
    }
    
    val res = par(futures)
    ex.shutdown()
    res
  }
  
  def reduceSeq[A](l:List[A], r:(A,A)=>A):A = {
    l match {
      case Nil => sys.error("cannot reduce empty list")
      case a :: Nil => a
      case a :: as => r(a,reduceSeq(as,r))
    }
  }
  
  def reducePar[A](l:List[A], r:(A,A)=>A):A = {
    def inner[A](l:List[A], r:(A,A)=>A):List[A] = {
      val ex = Executors.newFixedThreadPool(8)
      def inner2[A](l:List[A], r:(A,A)=>A):List[Future[A]] = {
        l match {
          case Nil => Nil
          case x1 :: x2 :: xs => ex.submit(new Callable[A]() { def call:A = r(x1,x2) }) :: inner2(xs, r)
          case x1 :: xs => List(ex.submit(new Callable[A]() { def call:A = x1}))
        }
      }
      val res = inner2(l, r)
      ex.shutdown()
      mapSeq(res, (fut:Future[A]) => fut.get())
    }
    
//    // how to solve this without var?
//    var innerR = inner(l,r)
//    while (innerR.length != 1) {innerR = inner(innerR,r)}
//    innerR.head
    
    // ... with doReduce
    def doReduce(l:List[A]):List[A] = {
      l match {
        case xs if xs.length > 1 => doReduce(inner(xs, r))
        case xs => xs
      }
    }
    doReduce(l).head
  }
  
  val list = List.range(1, 2000, 1)
  println(list.map(slowFunction))
  println(mapSeq(list, (slowFunction)))
  println(mapPar(list, (slowFunction)))
  
  println(list.filter((x:Int) => x > 1250))
  println(filterSeq(list, (x:Int) => x > 1250))
  println(filterPar(list, (x:Int) => x > 1250))
  
  println(list.reduceRight((x:Int, y:Int) => x+y))
  println(reduceSeq(list, (x:Int, y:Int) => x+y))
  println(reducePar(list, (x:Int, y:Int) => x+y))
}