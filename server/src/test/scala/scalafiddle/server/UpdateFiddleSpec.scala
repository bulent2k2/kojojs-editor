package scalafiddle.server

import java.sql.DriverManager

import akka.actor.{ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.mohiva.play.silhouette.api.LoginInfo
import org.scalatest._
import play.api.Configuration

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.Try
import scalafiddle.server.dao.Fiddle
import scalafiddle.server.models.User
import scalafiddle.shared.{FiddleData, FiddleId, UserInfo}

/**
  * Fiddle güncelleme yetkisi kayıtlı sahibe göre mi? Eskiden `ApiService.update`
  * istemcinin gönderdiği `FiddleData.author`a bakıyordu; alan boş gelince izin
  * veriyordu, yani herkes başkasının fiddle'ına onun adına yeni sürüm ekleyebiliyordu.
  *
  * Gerçek Persistence aktörü, application.conf'taki bellek içi "h2" veritabanıyla.
  */
class UpdateFiddleSpec extends WordSpec with Matchers with BeforeAndAfterAll {
  implicit val timeout: Timeout = 15.seconds

  val system = ActorSystem("update-fiddle-spec")

  // application.conf "h2": jdbc:h2:mem:tsql1, DB_CLOSE_DELAY=-1. Bu bağlantı açık
  // kaldıkça veritabanı yaşıyor; tabloyu buradan kuruyoruz.
  val baglanti = DriverManager.getConnection("jdbc:h2:mem:tsql1;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
  baglanti.createStatement().execute(
    """CREATE TABLE IF NOT EXISTS "fiddle" (
      |  "id" VARCHAR NOT NULL, "version" INTEGER NOT NULL, "name" VARCHAR NOT NULL,
      |  "description" VARCHAR NOT NULL, "sourcecode" VARCHAR NOT NULL, "libraries" VARCHAR NOT NULL,
      |  "scala_version" VARCHAR NOT NULL, "user" VARCHAR NOT NULL, "parent" VARCHAR,
      |  "created" BIGINT NOT NULL, "removed" BOOLEAN NOT NULL)""".stripMargin)

  val persistence = system.actorOf(Props(new Persistence(Configuration.from(Map("scalafiddle.dbConfig" -> "h2")))))

  override def afterAll(): Unit = {
    Await.result(system.terminate(), 15.seconds)
    baglanti.close()
  }

  private def kullanici(id: String) =
    User(id, LoginInfo("github", id), None, None, Some(id), None, None, activated = true)

  private def fiddle(kod: String, yazar: Option[UserInfo]) =
    FiddleData("ad", "", kod, Nil, Nil, "2.13", yazar)

  private def api(k: Option[User]) = new ApiService(persistence, k, Nil)

  private def bekle[T](f: scala.concurrent.Future[T]): T = Await.result(f, 15.seconds)

  private def sonSurum(id: String): Fiddle =
    bekle((persistence ? FindLastFiddle(id)).mapTo[Try[Fiddle]]).get

  private def kaydet(k: Option[User]): FiddleId =
    bekle(api(k).save(fiddle("sahibin kodu", None))) match {
      case Right(fid) => fid
      case Left(e)    => fail(e)
    }

  "ApiService.update" should {
    "başkasının fiddle'ına, author boş gönderilse de, yeni sürüm eklemiyor" in {
      val fid = kaydet(Some(kullanici("github:sahip")))
      val sonuc = bekle(api(Some(kullanici("github:baskasi"))).update(fiddle("saldırgan kodu", None), fid.id))
      sonuc shouldBe Left("Not allowed to update fiddle")
      sonSurum(fid.id).sourceCode shouldBe "sahibin kodu"
    }

    "author sahibi gösterse de oturumsuz isteği reddediyor" in {
      val fid = kaydet(Some(kullanici("github:sahip")))
      val sahte = Some(UserInfo("github:sahip", "sahip", None, loggedIn = true))
      bekle(api(None).update(fiddle("saldırgan kodu", sahte), fid.id)) shouldBe Left("Not allowed to update fiddle")
      sonSurum(fid.id).sourceCode shouldBe "sahibin kodu"
    }

    "sahibin kendi güncellemesine izin veriyor" in {
      val sahip = Some(kullanici("github:sahip"))
      val fid   = kaydet(sahip)
      bekle(api(sahip).update(fiddle("yeni kod", None), fid.id)) shouldBe Right(FiddleId(fid.id, fid.version + 1))
      val son = sonSurum(fid.id)
      son.sourceCode shouldBe "yeni kod"
      son.user shouldBe "github:sahip"
    }

    "anonim fiddle'ı herkes güncelleyebiliyor (eski davranış)" in {
      val fid = kaydet(None)
      bekle(api(Some(kullanici("github:biri"))).update(fiddle("yeni kod", None), fid.id)) shouldBe
        Right(FiddleId(fid.id, fid.version + 1))
      bekle(api(None).update(fiddle("daha yeni", None), fid.id)) shouldBe Right(FiddleId(fid.id, fid.version + 2))
    }
  }

  "FiddleOwnership.mayUpdate" should {
    "kayıtlı sahibe ve anonim fiddle'a izin verip gerisini reddediyor" in {
      FiddleOwnership.mayUpdate("github:sahip", "github:sahip") shouldBe true
      FiddleOwnership.mayUpdate("anonymous", "github:biri") shouldBe true
      FiddleOwnership.mayUpdate("anonymous", "anonymous") shouldBe true
      FiddleOwnership.mayUpdate("github:sahip", "github:baskasi") shouldBe false
      FiddleOwnership.mayUpdate("github:sahip", "anonymous") shouldBe false
    }
  }
}
