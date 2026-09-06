package koco

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import scala.collection.mutable
import scala.util.Try
import scala.util.matching.Regex

/**
  * `/ornek/<yol>` rotasının arkasındaki saf mantık: kojojs-dev/ornekler dizinindeki bir
  * `.kojo` betiğini okur, masaüstü Koco'nun `// #yükle` (İngilizce Kojo'da `// #include`)
  * satırlarını genişletir ve gövdeyi ScalaFiddle sarmalayıcısına yerleştirir.
  *
  * Genişletme masaüstü Kojo'nun `Utils.preProcessInclude` davranışını izler:
  *  - içe alınan dosyaların içeriği, betiğin BAŞINA (kendi aralarında görülme sırasıyla,
  *    iç içe alınanlar önce) eklenir; `#yükle` satırı yerinde kalır ama `#Yükle` diye
  *    işaretlenir. Kojo'da da böyle: `kojo-documentation.kojo` 284. satırdaki `#yükle`nin
  *    tanımlarını daha üstte kullanıyor, yerinde açsak `null` değerlerle karşılaşırdık.
  *  - `/samples/tr/x` gibi MUTLAK yollar Kojo'da classpath kaynağıdır; burada
  *    `<kök>/masaustu/src/main/resources` altına göre çözülür.
  *  - Göreli yollar (`tr/anaTanimlar`) Kojo'da "son açılan dosyanın dizinine" göre çözülür;
  *    burada önce içe alan dosyanın dizini, sonra sırayla üst dizinleri (köke kadar) denenir.
  *    Böylece `othello/tr/otello.kojo` içindeki `tr/anaTanimlar` -> `othello/tr/anaTanimlar.kojo`.
  *  - Uzantısız ada `.kojo` eklenir; aynı dosya iki kez alınmaz (döngü koruması da bu).
  *  - `~/...` (ev dizini) tarayıcıda karşılıksız: içe alma yerine açıklayıcı yorum bırakılır.
  *  - Bulunamayan dosya için de sayfa 404 vermez, yorum satırıyla açıklanır.
  *
  * Denetleyici (controllers.Application) yalnız `genislet` ve `sar`ı çağırır; dosya sistemine
  * dokunan tek yer burasıdır, böylece birim testi yazılabilir (OrnekYukleyiciSpec).
  */
object OrnekYukleyici {

  /** Sunulan dosya türleri: kojo'nun betikleri ve installer'ın kurulumda ".kojo" olacak dosyaları */
  val uzantilar: Seq[String] = Seq(".kojo", ".kojo.installed")

  /** Kojo'nun mutlak (`/samples/tr/...`) yollarının kökü, örnek kökünün altında */
  val kaynakKoku: String = "masaustu/src/main/resources"

  // Kojo: """//\s*#yükle.*""" -- burada satır bazlı, hedef yakalanır. İngilizce Kojo dosyaları için #include de.
  private val yukleRE: Regex = """^\s*//\s*#(yükle|include)\s+(\S.*?)\s*$""".r

  // Editör istemcisiyle (FiddleEditor.extractCode) aynı işaretler
  private val fiddleStartRE: Regex = """\s*// \$FiddleStart\s*$""".r
  private val fiddleEndRE: Regex   = """\s*// \$FiddleEnd\s*$""".r

  /**
    * İstekten gelen yolu güvenle örnek kökünün altındaki bir dosyaya çevirir.
    * Reddedilenler: boş yol, mutlak yol, `~`, `..` parçası, ters bölü, kök dışına çıkan
    * (sembolik bağ dahil) yol, tanınmayan uzantı, olmayan/dosya olmayan hedef.
    */
  def yolCoz(kok: Path, yol0: String): Either[String, Path] = {
    val yol = Try(URLDecoder.decode(yol0, "UTF-8")).getOrElse(yol0)
    val parcalar = yol.split("/").toList
    if (yol.isEmpty) Left("boş yol")
    else if (yol.startsWith("/") || yol.startsWith("~")) Left(s"mutlak yol kabul edilmez: $yol")
    else if (yol.contains("\\") || yol.contains("\u0000")) Left(s"geçersiz karakter: $yol")
    else if (parcalar.contains("..")) Left(s"'..' kabul edilmez: $yol")
    else if (!uzantilar.exists(yol.endsWith)) Left(s"yalnız ${uzantilar.mkString(", ")} dosyaları sunulur: $yol")
    else {
      val kokN = kok.toAbsolutePath.normalize
      val dosya = kokN.resolve(yol).normalize
      if (!dosya.startsWith(kokN)) Left(s"kök dışına çıkıyor: $yol")
      else if (!Files.isRegularFile(dosya)) Left(s"dosya yok: $yol")
      else {
        // sembolik bağ kök dışına götürüyorsa da reddet
        val gercek = Try((dosya.toRealPath(), kokN.toRealPath()))
        gercek match {
          case scala.util.Success((d, k)) if d.startsWith(k) => Right(dosya)
          case scala.util.Success(_)                         => Left(s"kök dışına çıkıyor (sembolik bağ): $yol")
          case scala.util.Failure(e)                         => Left(s"yol çözülemedi: $yol (${e.getMessage})")
        }
      }
    }
  }

  /** Kök altındaki betiği okur ve `#yükle` satırlarını genişletir. Hata: Left(neden). */
  def genislet(kok: Path, yol: String): Either[String, String] = {
    val kokN = kok.toAbsolutePath.normalize
    yolCoz(kokN, yol).flatMap { dosya =>
      Try(oku(dosya)).toOption.toRight(s"okunamadı: $yol").map { icerik =>
        val alinanlar = mutable.HashSet[Path](dosya)
        genisletKod(kokN, dosya, icerik, alinanlar)
      }
    }
  }

