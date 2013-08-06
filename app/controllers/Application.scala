package controllers

import play.api.mvc._
import play.api.libs.iteratee.{Concurrent, Enumerator}

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
      body = channel andThen Enumerator.eof
    )

  }
  
}