package controllers

import play.api.mvc._
import play.api.libs.iteratee.Enumeratee
import play.api.libs.json.Json.toJson
import play.api.libs.json.Writes.listWrites
import play.api.libs.Comet

import models._


object Application extends Controller {
  implicit val recordsWrites = listWrites(Records.RecordWrites)

  def index = Action { request =>
    Ok(views.html.index())
  }

  def list = Action { request =>
    Records.ensureDbPopulated()
    Ok(toJson(Records.all))
  }

  def listComet = Action { request =>
    Records.ensureDbPopulated()

    // Records fetched in chunks of 2, and asynchronously piped out to
    // browser in chunked http responses, to be handled by comet callback.
    //
    // see http://www.playframework.com/documentation/2.0.x/ScalaComet
    val pipeline = (
      Records.enumerateAllInChunksOfTwo
        &> Enumeratee.map(toJson(_))
        &> Comet(callback = "parent.cometMessage"))

    Ok.stream(pipeline)
  }

}