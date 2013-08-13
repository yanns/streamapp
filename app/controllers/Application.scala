package controllers

import play.api.mvc._
import play.api.libs.iteratee._
import scala.concurrent.{Future, Promise}
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.ResponseHeader
import play.api.mvc.SimpleResult
import play.api.libs.ws.{ResponseHeaders, WS}

object Application extends Controller {
  
  def index = Action {
    Ok(views.html.index())
  }

  def stream = Action { request =>

    val content = "hello world ".*(10).getBytes

    val channel = Concurrent.unicast[Array[Byte]] (
      onStart = { pushee =>
        pushee.push(content)
        pushee.push(content)
        pushee.end()
      }
    )

    SimpleResult(
      header = ResponseHeader(
        OK,
        Map(
          CONTENT_LENGTH.toString -> (content.size * 2).toString,
          CONTENT_DISPOSITION -> s"""attachment; filename="stream-test.txt"""",
          CONTENT_TYPE -> "plain/txt"
        )),
      body = channel
    )

  }
  
  def streamFromIteratee = Action { request =>

    val content = "hello world ".*(10).getBytes

    val (iteratee, channel) = joined[Array[Byte]]

    // send first chunk
    Enumerator(content)(iteratee) map { it =>
      // send second chunk
      Enumerator(content)(it) map { it2 =>
        // finish streaming
        Enumerator.eof(it2)
      }
    }

    SimpleResult(
      header = ResponseHeader(
        OK,
        Map(
          CONTENT_LENGTH.toString -> (content.size * 2).toString,
          CONTENT_DISPOSITION -> s"""attachment; filename="stream2-test.txt"""",
          CONTENT_TYPE -> "plain/txt"
        )),
      body = channel
    )

  }

  def streamFromWS = Action { request =>

    val (wsConsumer, stream) = joined[Array[Byte]]

    var contentLength = 0
    var contentType = "binary/octet-stream"
    val consumer = { rs: ResponseHeaders =>
      contentLength = rs.headers.get("Content-Length").get(0).toInt
      contentType = rs.headers.get("Content-Type").map(_(0)).getOrElse("binary/octet-stream")
      wsConsumer
    }
    Async {
      WS.url("http://www.bvg.de/index.php/de/binaries/asset/download/780593/file/1-1").get(consumer).map { it =>
        SimpleResult(
          header = ResponseHeader(
            OK,
            Map(
              CONTENT_LENGTH.toString -> contentLength.toString,
              CONTENT_DISPOSITION -> s"""attachment; filename="Berlin-Tarifbereiche.pdf"""",
              CONTENT_TYPE -> contentType
            )),
          body = stream
        )
      }
    }

  }

  /**
   * Create a joined iteratee enumerator pair.
   *
   * When the enumerator is applied to an iteratee, the iteratee subsequently consumes whatever the iteratee in the pair
   * is applied to.  Consequently the enumerator is "one shot", applying it to subsequent iteratees will throw an
   * exception.
   */
  private def joined[A]: (Iteratee[A, Unit], Enumerator[A]) = {
    val promisedIteratee = Promise[Iteratee[A, Unit]]()
    val enumerator = new Enumerator[A] {
      def apply[B](i: Iteratee[A, B]): Future[Iteratee[A, B]] = {
        val doneIteratee = Promise[Iteratee[A, B]]()

        // Equivalent to map, but allows us to handle failures
        def wrap(delegate: Iteratee[A, B]): Iteratee[A, B] = new Iteratee[A, B] {
          def fold[C](folder: (Step[A, B]) => Future[C]) = {
            val toReturn = delegate.fold {
              case done @ Step.Done(a, in) => {
                doneIteratee.success(done.it)
                folder(done)
              }
              case Step.Cont(k) => {
                folder(Step.Cont(k.andThen(wrap)))
              }
              case err => folder(err)
            }
            toReturn.onFailure {
              case e => doneIteratee.failure(e)
            }
            toReturn
          }
        }

        if (promisedIteratee.trySuccess(wrap(i).map(_ => ()))) {
          doneIteratee.future
        } else {
          throw new IllegalStateException("Joined enumerator may only be applied once")
        }
      }
    }
    (Iteratee.flatten(promisedIteratee.future), enumerator)
  }

}