package controllers

import play.api.mvc._
import play.api.libs.json.Json._

import models._
import models.Records.RecordWrites

import play.api.libs.json.{JsObject, JsValue, Writes}

object Application extends Controller {

  def index = Action { request =>
    Ok(views.html.index())
  }

  def list = Action { request =>
    Records.ensureDbPopulated()
    Ok(toJson(Records.all))
  }

}