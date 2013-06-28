package concrawler

/**
 * The web crawler interface.
 */
trait Crawler {
    /**
     * Crawls the web, starting at {@code startURL} and returning all links
     * reachable from there. The maximum size of resulting list should be
     * sensibly limited (e.g. 25 Results).
     * @param startURL the URL to start crawling the web
     * @return a list of urls which are reachable from {@code startURL}
     */
    def crawl(startURL: String): List[String]
}

/*
 *****************************************************************************
 */
import scala.util.Try
import java.net.ServerSocket
import java.net.Socket
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.URLDecoder
import scala.util.Success
import scala.util.Failure
import scala.util.control.NonFatal
import scala.concurrent._
import java.util.concurrent.Executors
import scala.annotation.tailrec

/**
 * Minimalistic webserver providing web-crawling functionality to clients.<br/>
 * Point a browser to:<br/>
 * http://localhost:8080/
 */
object ConCrawler {
  /** Port of the webserver. */
  private val PORT = 8080;

  val HEADER_OK = "HTTP/1.0 200 OK\r\nConnection: close\r\nServer: WebCrawler v0\r\nContent-Type: text/html\r\n\r\n";
  val HEADER_404 = "HTTP/1.0 404 Not Found\r\nConnection: close\r\nServer: WebCrawler v0\r\n";

  /** Replace this string in HTML_TEMPLATE to fill result section. */
  val RESULT_PLACEHOLDER = "<div id='RESULT_SECTION'/>"

  /** Website of the crawler. */
  val HTML_TEMPLATE = s"""
    |<html>
    |    <body>
    |      <h1>ConCrawler</h1>
    |        <form action='' method='get'>
    |          <input type='text' name='q' size='60'/>
    |          <input type='submit' value='Crawl'/>
    |        </form>
    |  $RESULT_PLACEHOLDER
    |    </body>
    |</html>""".stripMargin;
  
  implicit val ec = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(30))

  /** The webcrawler instance. */
  val crawler: Crawler = ParCrawler2

  /** Starts the webserver. */
  def main(args: Array[String]): Unit = {
    println("ConCrawler started: http://localhost:" + PORT)
    Try {
      val serverSocket = new ServerSocket(PORT)

      @tailrec
      def serve(): Unit = {
        val connection = serverSocket.accept()
        future(handleRequest(connection)).onComplete{ case _ => connection.close() }
        serve()
      }
      serve()
    }
  }

  /**
   * Handles a single request.
   * @throws IOException
   */
  def handleRequest(connection: Socket) {
    try {
      val in = new BufferedReader(new InputStreamReader(connection.getInputStream()))
      val out = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream()))

      val request = in.readLine()
      println("REQUEST: " + request)
      
      val response = computeResponse(request)
      println("RESPONSE: " + response)
      
      out.write(response)
      out.flush()
    } catch {
      case NonFatal(ex) => ex.printStackTrace()
    }
  }

  def computeResponse(request: String): String = {
    if (isRoot(request)) {
      HEADER_OK + HTML_TEMPLATE
    } else {
      val resultOrNone = for {
        query <- extractQuery(request)
        val startTime = System.currentTimeMillis()
        val urls = crawler.crawl(query)
        val duration = System.currentTimeMillis() - startTime
      } yield formatResult(query, urls, duration)
      resultOrNone.getOrElse(HEADER_404)
    }
  }

  /** Returns true if the given request matches root. */
  private def isRoot(request: String): Boolean =
    return request.matches("GET / HTTP/1\\.(?:0|1)")

  /** Returns an OK HTTP header accompanied by the result html content. */
  private def formatResult(query: String, urls: List[String], duration: Long): String = {
    val resultTitle = s"<h2>URLs reachable from: $query</h2>\n"

    val urlLinks = urls.map(url => s"<li><a href='$url'/>$url</a></li>")
    val resultList = urlLinks.mkString("<ol>", "\n", "</ol>")

    val processingTime = "Processing time: " + duration + "ms"

    val body = HTML_TEMPLATE.replace(RESULT_PLACEHOLDER, resultTitle + resultList + processingTime)
    HEADER_OK + body
  }

  /** Extracts the query of the request. */
  private def extractQuery(request: String): Option[String] = {
    val QueryPattern = "GET /\\?q=(.*) HTTP/1\\.(?:0|1)".r
    request match {
      case QueryPattern(url) =>
        val decoded = URLDecoder.decode(url, "utf8")
        val httpUrl =
          if (decoded.startsWith("http")) decoded
          else "http://" + decoded
        Some(httpUrl)
      case _ => None
    }
  }
}

/*
 ***************************************************************************************************
 */
import java.io.BufferedInputStream
import java.net.URL
import java.net.URLConnection
import scala.collection.JavaConversions._
import scala.concurrent._
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.util.concurrent.Executors
import scala.concurrent.duration.Duration
import java.util.concurrent.{ ConcurrentSkipListSet => JSet }
import scala.util.Try
import java.util.concurrent.atomic.AtomicInteger

/** Crawls the web in parallel. */
object ParCrawler2 extends Crawler {
  /** Maximal number of visited urls per request. */
  private val MAX_VISITS = 50

  /** Idealerweise würde diese Methode gleich ein Future[List[String]] zurück geben. */
  def crawl(startURL: String): List[String] = {
    Await.result(crawlParallel(startURL), Duration.Inf)
  }

  def crawlParallel(startUrl: String): Future[List[String]] = {
    implicit val ec = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(50))
    val p = promise[List[String]]
    val result = new JSet[String]()
    val nrWorkers = new AtomicInteger

    def finish() {
      if  (!p.isCompleted) {
        p.trySuccess(result.toList.take(MAX_VISITS))
        ec.shutdown()
      }
    }

    def recCrawl(url: String) {
      nrWorkers.incrementAndGet()
      future {
        val urls = crawlUrl(url)
        val urlsToVisit = urls.filterNot(result)
        result.addAll(urlsToVisit)
        if (result.size() >= MAX_VISITS) { 
          finish() // finish if enough results
        } else {
          urlsToVisit.foreach(recCrawl)
        }
        if (nrWorkers.decrementAndGet() == 0) {
          finish() // finish if no more urls to crawl
        }
      }
    }

    recCrawl(startUrl)
    p.future
  }

  def crawlUrl(startURL: String): List[String] =
    loadDocument(startURL).map(httpLinks).getOrElse(Nil)

  def loadDocument(startURL: String): Option[Document] =
    Try(Jsoup.parse(Jsoup.connect(startURL).timeout(3000).get().html())).toOption

  /* Split pure parts from IO parts */
  def httpLinks(doc: Document): List[String] =
    doc.select("a[href]").toList.map(_.absUrl("href")).filter(_.startsWith("http"))
}