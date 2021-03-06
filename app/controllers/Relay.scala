package controllers

import play.api.mvc._
import play.api.libs.json.JsValue

import lila.api.Context
import lila.app._
import lila.relay.{ Relay => RelayModel }
import views._

object Relay extends LilaController {

  private val env = Env.relay

  def index = Open { implicit ctx =>
    env.api.all(ctx.me) map { sel =>
      Ok(html.relay.index(sel))
    }
  }

  def form = Secure(_.Beta) { implicit ctx => me =>
    NoLame {
      Ok(html.relay.create(env.forms.create)).fuccess
    }
  }

  def create = SecureBody(_.Beta) { implicit ctx => me =>
    implicit val req = ctx.body
    env.forms.create.bindFromRequest.fold(
      err => BadRequest(html.relay.create(err)).fuccess,
      setup => env.api.create(setup, me) map { relay =>
        Redirect(showRoute(relay))
      }
    )
  }

  def edit(slug: String, id: String) = Auth { implicit ctx => me =>
    OptionFuResult(env.api.byIdAndOwner(RelayModel.Id(id), me)) { relay =>
      Ok(html.relay.edit(relay, env.forms.edit(relay))).fuccess
    }
  }

  def update(slug: String, id: String) = AuthBody { implicit ctx => me =>
    OptionFuResult(env.api.byIdAndOwner(RelayModel.Id(id), me)) { relay =>
      implicit val req = ctx.body
      env.forms.edit(relay).bindFromRequest.fold(
        err => BadRequest(html.relay.edit(relay, err)).fuccess,
        data => env.api.update(data update relay) inject Redirect(showRoute(relay))
      )
    }
  }

  def show(slug: String, id: String) = Open { implicit ctx =>
    OptionFuResult(env.api byId RelayModel.Id(id)) { relay =>
      if (relay.slug != slug) Redirect(showRoute(relay)).fuccess
      else Env.study.api byIdWithChapter relay.studyId flatMap {
        _ ?? { oldSc =>
          for {
            (sc, studyData) <- Study.getJsonData(oldSc)
            data = lila.relay.JsonView.makeData(relay, studyData)
            chat <- Study.chatOf(sc.study)
            sVersion <- Env.study.version(sc.study.id)
          } yield Ok(html.relay.show(relay, sc.study, data, chat, sVersion))
        }
      }
    }
  }

  def websocket(id: String, apiVersion: Int) = SocketOption[JsValue] { implicit ctx =>
    get("sri") ?? { uid =>
      env.api byId RelayModel.Id(id) flatMap {
        _ ?? { relay =>
          env.socketHandler.join(
            relayId = relay.id,
            uid = lila.socket.Socket.Uid(uid),
            user = ctx.me
          )
        }
      }
    }
  }

  private def showRoute(r: RelayModel) = routes.Relay.show(r.slug, r.id.value)
}
