package scalafiddle.client

import org.scalajs.dom

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js
import scala.util.Try

/**
 * Editördeki "Çevir" düğmesinin istemci mantığı (kojojs-dev#183, Aşama 4).
 *
 * Betiği sunucuya (`POST /cevir`, kojojs-core router) gönderir, masaüstü
 * çevirmeninin sonucunu alır ve editöre yazar. Bu dosya BİLEREK yalnız
 * scalajs-dom ve js.Dynamic'e dayanıyor (ne React, ne diode, ne upickle):
 * editörün kendi derlemesi bu ortamda koşturulamıyor, bu dosya ise ayrı, güncel
 * bir Scala.js derlemesinde derlenip Node'da sınanabiliyor (CevirTest).
 *
 * Yanıtın biçimi (kojojs-core router/Cevir.scala):
 * {{{
 * { "yon": "tr2en", "kod": "...",
 *   "rapor": { "cevrilen": 7,
 *              "kalanlar": [ { "ad": "halka", "sayi": 3 } ],
 *              "belirsiz": [ { "ad": "al", "secilen": "take", "digerleri": ["get"], "satir": 4 } ] },
 *   "kalanAnahtarSozcukler": [] }
 * }}}
 */
object Cevir {

  /** Birden çok karşılığı olan, en sık görüleniyle çevrilen ad. */
  case class Belirsiz(ad: String, secilen: String, digerleri: Seq[String], satir: Int)

  /**
   * @param yon                    sunucunun kullandığı yön: "tr2en" ya da "en2tr"
   * @param kod                    çevrilmiş betik
   * @param kalanlar               çevrilmeden kalan adlar ve kaç kez geçtikleri
   * @param kalanAnahtarSozcukler  çıktıda hâlâ kaynak dilin anahtar sözcüğü var (çeviri eksik)
   */
  case class Sonuc(yon: String,
                   kod: String,
                   cevrilen: Int,
                   kalanlar: Seq[(String, Int)],
                   belirsiz: Seq[Belirsiz],
                   kalanAnahtarSozcukler: Seq[String])

  /** Yön seçenekleri: `None` = sunucu betikten bulsun (Türkçe anahtar sözcük varsa tr2en). */
  val Oto: Option[String]    = None
  val TrdenEn: Option[String] = Some("tr2en")
  val EndenTr: Option[String] = Some("en2tr")

  /** `taban`: derleyicinin adresi (`ScalaFiddleConfig.compilerURL`), sonunda eğik çizgi yok. */
  def adres(taban: String, yon: Option[String]): String =
    taban + "/cevir" + yon.fold("")("?yon=" + _) // yön yalnız "tr2en" / "en2tr": kaçışa gerek yok

  // Alanlar dönüştürülmeden ÖNCE türüne bakılıyor. `undefined.asInstanceOf[String]`
  // Scala.js'te sürüme ve kipe göre ya UndefinedBehaviorError atıyor (ölümcül
  // sayılıyor: Try/NonFatal yakalamıyor) ya da hiç atmayıp undefined değeri
  // sessizce taşıyor (0.6 fullOpt). İkisi de "hiçbir şey olmadı" demek.
  private def bozuk(ne: String): Nothing = throw new IllegalArgumentException(s"yanıtta beklenen alan yok: $ne")

  private def metin(d: js.Dynamic, ad: String): String = {
    val v = d.selectDynamic(ad)
    if (js.typeOf(v) == "string") v.asInstanceOf[String] else bozuk(ad)
  }

  private def sayi(d: js.Dynamic, ad: String): Int = {
    val v = d.selectDynamic(ad)
    if (js.typeOf(v) == "number") v.asInstanceOf[Double].toInt else bozuk(ad)
  }

  private def nesne(d: js.Dynamic, ad: String): js.Dynamic = {
    val v = d.selectDynamic(ad)
    if (js.typeOf(v) == "object" && v != null) v else bozuk(ad)
  }

  private def dizi(d: js.Dynamic, ad: String): Seq[js.Dynamic] = {
    val v = d.selectDynamic(ad)
    if (js.Array.isArray(v)) v.asInstanceOf[js.Array[js.Dynamic]].toSeq else bozuk(ad)
  }

  private def metinler(d: js.Dynamic, ad: String): Seq[String] =
    dizi(d, ad).map(x => if (js.typeOf(x) == "string") x.asInstanceOf[String] else bozuk(ad))

  /** Yanıt gövdesini okur. Biçim bozuksa istisna atar (`yanit` onu iletiye çeviriyor). */
  def oku(json: String): Sonuc = {
    val d = js.JSON.parse(json).asInstanceOf[js.Dynamic]
    if (js.typeOf(d) != "object" || d == null) bozuk("(nesne)")
    val r = nesne(d, "rapor")
    Sonuc(
      yon = metin(d, "yon"),
      kod = metin(d, "kod"),
      cevrilen = sayi(r, "cevrilen"),
      kalanlar = dizi(r, "kalanlar").map(k => (metin(k, "ad"), sayi(k, "sayi"))),
      belirsiz = dizi(r, "belirsiz").map { b =>
        Belirsiz(metin(b, "ad"), metin(b, "secilen"), metinler(b, "digerleri"), sayi(b, "satir"))
      },
      kalanAnahtarSozcukler = metinler(d, "kalanAnahtarSozcukler")
    )
  }

  def yonAdi(yon: String): String = yon match {
    case "tr2en" => "Türkçeden İngilizceye (Koco → Kojo)"
    case "en2tr" => "İngilizceden Türkçeye (Kojo → Koco)"
    case baska   => baska
  }

