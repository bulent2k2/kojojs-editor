package scalafiddle.server

import org.scalatest._
import play.api.mvc.{Cookie, DiscardingCookie, Results}

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
      val c = Cookie("PLAY_SESSION", "v", sameSite = Some(Cookie.SameSite.Strict))
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
  }
}
