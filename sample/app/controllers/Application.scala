package controllers

import play.api.mvc._
import play.api.libs.json.Json._

import models._

object Application extends Controller {
  implicit val writes = Records.RecordWrites

  def index = Action { request =>
    Ok(views.html.index())
  }

  def list = Action { request =>
    Records.ensureDbPopulated()
    Ok(toJson(Records.all))
  }

}