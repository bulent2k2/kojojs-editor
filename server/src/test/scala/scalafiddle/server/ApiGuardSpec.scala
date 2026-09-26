package scalafiddle.server

import org.scalatest._
import play.api.mvc.Headers

import scalafiddle.shared.ApiHeader

/**
  * `/api` CSRF koruması (#47): editörün isteği `ApiHeader` taşıyor; başka bir
  * sitenin formu, `text/plain` POST'u ya da opak kökenli bir çerçeve taşıyamıyor.
  */
class ApiGuardSpec extends WordSpec with Matchers {
  private val baslik = ApiHeader.Name -> ApiHeader.Value

  "ApiGuard.izinli" should {
    "editörün isteğini (başlık var, Origin aynı site) geçiriyor" in {
      ApiGuard.izinli(Headers(baslik, "Origin" -> "https://ikojo.fly.dev")) shouldBe true
    }
    "Origin'siz ama başlıklı isteği geçiriyor" in {
      ApiGuard.izinli(Headers(baslik)) shouldBe true
    }
    "başlık adında büyük/küçük harfe bakmıyor" in {
      ApiGuard.izinli(Headers(ApiHeader.Name.toLowerCase -> ApiHeader.Value)) shouldBe true
    }
    "başlıksız isteği (başka sitenin formu) reddediyor" in {
      ApiGuard.izinli(Headers("Content-Type" -> "text/plain", "Origin" -> "https://kotu.example")) shouldBe false
    }
    "başlığın değeri yanlışsa reddediyor" in {
      ApiGuard.izinli(Headers(ApiHeader.Name -> "XMLHttpRequest")) shouldBe false
    }
    "Origin: null (opak çerçeve) başlık taşısa da reddediliyor" in {
      ApiGuard.izinli(Headers(baslik, "Origin" -> "null")) shouldBe false
    }
  }

  "ApiGuard.fiddleKimligiMi" should {
    "Persistence.createId biçimini (7 karakter, 0-9A-Za-z) kabul ediyor" in {
      ApiGuard.fiddleKimligiMi("a1B2c3D") shouldBe true
    }
    "başka her şeyi reddediyor" in {
      Seq("", "abc", "a1B2c3D4", "abc\"de", "a1B2c3", "ab/cdef", "ab cdef", "çğüşöıİ").foreach { id =>
        withClue(s"'$id' -- ") { ApiGuard.fiddleKimligiMi(id) shouldBe false }
      }
    }
  }
}
