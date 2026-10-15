package scalafiddle.client

import org.scalatest.FunSuite

import scala.scalajs.js

/**
 * "Çevir" düğmesinin istemci mantığı (kojojs-dev#183, Aşama 4).
 *
 * Yanıt örnekleri UYDURMA DEĞİL: kojojs-core router'ı (POST /translate, gerçek
 * çevirmen) yerelde koşturulup curl ile alındı. Router'ın yanıt biçimi
 * değişirse bu sınamaları yeni gerçek yanıtla yenileyin.
 */
class CevirTest extends FunSuite {

  // Türkçe betik -> İngilizce; sözlükten gelen iki "belirsiz" ve iki "kalan" ad var.
  private val trYaniti =
    """{"direction":"tr2en","code":"val başlık = \"a\"\nval halka = Picture.circle(50)\ndraw(halka)\n\"istanbul\".map\nyaşlar.take(\"Ayşe\")\nrepeat(3) { forward(10) }\n","report":{"translated":7,"leftovers":[{"name":"başlık","count":1},{"name":"yaşlar","count":1}],"ambiguous":[{"name":"daire","chosen":"Picture.circle","others":["circle"],"line":2},{"name":"büyükHarfe","chosen":"map","others":["toUpper"],"line":4}]},"remainingKeywords":[]}"""

  // İngilizce betik -> Türkçe (yön betikten bulundu)
  private val enYaniti =
    """{"direction":"en2tr","code":"dez a = 1\nileri(10)\nsatıryaz(a)\n","report":{"translated":2,"leftovers":[],"ambiguous":[]},"remainingKeywords":[]}"""

  test("yanıt okunuyor: kod, yön, sayılar, kalan ve belirsiz adlar") {
    val s = Cevir.oku(trYaniti)
    assert(s.yon == "tr2en")
    assert(s.kod.startsWith("val başlık = \"a\"\n"))
    assert(s.kod.contains("\"istanbul\".map"))
    assert(s.cevrilen == 7)
    assert(s.kalanlar == Seq(("başlık", 1), ("yaşlar", 1)))
    assert(s.belirsiz.map(_.ad) == Seq("daire", "büyükHarfe"))
    assert(s.belirsiz.head == Cevir.Belirsiz("daire", "Picture.circle", Seq("circle"), 2))
    assert(s.kalanAnahtarSozcukler.isEmpty)
  }

  test("boş rapor alanları boş dizi; Türkçe karakterler bozulmadan geçiyor") {
    val s = Cevir.oku(enYaniti)
    assert(s.yon == "en2tr")
    assert(s.kod == "dez a = 1\nileri(10)\nsatıryaz(a)\n")
    assert(s.kalanlar.isEmpty && s.belirsiz.isEmpty && s.kalanAnahtarSozcukler.isEmpty)
  }

  test("bozuk gövde oku'da istisna atıyor (hangisi olduğu sürüme göre değişiyor)") {
    intercept[Throwable](Cevir.oku("<html>vekil sayfası</html>"))
    intercept[Throwable](Cevir.oku("""{"direction":"tr2en"}""")) // report yok
  }

  test("bozuk gövdelerin hepsi sıradan istisna (ölümcül UndefinedBehaviorError değil): yanit hata döndürür") {
    val bozuklar = Seq(
      "null", "123", "[]", "\"metin\"", "{}",
      """{"direction":1,"code":"x","report":{"translated":0,"leftovers":[],"ambiguous":[]},"remainingKeywords":[]}""", // direction sayı
      """{"direction":"tr2en","code":"x","report":{"translated":"7","leftovers":[],"ambiguous":[]},"remainingKeywords":[]}""", // sayı metin
      """{"direction":"tr2en","code":"x","report":{"translated":0,"leftovers":[{"name":"a"}],"ambiguous":[]},"remainingKeywords":[]}""", // count yok
      """{"direction":"tr2en","code":"x","report":{"translated":0,"leftovers":[],"ambiguous":[{"name":"a","chosen":"b","others":[1],"line":2}]},"remainingKeywords":[]}""", // öğe sayı
      """{"direction":"tr2en","code":"x","report":{"translated":0,"leftovers":[],"ambiguous":[]},"remainingKeywords":"yok"}""" // dizi değil
    )
    bozuklar.foreach { g =>
      assert(Cevir.yanit(200, g) == Left("Çeviri sunucusundan beklenmeyen bir yanıt geldi."), g)
    }
  }

