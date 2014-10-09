package com.act.edge

import akka.actor.Actor
import spray.routing._
import spray.http._
import spray.caching._
import MediaTypes._
import scala.io._
import java.io._
import java.net.URLDecoder

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
  val rendered_imgs_path = "/var/tmp/act-edge/"

  val myRoute: Route =
    path("ping" / IntNumber) { id => get { complete { "pong: " + id } } }~
    path("render" / Rest) { str => {
        val renderedLoc = render(rendered_imgs_path, URLDecoder.decode(str, "UTF-8"))
        getFromFile(renderedLoc)
      }
    }~
    path("sampleimage") {
      getFromFile("/Users/saurabhs/my-project/sample.png")
    }~
    path("getsomehtml") {
      get {
        respondWithMediaType(`text/html`) { // XML is marshalled to `text/xml` by default, so we simply override here
          complete {
            <html><body><h1>hello! ordered:</h1></body></html>
          }
        }
      }
    }~
    path("ms-trace") {
      post {
        decompressRequest() {
          entity(as[spray.http.MultipartContent]) { data =>
            complete {
              data.parts.map(receivePart)
              println("Finished processing post")
              "post ack"
            }
          }
        }
      }
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
  def render(dir: String, what: String) = {
    var dirf = new File(dir)
    if (!(dirf exists)) 
      dirf mkdir
    println("rendering: " + dir + what)

    global_cnt = global_cnt + 1
    val id = global_cnt
    val format = "svg"
    val out = id + "." + format
    val src = id + ".mol"
    val writer = new PrintWriter(new File(rendered_imgs_path, src))
    writer write what
    writer close
    val typ = if (what startsWith "InChI=") "inchi" else "smiles"
    val cmd = List("/usr/local/bin/obabel", "-i" + typ, src, "-o" + format, "-O", out) 
    exec(cmd)
    dir + "/" + out
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
