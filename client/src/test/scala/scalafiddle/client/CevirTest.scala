package scalafiddle.client

import org.scalatest.FunSuite

import scala.scalajs.js

/**
 * "Çevir" düğmesinin istemci mantığı (kojojs-dev#183, Aşama 4).
 *
 * Yanıt örnekleri UYDURMA DEĞİL: kojojs-core router'ı (POST /cevir, gerçek
 * çevirmen) yerelde koşturulup curl ile alındı. Router'ın yanıt biçimi
 * değişirse bu sınamaları yeni gerçek yanıtla yenileyin.
 */
class CevirTest extends FunSuite {

  // Türkçe betik -> İngilizce; sözlükten gelen iki "belirsiz" ve iki "kalan" ad var.
  private val trYaniti =
    """{"yon":"tr2en","kod":"val başlık = \"a\"\nval halka = Picture.circle(50)\ndraw(halka)\n\"istanbul\".map\nyaşlar.take(\"Ayşe\")\nrepeat(3) { forward(10) }\n","rapor":{"cevrilen":7,"kalanlar":[{"ad":"başlık","sayi":1},{"ad":"yaşlar","sayi":1}],"belirsiz":[{"ad":"daire","secilen":"Picture.circle","digerleri":["circle"],"satir":2},{"ad":"büyükHarfe","secilen":"map","digerleri":["toUpper"],"satir":4}]},"kalanAnahtarSozcukler":[]}"""

  // İngilizce betik -> Türkçe (yön betikten bulundu)
  private val enYaniti =
    """{"yon":"en2tr","kod":"dez a = 1\nileri(10)\nsatıryaz(a)\n","rapor":{"cevrilen":2,"kalanlar":[],"belirsiz":[]},"kalanAnahtarSozcukler":[]}"""

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
    intercept[Throwable](Cevir.oku("""{"yon":"tr2en"}""")) // rapor yok
  }

  test("bozuk gövdelerin hepsi sıradan istisna (ölümcül UndefinedBehaviorError değil): yanit hata döndürür") {
    val bozuklar = Seq(
      "null", "123", "[]", "\"metin\"", "{}",
      """{"yon":1,"kod":"x","rapor":{"cevrilen":0,"kalanlar":[],"belirsiz":[]},"kalanAnahtarSozcukler":[]}""", // yon sayı
      """{"yon":"tr2en","kod":"x","rapor":{"cevrilen":"7","kalanlar":[],"belirsiz":[]},"kalanAnahtarSozcukler":[]}""", // sayı metin
      """{"yon":"tr2en","kod":"x","rapor":{"cevrilen":0,"kalanlar":[{"ad":"a"}],"belirsiz":[]},"kalanAnahtarSozcukler":[]}""", // sayi yok
      """{"yon":"tr2en","kod":"x","rapor":{"cevrilen":0,"kalanlar":[],"belirsiz":[{"ad":"a","secilen":"b","digerleri":[1],"satir":2}]},"kalanAnahtarSozcukler":[]}""", // öğe sayı
      """{"yon":"tr2en","kod":"x","rapor":{"cevrilen":0,"kalanlar":[],"belirsiz":[]},"kalanAnahtarSozcukler":"yok"}""" // dizi değil
    )
    bozuklar.foreach { g =>
      assert(Cevir.yanit(200, g) == Left("Çeviri sunucusundan beklenmeyen bir yanıt geldi."), g)
    }
  }

  test("yanit: 200 sonuca, bozuk 200 ve hata durumları Türkçe iletiye") {
    assert(Cevir.yanit(200, enYaniti).right.get.yon == "en2tr")
    assert(Cevir.yanit(200, "<html>vekil sayfası</html>") == Left("Çeviri sunucusundan beklenmeyen bir yanıt geldi."))
    assert(Cevir.yanit(200, """{"yon":"tr2en"}""") == Left("Çeviri sunucusundan beklenmeyen bir yanıt geldi."))
    assert(Cevir.yanit(400, "geçersiz yön: 'xx'") == Left("geçersiz yön: 'xx'"))
    assert(Cevir.yanit(413, "").left.get.contains("64 KB"))
    assert(Cevir.yanit(429, "x").left.get.contains("yoğun"))
    assert(Cevir.yanit(0, "") == Left("Sunucuya ulaşılamadı. Bağlantınızı denetleyip yine deneyin."))
    // Hata gövdesi başarılı bir yanıt gibi OKUNMAMALI: 502'de gelen geçerli JSON bile hata sayılır
    assert(Cevir.yanit(502, enYaniti).isLeft)
  }

  test("adres: yön yoksa parametresiz, varsa ?yon=") {
    assert(Cevir.adres("http://x", Cevir.Oto) == "http://x/cevir")
    assert(Cevir.adres("http://x", Cevir.TrdenEn) == "http://x/cevir?yon=tr2en")
    assert(Cevir.adres("", Cevir.EndenTr) == "/cevir?yon=en2tr") // koco-deploy'da compilerURL boş (tek köken)
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