  /**
   * Kullanıcıya gösterilen düz metin rapor (HTML kaçışı çağıranın işi).
   *
   * `degisti` false ise çeviri betiği hiç değiştirmedi: sıklıkla yön yanlış
   * bulunmuştur (Türkçe anahtar sözcüğü olmayan Türkçe betik "İngilizce" sanılır),
   * o yüzden menüden yönü elle seçmek öneriliyor.
   */
  def rapor(s: Sonuc, degisti: Boolean): String = {
    val satirlar = Seq.newBuilder[String]
    satirlar += s"${yonAdi(s.yon)} çevrildi: ${s.cevrilen} ad."
    if (!degisti)
      satirlar += "Betik değişmedi. Yön yanlış bulunmuş olabilir: Çevir menüsünden yönü elle seçin."
    if (s.kalanlar.nonEmpty)
      satirlar += "Çevrilmeden kalan adlar (kendi adlarınız ya da sözlükte olmayanlar): " +
        s.kalanlar.map { case (ad, sayi) => s"$ad ($sayi)" }.mkString(", ")
    if (s.belirsiz.nonEmpty) {
      satirlar += "Birden çok karşılığı olan adlar (en sık görüleniyle çevrildi):"
      s.belirsiz.foreach { b =>
        val digerleri = if (b.digerleri.isEmpty) "" else s" (öbür seçenekler: ${b.digerleri.mkString(", ")})"
        satirlar += s"  satır ${b.satir}: ${b.ad} → ${b.secilen}$digerleri"
      }
    }
    if (s.kalanAnahtarSozcukler.nonEmpty)
      satirlar += "Çıktıda hâlâ kaynak dilin anahtar sözcüğü var: " + s.kalanAnahtarSozcukler.mkString(", ")
    satirlar += "Geri almak için Ctrl+Z."
    satirlar.result().mkString("\n")
  }

  /** Sunucudan ya da ağdan gelen hataların kullanıcıya gösterilen Türkçe metni. */
  def hataMetni(durum: Int, govde: String): String = durum match {
    // 400: yönlendiricinin kendi iletisi ("geçersiz yön: ..."), zaten Türkçe.
    case 400 if govde.trim.nonEmpty => govde.trim
    // 413: nginx ya da router gövde sınırı (64 KB).
    case 413 => "Betik çeviri için çok büyük (en çok 64 KB)."
    // 429: nginx hız sınırı (koco-deploy: tüm istemciler için birlikte 10 istek/sn).
    case 429 => "Çeviri sunucusu şu anda çok yoğun. Birkaç saniye sonra yine deneyin."
    // 0: yanıt yok (sunucu kapalı, ağ kesik ya da istek engellendi).
    case 0 => "Sunucuya ulaşılamadı. Bağlantınızı denetleyip yine deneyin."
    case n => s"Çeviri yapılamadı (HTTP $n)."
  }

  /**
   * Bir HTTP yanıtını sonuca ya da kullanıcıya gösterilecek hataya çevirir.
   * Ağdan bağımsız, saf: bütün karar mantığı burada ve CevirTest'te sınanıyor.
   *
   * Ayrıştırma hatası HER TÜRLÜ istisnayı yakalıyor (JS TypeError/SyntaxError,
   * ClassCastException ...): hangisinin atıldığı Scala.js sürümüne ve derleme
   * kipine göre değişiyor, ve sessizce yutulan bir hata kullanıcıya "hiçbir şey
   * olmadı" olarak görünür.
   */
  def yanit(durum: Int, govde: String): Either[String, Sonuc] =
    if (durum >= 200 && durum < 300)
      Try(oku(govde)).toEither.left.map(_ => "Çeviri sunucusundan beklenmeyen bir yanıt geldi.")
    else
      Left(hataMetni(durum, govde))

  /** Betiği çevirtir. `Left` kullanıcıya gösterilecek hata iletisi. */
  def cevir(taban: String, kod: String, yon: Option[String])(implicit ec: ExecutionContext): Future[Either[String, Sonuc]] =
    dom.ext.Ajax
      .post(url = adres(taban, yon), data = kod)
      .map(istek => yanit(istek.status, istek.responseText))
      .recover {
        // Ajax 2xx dışındaki her durumu istisna olarak veriyor (status 0 = yanıt yok).
        case e: dom.ext.AjaxException => yanit(e.xhr.status, e.xhr.responseText)
        case _                        => Left(hataMetni(0, ""))
      }

  /**
   * Çeviriyi editöre yazar, GERİ ALINABİLİR biçimde.
   *
   * `session.setValue` Ace'in geri alma geçmişini SİLİYOR; Ctrl+Z hiçbir şey
   * yapmıyordu. `session.getDocument().setValue` ise silme + ekleme olarak
   * belgeye yazıyor ve geçmişe giriyor: tek Ctrl+Z çeviri öncesi metne döner,
   * ikincisi önceki kullanıcı düzenlemesine gider. Ace 1.2.4'te başsız
   * Chromium'da ölçüldü (docs/ace-geri-al-olcumu.js): session.setValue geçmişi
   * siliyor; doc.setValue, editor.setValue(t, -1) ve session.replace korunuyor.
   */
  def yaz(editor: js.Dynamic, kod: String): Unit = {
    editor.getSession().getDocument().setValue(kod)
    editor.clearSelection()
    editor.moveCursorTo(0, 0)
  }
}
