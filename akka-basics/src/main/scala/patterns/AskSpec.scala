package patterns

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{Failure, Success}

// Step: 1 Import the Ask Pattern
import akka.pattern.ask

import akka.pattern.pipe

/**
  * Lecture 33 [The Ask Pattern]
  *
  * - Technique of communicating with actors when you expect a response and that is the Ask
  *
  * Ref
  * - https://www.udemy.com/course/akka-essentials/learn/lecture/12418724#notes
  */

class AskSpec extends TestKit(ActorSystem("AskSpec")) with ImplicitSender with WordSpecLike with BeforeAndAfterAll {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  import AskSpec._

  "An authenticator " should {

    authenticatorTestSuite(Props[AuthManager])
  }

  "A piped authenticator " should {

    authenticatorTestSuite(Props[PipedAuthManager])
  }

  def authenticatorTestSuite(props: Props): Unit = {

    import AuthManager._

    "fail to authenticate a non-registered user " in {
      val authManager = system.actorOf(props)
      authManager ! Authenticate("daniel", "123")
      expectMsg(AuthFailure(AUTH_FAILURE_NOT_FOUND))
    }

    "fail to authenticate if invalid password" in {
      val authManager = system.actorOf(props)
      authManager ! RegisterUser("daniel", "123")

      authManager ! Authenticate("daniel", "1234")
      expectMsg(AuthFailure(AUTH_FAILURE_PASSWORD_INCORRECT))
    }

    "successfully authenticate a valid user" in {
      val authManager = system.actorOf(props)
      authManager ! RegisterUser("daniel", "123")

      authManager ! Authenticate("daniel", "123")
      expectMsg(AuthSuccess)
    }

  }


}

object AskSpec {

  // Assume this code is somewhere else in Application

  case class Read(key: String)
  case class Write(key: String, value: String)

  class KVActor extends Actor with ActorLogging {
    override def receive: Receive = online(Map())

    def online(kv: Map[String, String]): Receive = {
      case Read(key) =>
        log.info(s"Trying to read Key = $key")
        sender() ! kv.get(key)

      case Write(key, value) =>
        log.info(s"Writing Value $value for Key $key")
        context.become(online(kv + (key -> value)))
    }

  }

  // user Authenticator Actor
  case class RegisterUser(username: String, password: String)
  case class Authenticate(username: String, password: String)

  case class AuthFailure(message: String)
  case object AuthSuccess

  object AuthManager {
    val AUTH_FAILURE_NOT_FOUND = "username NOT found"
    val AUTH_FAILURE_PASSWORD_INCORRECT = "Password Incorrect"
  }
  class AuthManager extends Actor with ActorLogging {

    import AuthManager._

    // Step 2: Logistics
    implicit val timeout: Timeout = Timeout(1 second)
    implicit val executionContext: ExecutionContext = context.dispatcher

    protected val authDB = context.actorOf(Props[KVActor])
    override def receive: Receive = {
      case RegisterUser(name, password) => authDB ! Write(name, password) // Fire and Forget Pattern We are using so Far

      case Authenticate(name, pass) => handleAuthentication(name, pass)
    }

    def handleAuthentication(name: String, pass: String) = {
      val originalSender = sender()
      // Step 3: Ask the Actor
      val future = authDB ? Read(name)

      // Step 4: Handle the future for e.g. with onComplete
      future.onComplete {

        // Step 5: Most Important
        // NEVER CALL METHODS ON ACTOR INSTANCE OR ACCESS MUTABLE STATE IN `onComplete`
        // so avoid CLOSING OVER the actor instance or mutable state.
        case Success(None) => originalSender ! AuthFailure(AUTH_FAILURE_NOT_FOUND)
        case Success(Some(dbPassword)) =>
          if (dbPassword == pass) originalSender ! AuthSuccess
          else originalSender ! AuthFailure(AUTH_FAILURE_PASSWORD_INCORRECT)
        case Failure(_) => originalSender ! AuthFailure("System Error")
      }
    }
  }

  // Preferable Approach [Using `pipeTo`]
  // Because this doesn't expose us to `onComplete` callbacks where we can break the actor encapsulation. Notice that we're just doing functional composition with futures.
  class PipedAuthManager extends AuthManager {

    import AuthManager._

    override def handleAuthentication(name: String, pass: String): Unit = {

      // Step 3: Ask the Actor
      val future = authDB ? Read(name) // Future[Any]

      // Step 4: Process the Future until we get the responses we will send back
      val passwordFuture = future.mapTo[Option[String]] // Future[Option[String]]
      val resonseFuture = passwordFuture.map {
        case None => AuthFailure(AUTH_FAILURE_NOT_FOUND)
        case Some(dbPassword) =>
          if (dbPassword == pass) AuthSuccess
          else AuthFailure(AUTH_FAILURE_PASSWORD_INCORRECT)
      } // Future[Any] <- This will be completed with the response I will send back.

      // Step 4: Pipe the resulting future to the actor we want to send the result to

      // When the future completes send the response to the actor ref in the arg list.
      resonseFuture.pipeTo(sender())


    }
  }

}