  test("yanit: 200 sonuca, bozuk 200 ve hata durumları Türkçe iletiye") {
    assert(Cevir.yanit(200, enYaniti).right.get.yon == "en2tr")
    assert(Cevir.yanit(200, "<html>vekil sayfası</html>") == Left("Çeviri sunucusundan beklenmeyen bir yanıt geldi."))
    assert(Cevir.yanit(200, """{"direction":"tr2en"}""") == Left("Çeviri sunucusundan beklenmeyen bir yanıt geldi."))
    assert(Cevir.yanit(400, "invalid direction: 'xx'") == Left("invalid direction: 'xx'"))
    assert(Cevir.yanit(413, "").left.get.contains("64 KB"))
    assert(Cevir.yanit(429, "x").left.get.contains("yoğun"))
    assert(Cevir.yanit(0, "") == Left("Sunucuya ulaşılamadı. Bağlantınızı denetleyip yine deneyin."))
    // Hata gövdesi başarılı bir yanıt gibi OKUNMAMALI: 502'de gelen geçerli JSON bile hata sayılır
    assert(Cevir.yanit(502, enYaniti).isLeft)
  }

  test("adres: yön yoksa parametresiz, varsa ?direction=") {
    assert(Cevir.adres("http://x", Cevir.Oto) == "http://x/translate")
    assert(Cevir.adres("http://x", Cevir.TrdenEn) == "http://x/translate?direction=tr2en")
    assert(Cevir.adres("", Cevir.EndenTr) == "/translate?direction=en2tr") // boş taban: /compile ile aynı kalıp ("$compilerURL/yol")
  }

  test("rapor: kalan ve belirsiz adlar satır numarasıyla, geri alma ipucu") {
    val s = Cevir.oku(trYaniti)
    val r = Cevir.rapor(s, degisti = true)
    assert(r.contains("Türkçeden İngilizceye"))
    assert(r.contains("7 ad"))
    assert(r.contains("başlık (1), yaşlar (1)"))
    assert(r.contains("satır 2: daire → Picture.circle (öbür seçenekler: circle)"))
    assert(r.contains("satır 4: büyükHarfe → map (öbür seçenekler: toUpper)"))
    assert(r.contains("Ctrl+Z"))
    assert(!r.contains("Betik değişmedi"))
  }

  test("rapor: betik değişmediyse yönü elle seçme önerisi; boş bölümler hiç görünmüyor") {
    val s = Cevir.oku(enYaniti)
    val r = Cevir.rapor(s, degisti = false)
    assert(r.contains("İngilizceden Türkçeye"))
    assert(r.contains("Betik değişmedi"))
    assert(r.contains("elle seçin"))
    assert(!r.contains("Çevrilmeden kalan"))
    assert(!r.contains("Birden çok karşılığı"))
    assert(!r.contains("anahtar sözcüğü var"))
  }

  test("rapor: çıktıda kalan anahtar sözcük uyarısı") {
    val s = Cevir.oku(enYaniti).copy(kalanAnahtarSozcukler = Seq("val", "def"))
    assert(Cevir.rapor(s, degisti = true).contains("Çıktıda hâlâ kaynak dilin anahtar sözcüğü var: val, def"))
  }

  test("hata metinleri: 400 sunucunun iletisi, 413/429/0/diğer Türkçe") {
    assert(Cevir.hataMetni(400, "geçersiz yön: 'xx' (tr2en, en2tr ya da oto olmalı)\n") ==
      "geçersiz yön: 'xx' (tr2en, en2tr ya da oto olmalı)")
    assert(Cevir.hataMetni(400, "  ") == "Çeviri yapılamadı (HTTP 400).") // boş gövde: genel dal
    assert(Cevir.hataMetni(413, "").contains("64 KB"))
    assert(Cevir.hataMetni(429, "").contains("yoğun"))
    assert(Cevir.hataMetni(0, "").contains("Sunucuya ulaşılamadı"))
    assert(Cevir.hataMetni(502, "<html>") == "Çeviri yapılamadı (HTTP 502).")
  }
}