  private def oku(dosya: Path): String =
    new String(Files.readAllBytes(dosya), StandardCharsets.UTF_8).replace("\r", "")

  /** Kojo'nun addKojoExtension'ı: dosya adında nokta yoksa `.kojo` ekle */
  private def kojoUzantisi(ad: String): String = {
    val salt = ad.substring(ad.lastIndexOf('/') + 1)
    if (salt.contains(".")) ad else ad + ".kojo"
  }

  /**
    * `#yükle` hedefini dosyaya çevirir. Mutlak (`/...`) hedef `<kök>/masaustu/src/main/resources`
    * (yedek: kökün kendisi) altında; göreli hedef içe alan dosyanın dizininde, sonra üst
    * dizinlerinde (köke kadar) aranır. Kök dışına çıkan aday yok sayılır.
    */
  private def hedefCoz(kok: Path, icAlan: Path, hedef0: String): Option[Path] = {
    val hedef = kojoUzantisi(hedef0)
    val adaylar: Seq[Path] =
      if (hedef.startsWith("/")) {
        val goreli = hedef.dropWhile(_ == '/')
        Seq(kok.resolve(kaynakKoku).resolve(goreli), kok.resolve(goreli))
      } else {
        // içe alan dosyanın dizini ve köke kadar üst dizinleri
        val dizinler = Iterator
          .iterate(icAlan.getParent)(_.getParent)
          .takeWhile(d => d != null && d.startsWith(kok))
          .toList
        dizinler.map(_.resolve(hedef))
      }
    adaylar.map(_.normalize).find(p => p.startsWith(kok) && Files.isRegularFile(p))
  }

  /**
    * Kojo'nun `_preProcessInclude`'u: içe alınanlar (özyineli genişletilmiş) başa, gövde sona.
    * `alinanlar` daha önce alınmış dosyaları tutar; aynı dosya ikinci kez alınmaz.
    */
  private def genisletKod(kok: Path, dosya: Path, kod: String, alinanlar: mutable.Set[Path]): String = {
    val eklenen = new StringBuilder
    val govde   = mutable.ArrayBuffer[String]()
    kod.split("\n", -1).foreach {
      case yukleRE(pragma, hedef) =>
        val isaret = pragma.capitalize // Kojo da işlenmiş satırı böyle işaretliyor: #Yükle
        if (hedef.startsWith("~")) {
          govde += s"// #$isaret $hedef -- ev dizini (~) tarayıcıda yok; bu dosya masaüstü Koco'ya özel, içe alınmadı"
        } else {
          hedefCoz(kok, dosya, hedef) match {
            case Some(p) if alinanlar.contains(p) =>
              govde += s"// #$isaret $hedef -- daha önce alındı"
            case Some(p) =>
              alinanlar += p
              val goreli = kok.relativize(p).toString.replace('\\', '/')
              val ic     = Try(oku(p)).toOption
              ic match {
                case Some(icerik) =>
                  val genis = genisletKod(kok, p, icerik, alinanlar)
                  eklenen ++= s"// --- #$pragma $hedef başı ($goreli) ---\n"
                  eklenen ++= genis
                  if (!genis.endsWith("\n")) eklenen ++= "\n"
                  eklenen ++= s"// --- #$pragma $hedef sonu ---\n"
                  govde += s"// #$isaret $hedef -- içeriği yukarıya alındı"
                case None =>
                  govde += s"// #$isaret $hedef -- $goreli okunamadı, içe alınmadı"
              }
            case None =>
              govde += s"// #$isaret $hedef -- dosya bulunamadı, içe alınmadı"
          }
        }
      case satir =>
        govde += satir
    }
    eklenen.toString + govde.mkString("\n")
  }

  /**
    * Betik gövdesini ScalaFiddle şablonuna (application.conf `defaultSource`) yerleştirir:
    * `// $FiddleStart` ile `// $FiddleEnd` arasındaki örnek satırların yerine gövde gelir.
    * İstemci (FiddleEditor.extractCode) bu işaretlere göre sarmalayıcıyı gizler ve gövdenin
    * en küçük girintisini soyar; gövde girintilenmez, `ornekleri-dogrula.sh` içindeki `sar()`
    * da 0. sütunda sarmalıyor. İşaretler yoksa gövde son `}` satırının önüne konur.
    */
  def sar(sablon: String, govde: String): String = {
    val satirlar = sablon.split("\n", -1).toList
    val govdeSatirlari =
      if (govde.trim.isEmpty) List("// (boş betik)") else govde.split("\n", -1).toList
    val bas = satirlar.indexWhere(s => fiddleStartRE.unapplySeq(s).isDefined)
    val son = satirlar.indexWhere(s => fiddleEndRE.unapplySeq(s).isDefined)
    if (bas >= 0 && son > bas) {
      (satirlar.take(bas + 1) ++ govdeSatirlari ++ satirlar.drop(son)).mkString("\n")
    } else {
      val kapanis = satirlar.lastIndexWhere(_.trim == "}")
      if (kapanis >= 0) (satirlar.take(kapanis) ++ govdeSatirlari ++ satirlar.drop(kapanis)).mkString("\n")
      else sablon + "\n" + govdeSatirlari.mkString("\n")
    }
  }
}
