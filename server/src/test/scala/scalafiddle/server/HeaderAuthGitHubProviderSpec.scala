package scalafiddle.server

import org.scalatest._

/**
  * GitHub girişi başlık-boyutu olayı (koco-deploy docs/olaylar/2026-10-24-github-giris.md):
  * profil yanıtının başlık boyutu uyarısı için kullanılan yaklaşık hesap.
  */
class HeaderAuthGitHubProviderSpec extends WordSpec with Matchers {
  "HeaderAuthGitHubProvider.headerBytes" should {
    "boş başlıklarda 0 veriyor" in {
      HeaderAuthGitHubProvider.headerBytes(Map.empty) shouldBe 0
    }
    "her 'ad: değer\\r\\n' için ad + değer + 4 sayıyor, çok değerli başlıkta her değeri ayrı" in {
      val h = Map("a" -> Seq("bb"), "cc" -> Seq("d", "eee"))
      // a: bb\r\n = 1+2+4 ; cc: d\r\n = 2+1+4 ; cc: eee\r\n = 2+3+4
      HeaderAuthGitHubProvider.headerBytes(h) shouldBe (7 + 7 + 9)
    }
    "uyarı eşiği eski 8192 sınırının yarısı" in {
      HeaderAuthGitHubProvider.WarnHeaderBytes shouldBe 4096
    }
  }
}
