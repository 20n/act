package com.act.edge

import akka.actor.Actor
import akka.actor.{ActorSystem, Props}
import spray.routing._
import spray.http._
import spray.caching._
import MediaTypes._
import scala.io._
import java.io._
import java.net.URLDecoder

import akka.io.IO
import spray.can.Http
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._

object ActEdge extends App {

  // we need an ActorSystem to host our application in
  implicit val system = ActorSystem("on-spray-can")

  // create and start our service actor
  val service = system.actorOf(Props[ActEdgeServiceActor], "act-edge-service")

  implicit val timeout = Timeout(5.seconds)
  // start a new HTTP server on port 8080 with our service actor as the handler
  IO(Http) ? Http.Bind(service, interface = "localhost", port = 8080)
}

// we don't implement our route structure directly in the service actor because
// we want ability to test it independently, without having to spin up an actor
class ActEdgeServiceActor extends Actor with ActEdgeService {

  // the HttpService trait defines only one abstract member, which
  // connects the services environment to the enclosing actor or test
  def actorRefFactory = context


  // this actor only runs our route, but you could add
  // other things here, like request stream processing
  // or timeout handling
  def receive = runRoute(myRoute)
}


// this trait defines our service behavior independently from the service actor
trait ActEdgeService extends HttpService {
  val imgs_path = "/var/tmp/act-edge/"

  val myRoute: Route =
    path("render" / Rest) { str => {
        // take the render string request and create an SVG file
        // the "render" call returns the location of the created file
        parameters('callback, '_) { (jsonp_callback_name, extraid) =>
          val str_decoded = URLDecoder.decode(str, "UTF-8")
          val renderedLoc = render(imgs_path, str_decoded, jsonp_callback_name)

          // send back the contents of the SVG file to the client
          // encapsulated in JSONP padding; and w/ json { svg: "escaped_xml }
          respondWithMediaType(`application/javascript`) { 
            getFromFile(renderedLoc)
          }
        }

      }
    }~
    path("query" / Rest) { query =>
      get {
        parameters('callback, '_) { (jsonp_callback_name, extraid) =>
          val query_decoded = URLDecoder.decode(query, "UTF-8")
          val json = backend_solve(query_decoded, jsonp_callback_name)
          respondWithMediaType(`application/json`) { complete { json } }
        }
      }
    }~
    path("upload") {
      post {
        decompressRequest() {
          entity(as[spray.http.MultipartContent]) { data =>
            complete {
              data.parts.map(receivePart)
              println("Finished processing post")
              "post ack" // this ack is sent back to client
            }
          }
        }
      }
    }

    /* // Example of a GET with an int
     * path("ping" / IntNumber) { id => get { complete { "pong: " + id } } }~
     * // Example of a GET that returns html
     * path("getsomehtml") {
     *   get {
     *     respondWithMediaType(`text/html`) { 
     *       // XML is marshalled to `text/xml` by default, so override
     *       complete {
     *         <html><body><h1>hello! ordered:</h1></body></html>
     *       }
     *     }
     *   }
     * }
     */

  def backend_solve(q: String, jsonp_cb: String) = {
    /* 
     * FIX: Need to separate the edge server and the backend server for security 
     * Use AKKA actors to relay the query message to a remote server; and get json response
     */
    println("NEED FIX: current code has no separation between ")
    println("NEED FIX: edge & backend query solver servers")
    println("NEED FIX: potential security hole: compromised")
    println("NEED FIX: edge servers will expose all of Act to public")

    import com.act.query.solver
    solver.solve(q, jsonp_cb)
  }
  
  def headersMap(hdrs: Seq[HttpHeader]) = {
    // Seq[HttpHeader] example is:
    // List(Content-Type: application/octet-stream, Content-Disposition: form-data; name=filedata; filename=sample-data.zip)
    // If call is curl -X POST -F "userid=1" -F "filecomment=This is an image file" -F "filedata=@/Users/saurabhs/Desktop/sample-data.zip" localhost:8080/ms-trace
    def keyval(hdr: HttpHeader) = (hdr.name, hdr.value)
    val map = hdrs.map(keyval).toMap
    map
  }

