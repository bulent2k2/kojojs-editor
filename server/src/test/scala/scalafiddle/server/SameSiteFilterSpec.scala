package scalafiddle.server

import org.scalatest._
import play.api.mvc.{Cookie, DefaultCookieHeaderEncoding, DiscardingCookie, RequestHeader, Result, Results}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

/**
  * Yanıtın koyduğu çerezlere `SameSite=Lax` (#47). Silhouette 5.0.1 çerezleri
  * `withCookies`/`discardingCookies` ile koyuyor, yani hepsi `newCookies`'te.
  */
class SameSiteFilterSpec extends WordSpec with Matchers {
  "SameSiteFilter" should {
    "SameSite'sız çereze Lax koyuyor, gerisine dokunmuyor" in {
      val c = Cookie("authenticator", "v", maxAge = Some(60), secure = true, httpOnly = true)
      SameSiteFilter.laxCerez(c) shouldBe c.copy(sameSite = Some(Cookie.SameSite.Lax))
    }
    "kendi SameSite'ı olan çereze dokunmuyor" in {
      val c = Cookie("baska", "v", sameSite = Some(Cookie.SameSite.Strict))
      SameSiteFilter.laxCerez(c) shouldBe c
    }
    "yanıttaki bütün yeni çerezleri, silinen çerez dahil, kapsıyor" in {
      val r = Results.Ok
        .withCookies(Cookie("authenticator", "v"), Cookie("OAuth2State", "s"))
        .discardingCookies(DiscardingCookie("OAuth1TokenSecret"))
      val sonuc = SameSiteFilter.laxYap(r)
      sonuc.newCookies.map(_.name) should contain theSameElementsAs Seq("authenticator", "OAuth2State", "OAuth1TokenSecret")
      sonuc.newCookies.foreach { c =>
        withClue(s"${c.name} -- ") { c.sameSite shouldBe Some(Cookie.SameSite.Lax) }
      }
      sonuc.header shouldBe r.header
    }
    "çerez koymayan yanıtı olduğu gibi bırakıyor" in {
      val r = Results.Ok("x")
      SameSiteFilter.laxYap(r) should be theSameInstanceAs r
    }
    "Play'in Set-Cookie kodlayıcısı özniteliği gerçekten yazıyor" in {
      val baslik = new DefaultCookieHeaderEncoding()
        .encodeSetCookieHeader(Seq(SameSiteFilter.laxCerez(Cookie("authenticator", "v"))))
      baslik should include("SameSite=Lax")
    }
  }

  "SameSiteFilter (süzgeç)" should {
    // İstek başlığı ve Materializer süzgeçte kullanılmıyor; null yeterli.
    val suzgec = new SameSiteFilter()(null, ExecutionContext.global)

    "eylemin yanıtındaki çerezlere Lax koyuyor" in {
      // Açık türlü işlev: Filter.apply aşırı yüklü (EssentialAction sürümü de var).
      val eylem: RequestHeader => Future[Result] = _ => Future.successful(Results.Ok.withCookies(Cookie("authenticator", "v")))
      val sonuc = Await.result(suzgec.apply(eylem)(null), 5.seconds)
      sonuc.newCookies.map(_.sameSite) shouldBe Seq(Some(Cookie.SameSite.Lax))
    }
    "Filters'a kayıtlı (sökülürse çerezler yine özniteliksiz gider)" in {
      new Filters(null, null, suzgec).filters should contain(suzgec)
    }
  }
}
