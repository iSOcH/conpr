package _09_scala_intro
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit
import scala.annotation.tailrec

object EXParMap_Solution {
  /* Sequentielle map Methode. */
  def map[A, B](l: List[A], f: A => B): List[B] = l match {
    case Nil => Nil
    case x :: xs => f(x) :: map(xs, f)
  }

  /* Parallele map Methode. */
  def parmap[A, B](l: List[A], f: A => B): List[B] = {
    val ex = Executors.newFixedThreadPool(4)
    val futures = map[A, Future[B]](l, a => ex.submit(new Callable[B]() { def call = f(a) }))
    val result = map[Future[B], B](futures, f => f.get)
    ex.shutdown()
    result
  }

  /* Sequentielle filter Methode. */
  def filter[A, B](l: List[A], f: A => Boolean): List[A] = {
    l match {
      case Nil => Nil
      case x :: xs => if (f(x)) x :: filter(xs, f) else filter(xs, f)
    }
  }

  /* Parallele filter Methode. */
  def parfilter[A](l: List[A], f: A => Boolean): List[A] = {
    val ex = Executors.newFixedThreadPool(4)
    val futures = map[A, (A, Future[Boolean])](l, a => (a, ex.submit(new Callable[Boolean]() { def call = f(a) })))
    def par(l: List[(A, Future[Boolean])]): List[A] = {
      l match {
        case Nil => Nil
        case (x, b) :: xs => if (b.get) x :: par(xs) else par(xs)
      }
    }
    val result = par(futures)
    ex.shutdown()
    result
  }

  /* Sequentielle reduce (right) Methode. */
  def reduce[A](as: List[A], r: (A, A) => A): A = {
    as match {
      case Nil => sys.error("reduce(Nil,r)")
      case a :: Nil => a
      case a :: as => r(a, reduce(as, r))
    }
  }

  /* Parallele reduce Methode.  ;-) */
  def parreduce0[A](as: List[A], r: (A, A) => A): A = as.par.reduce(r)
  

  /* Bottom up parallele reduce Methode. 
   *          
   *Level:2        R
   *              /r\
   *Level:1      A   B
   *            /r\ /r\
   *Level:0    C  D E  F
   *
   * Die Methode reduceLayer reduziert eine Liste auf Level n in eine neue Liste auf Level n+1.
   * Die Methode repeatReduce wiederholt reduceLayer bis nur noch ein Resultat übrig ist.
   * 
   * Bei top-down Lösungen muss man vorsichtig sein, dass nicht Threads auf höheren Levels auf
   * Resultate aus tieferen Levels warten. 
   * 
   * Die übergebene Funktion r muss assoziativ sein: ((A r B) r C) == (A r (B r C))
   */
  def parreduce[A](as: List[A], r: (A, A) => A): A = {
    val ex = Executors.newFixedThreadPool(4)
    val prep: List[Future[A]] = as.map(a => new FinishedFuture[A](a))
    
    def reduceLayer(as: List[Future[A]]): List[Future[A]] = as match {
      case a :: b :: rest => ex.submit(new Callable[A]() { def call = r(a.get, b.get) }) :: reduceLayer(rest)
      case other => other
    }
    
    @tailrec
    def repeatReduce(as: List[Future[A]]): Future[A] = as match {
      case Nil => sys.error("reduce emptyList")
      case List(a) => a
      case more @ _ => repeatReduce(reduceLayer(more))
    }
    val res = repeatReduce(prep).get
    ex.shutdown
    res
  }
  
   /* Hilfsklasse: Future mit vorevaluiertem Wert. */
  class FinishedFuture[A](a: A) extends Future[A] {
    def get = a
    def get(tu: Long, unit: TimeUnit) = a
    def cancel(b: Boolean) = false
    def isCancelled() = false
    def isDone() = true
  }
}