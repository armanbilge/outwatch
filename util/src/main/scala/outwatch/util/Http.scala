package outwatch.util

import cats.effect.IO
import colibri._

import org.scalajs.dom.ext.Ajax.InputData
import org.scalajs.dom.ext.{Ajax, AjaxException}
import org.scalajs.dom.{Blob, XMLHttpRequest}

import scala.scalajs.js
import scala.scalajs.js.typedarray.ArrayBuffer
import scala.scalajs.js.|
import cats.effect.ContextShift

import scala.concurrent.{ExecutionContext, Future}

object Http {
  final case class Request(url: String,
    data: InputData = "",
    timeout: Int = 0,
    headers: Map[String, String] = Map.empty,
    crossDomain: Boolean = false,
    responseType: String = "",
    method: String = Get.toString,
    withCredentials: Boolean = false
  )

  type BodyType = String | ArrayBuffer | Blob | js.Dynamic | js.Any

  final case class Response(
    body: BodyType,
    status: Int,
    responseType: String,
    xhr: XMLHttpRequest,
    response: js.Any
  )

  sealed trait HttpRequestType
  case object Get extends HttpRequestType
  case object Post extends HttpRequestType
  case object Delete extends HttpRequestType
  case object Put extends HttpRequestType
  case object Options extends HttpRequestType
  case object Head extends HttpRequestType

  private def toResponse(req: XMLHttpRequest): Response = {
    val body : BodyType = req.responseType match {
      case "" => req.response.asInstanceOf[String]
      case "text" => req.responseText
      case "json" => req.response.asInstanceOf[js.Dynamic]
      case "arraybuffer" => req.response.asInstanceOf[ArrayBuffer]
      case "blob" => req.response.asInstanceOf[Blob]
      case _ => req.response
    }

    Response(
      body = body,
      status = req.status,
      responseType = req.responseType,
      xhr = req,
      response = req.response
    )
  }

  private def ajax(request: Request): Future[XMLHttpRequest] = Ajax(
    method = request.method,
    url = request.url,
    data = request.data,
    timeout = request.timeout,
    headers = request.headers,
    withCredentials = request.withCredentials,
    responseType = request.responseType
  ): @scala.annotation.nowarn("cat=deprecation") // TODO

  private def request(observable: Observable[Request], requestType: HttpRequestType)(implicit ec: ExecutionContext): Observable[Response] =
    observable.switchMap { request =>
      Observable.fromFuture(
        ajax(request.copy(method = requestType.toString))
          .map(toResponse)
          .recover {
            case AjaxException(req) => toResponse(req)
          }
      )
    }.publish

  private def requestWithUrl(urls: Observable[String], requestType: HttpRequestType)(implicit ec: ExecutionContext) =
    request(urls.map(url => Request(url)), requestType: HttpRequestType)

  def single(request: Request, method: HttpRequestType)(implicit cs: ContextShift[IO]): IO[Response] =
    IO.fromFuture(IO(ajax(request.copy(method = method.toString)))).map(toResponse _)

  def getWithUrl(urls: Observable[String])(implicit ec: ExecutionContext) = requestWithUrl(urls, Get)

  def get(requests: Observable[Request])(implicit ec: ExecutionContext) = request(requests, Get)

  def post(requests: Observable[Request])(implicit ec: ExecutionContext) = request(requests, Post)

  def delete(requests: Observable[Request])(implicit ec: ExecutionContext) = request(requests, Delete)

  def put(requests: Observable[Request])(implicit ec: ExecutionContext) = request(requests, Put)

  def options(requests: Observable[Request])(implicit ec: ExecutionContext) = request(requests, Options)

  def head(requests: Observable[Request])(implicit ec: ExecutionContext) = request(requests, Head)

}