  def splitDisposition(disp: String) = {
    // disposition string is of the form 
    // form-data; name=filedata; filename=sample-data.zip
    // form-data; name=filecomment
    // form-data; name=userid
    // If call is curl -X POST -F "userid=1" -F "filecomment=This is an image file" -F "filedata=@/Users/saurabhs/Desktop/sample-data.zip" localhost:8080/ms-trace
    val phrases = disp.split("; ")
    def pairs(p: String) = if (p.contains('=')) {
      val spl = p.split('=') 
      (spl(0), spl(1))
    } else (p, "")
    phrases.map(pairs).toMap
  }

  def writeBytesToFile(fname: String, bytes: Array[Byte]) {
    println("Writing " + bytes.length + " bytes to file " + fname)
    
    val file = new FileOutputStream(new File(fname))
    file write bytes
    file close
  }

  def receivePart(part: BodyPart) {
    part match {
      case BodyPart(httpEntity, headers) => {
        val hdrs = headersMap(headers)
        println("Headers: " + hdrs)
        val data = httpEntity.data
        if (data.nonEmpty) {
          println("HttpEntity Data #Bytes: " + data.length)
          val disposition = splitDisposition(hdrs("Content-Disposition"))
          if (hdrs.contains("Content-Type") && hdrs("Content-Type") == "application/octet-stream") {
            val bytes: Array[Byte] = data.toByteArray
            val fname: String = disposition("filename")
            writeBytesToFile(fname, bytes)
          }
        }
      }
      case x => println("Default: " + x)
    }
  }

  var global_cnt = 0;
  def render(dir: String, what: String, jsonp_callback: String) = {
    var dirf = new File(dir)
    if (!(dirf exists)) 
      dirf.mkdir()
    println("rendering: " + what)

    global_cnt = global_cnt + 1
    val id = global_cnt
    val format = "svg"
    val out = dir + "/" + id + "." + format
    val src = dir + "/" + id + "." + "mol"
    write_to_file(src, what)
    val typ = if (what startsWith "InChI=") "inchi" else "smiles"
    val cmd = List("/usr/local/bin/obabel", "-i" + typ, src, "-o" + format, "-O", out) 
    exec(cmd)

    val jsonp_padding = true
    if (jsonp_padding) {
      // if we want to allow the edge server to be called from anywhere
      // then these requests will come in as jsonp requests; so wrap it
      // for CORS Cross Domain jsonp requests
      to_jsonp(out, jsonp_callback) 
    } else {
      out
    }
  }

  def write_to_file(fname: String, contents: String) {
    val writer = new PrintWriter(new File(fname))
    writer write contents
    writer close
  }

  def to_jsonp(fname: String, jsonp_callback_fn_name: String) = {
    def escape_for_json(s: String) = {
      s.replaceAll("\"", "\\\\\"") 
    }
  
    val contents = Source.fromFile(fname).getLines().foldLeft("")((a, l) => a + " " + l)
    val json = "{ \"svg\":\"" + escape_for_json(contents) + "\"}"
    val padded = jsonp_callback_fn_name + "(\n" + json + "\n);\n"
    val json_fname = fname + ".json"
    write_to_file(json_fname, padded)

    json_fname
  }

  def exec(cmd: List[String]) {
    val p = Runtime.getRuntime().exec(cmd.toArray)
    p.waitFor()
    println("Exec done: " + cmd.mkString(" "))
    def consume(is: InputStream) {
      val br = new BufferedReader(new InputStreamReader(is))
      var read = br.readLine()
      while(read != null) {
        System.out.println(read);
        read = br.readLine()
      }
    }
    List(p.getInputStream, p.getErrorStream).map(consume)
  }

}
