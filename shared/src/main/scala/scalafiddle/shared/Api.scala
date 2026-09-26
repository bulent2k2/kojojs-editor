package scalafiddle.shared

import scala.concurrent.Future

/**
  * `/api` isteklerinin taşıması gereken başlık (#47). Başka bir site özel bir
  * başlık ekleyemez (ön-uçuş ister, `/api` CORS'a açık değil); sunucu başlıksız
  * isteği reddediyor.
  */
object ApiHeader {
  val Name  = "X-Requested-With"
  val Value = "koco"
}

case class LoginProvider(id: String, name: String, logoUrl: String)

case class UserInfo(id: String, name: String, avatarUrl: Option[String], loggedIn: Boolean)

case class FiddleVersions(id: String, name: String, libraries: Seq[String], latestVersion: Int, updated: Long)

trait Api {
  def save(fiddle: FiddleData): Future[Either[String, FiddleId]]

  def update(fiddle: FiddleData, id: String): Future[Either[String, FiddleId]]

  def fork(fiddle: FiddleData, id: String, version: Int): Future[Either[String, FiddleId]]

  def loginProviders(): Seq[LoginProvider]

  def userInfo(): UserInfo

  def listFiddles(): Future[Seq[FiddleVersions]]
}
