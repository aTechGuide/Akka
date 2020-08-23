package lowlevelserver

import java.io.InputStream
import java.security.{KeyStore, SecureRandom}

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse, StatusCodes}
import akka.http.scaladsl.{ConnectionContext, Http, HttpsConnectionContext}
import akka.stream.ActorMaterializer
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}

/**
  * Akka HTTP Lecture 11 [Low Level HTTPs]
  *
  *
  * Ref
  * - https://www.udemy.com/course/akka-http/learn/lecture/14128455#overview
  */

object HTTPsContext {

  // Step 1: KeyStore
  val ks: KeyStore = KeyStore.getInstance("PKCS12")
  val keystoreFile: InputStream = getClass.getClassLoader.getResourceAsStream("keystore.pkcs12")

  val password = "akka-https".toCharArray // Fetch password from a secure place
  ks.load(keystoreFile, password)

  // Step 2: Initialize a key manager [Manages the HTTPs certificates within a key store]
  val keyManagerFactory = KeyManagerFactory.getInstance("SunX509") // PKI = Public Key Infrastructure
  keyManagerFactory.init(ks, password)

  // Step 3: Initialize a trust manager [Managers who signed those certificates]
  val trustManagerFactory = TrustManagerFactory.getInstance("SunX509")
  trustManagerFactory.init(ks)

  // Step 4: Initialize an SSL Context
  val sslContext: SSLContext = SSLContext.getInstance("TLS") // Transport Layer Security
  sslContext.init(keyManagerFactory.getKeyManagers, trustManagerFactory.getTrustManagers, new SecureRandom())

  // Step 5: Return the HTTPs Connection Context
  val httpsConnectionContext: HttpsConnectionContext = ConnectionContext.https(sslContext)


}

object LowLevelHttps extends App {

  implicit val system: ActorSystem = ActorSystem("LowLevelHttps")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  val requestHandler: HttpRequest => HttpResponse = {
    case HttpRequest(HttpMethods.GET, _, _, _, _) => // method, URI, HTTP Headers, Content and the protocol (HTTP1.1 / HTTP 2.0)
      HttpResponse(
        StatusCodes.OK, // HTTP 200
        entity = HttpEntity( // Payload
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            | <body>
            |   Hello from Akka HTTP
            | </body>
            |</html>
          """.stripMargin
        )
      )

    case request: HttpRequest =>
      request.discardEntityBytes()
      HttpResponse(
        StatusCodes.NotFound, // HTTP 404
        entity = HttpEntity( // Payload
          ContentTypes.`text/html(UTF-8)`,
          """
            |<html>
            | <body>
            |   OOPS ! The resource can NOT be Found
            | </body>
            |</html>
          """.stripMargin
        )
      )
  }

  val httpsBinding = Http().bindAndHandleSync(requestHandler, "localhost", 8443, HTTPsContext.httpsConnectionContext)

}
