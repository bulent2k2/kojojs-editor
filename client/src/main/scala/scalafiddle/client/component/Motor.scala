package scalafiddle.client.component

import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.js.Dynamic.global
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.{Dynamic => Dyn}
import scalafiddle.client.{JsVal, ScalaFiddleConfig}

/**
 * Kod düzenleyici motoru: FiddleEditor'ün Ace'ten kullandığı yüzey (kojojs-editor#75).
 *
 * İki uygulama: `AceMotor` (bugünkü kod, davranışı değişmeden buraya taşındı) ve `CmMotor`
 * (CodeMirror 6; `window.KocoMotor` cephesine ince çağrılar, motor-cm/cephe.js). Seçim
 * adresle, bir kez: `?motor=cm` tarayıcıda (localStorage) kalır, `?motor=ace` geri alır
 * (`bayrağıOku`). Öntanımlı Ace; 3. dilimde çevrilecek.
 *
 * Satır ve sütun 0 tabanlı, Ace gibi. `setValue` geri alma geçmişini sıfırlar (Ace'in
 * `session.setValue`su gibi: şablon açma/kapama, betik yükleme); `yazGeriAlinabilir` tek
 * Ctrl+Z ile dönülebilir yazar (Çevir).
 */
trait Motor {
  def getValue: String
  def setValue(metin: String): Unit
  def yazGeriAlinabilir(metin: String): Unit
  def onInput(cb: () => Unit): Unit
  def focus(): Unit
  def getCursorPosition: (Int, Int)
  def moveCursorTo(row: Int, col: Int): Unit
  def setAnnotations(tanılar: Seq[Motor.Tanı]): Unit
  def clearAnnotations(): Unit
  def setCompleter(f: Motor.Tamamlayıcı): Unit
  def complete(): Unit
}

object Motor {
  /** Derleyici iletisi: Ace'in annotation'ı ile aynı alanlar; `tür` "error" | "warning" | "info". */
  case class Tanı(row: Int, col: Int, text: String, tür: String)

  /** (row, col, önek, geriÇağır(adaylar)) -- adaylar sunucudan (imza, ad) çiftleri, AutoCompleteFiddle'ın verdiği gibi. */
  type Tamamlayıcı = (Int, Int, String, Seq[(String, String)] => Unit) => Unit

  val Ace = "ace"
  val Cm  = "cm"

  private val motorParam = """[?&]motor=(cm|ace)(?:&|$)""".r
  private val Anahtar    = "koco.motor"
  private var seçim: Option[String] = None

  /**
   * Bayrağı adresten okur ve tarayıcıda saklar; AppMain.main'in İLK işi olmalı. Yönlendirici
   * (AppRouter) `?motor=cm`i tanımadığından adresi `/`a çeviriyor (notFound -> Redirect.Replace);
   * FiddleEditor mount olduğunda sorgu çoktan silinmiş oluyor. İlk sürüm `seçili`de
   * `location.search`e baktığı için hep Ace açıyordu -- sahibi ölçtü ("?motor=cm sayfa yüklenince
   * siliniyor"). Saklama localStorage'da: bayrak bir kez verilir, kaydet / yeniden yükle /
   * `/sf/...`ya geçişte kalır; `?motor=ace` geri alır. #75'in bir haftalık kullanım kapısı
   * ancak böyle ölçülebilir. localStorage kapalıysa (gizli pencere vb.) yalnız o yükleme için geçerli.
   */
  def bayrağıOku(): Unit = {
    val adresten = motorParam.findFirstMatchIn(dom.window.location.search).map(_.group(1))
    adresten.foreach(m => try dom.window.localStorage.setItem(Anahtar, m) catch { case _: Throwable => () })
    val saklı = try Option(dom.window.localStorage.getItem(Anahtar)) catch { case _: Throwable => None }
    seçim = adresten.orElse(saklı).filter(m => m == Cm || m == Ace)
  }

  /** Seçili motor: `bayrağıOku` ne bulduysa; bulamadıysa (ya da hiç çağrılmadıysa) Ace. */
  def seçili: String = seçim.getOrElse(Ace)

  /**
   * Motoru kurar ve hazır olunca `hazır`ı çağırır. Ace: eşzamanlı. CodeMirror: paket
   * (motor-cm.js, 123 KB gzip) yalnız bayrakla ve ancak o zaman yüklenir; Ace kullanıcısına
   * binmez. Yüklenemezse Ace ile sürülür ve konsola yazılır: sayfa boş kalmasın.
   */
  def kur(el: dom.raw.HTMLElement)(hazır: Motor => Unit): Unit = {
    // Hangi motorun kurulduğu dışarıdan okunabilsin: sınamada
    // document.getElementById("editor").dataset.motor ("cm" | "ace") ve konsolda bir satır.
    def bitti(m: Motor, ad: String): Unit = {
      el.setAttribute("data-motor", ad)
      dom.console.info("Koco motor: " + ad)
      hazır(m)
    }
    seçili match {
      case Cm =>
        val s = dom.document.createElement("script").asInstanceOf[dom.raw.HTMLScriptElement]
        s.src = ScalaFiddleConfig.motorURL
        s.onload = (_: dom.Event) => bitti(new CmMotor(global.KocoMotor.ac(el)), Cm)
        // scala-js-dom 0.9'un HTMLScriptElement'inde onerror yok; addEventListener ile.
        s.addEventListener("error", (_: dom.Event) => {
          dom.console.warn("motor-cm.js yüklenemedi (" + ScalaFiddleConfig.motorURL + "); Ace ile sürülüyor")
          bitti(new AceMotor(el), Ace)
        })
        dom.document.head.appendChild(s)
      case _ => bitti(new AceMotor(el), Ace)
    }
  }
}

/** Ace 1.2.4 (bugünkü motor). Kurulum, Ctrl-Space ve tamamlayıcı FiddleEditor.mounted'dan olduğu gibi taşındı. */
class AceMotor(el: dom.raw.HTMLElement) extends Motor {
  import JsVal.jsVal2jsAny

  private var tamamlayıcı: Option[Motor.Tamamlayıcı] = None

  private val Autocomplete = global.require("ace/autocomplete").Autocomplete
  private val completer    = Dyn.newInstance(Autocomplete)()
  private val editor: Dyn  = global.ace.edit(el)

  editor.setTheme("ace/theme/eclipse")
  editor.getSession().setMode("ace/mode/scala")
  editor.getSession().setTabSize(2)
  editor.setShowPrintMargin(false)
  editor.getSession().setOption("useWorker", false)
  editor.updateDynamic("completer")(completer) // because of SI-7420
  editor.updateDynamic("$blockScrolling")(Double.PositiveInfinity)
  editor.setFontSize("16px") // default is 14

  {
    val binding = "Ctrl-Space|Cmd-Space"
    val exec: js.Function0[Unit] = () => complete()
    editor.commands.addCommand(
      JsVal
        .obj(
          "name" -> "Complete",
          "bindKey" -> JsVal.obj(
            "win"    -> binding,
            "mac"    -> binding,
            "sender" -> "editor|cli"
          ),
          "exec" -> exec
        )
        .value
    )
  }

  // register auto complete
  editor.completers = js.Array(
    JsVal
      .obj(
        "getCompletions" -> { (editor: Dyn, session: Dyn, pos: Dyn, prefix: Dyn, callback: Dyn) =>
          {
            def applyResults(results: Seq[(String, String)]): Unit = {
              def params(signature: String): String = {
                val parts = signature.split(Array('(', ')'))
                if (parts.length > 2 && parts(0).length == 0) {
                  val paramStr = parts(1)
                  val params = paramStr.split(',')
                  val pnames = params.map { p =>
                    p.split(':')(0)
                  }
                  pnames.mkString("(", ", ", ")")
                }
                else {
                  ""
                }
              }

              val aceVersion = results.map {
                case (name, value) =>
                  val completionParams = params(name)
                  JsVal
                    .obj(
                      "value" -> (value + completionParams),
                      "caption" -> (value + name),
                      "completer" -> JsVal.obj(
                        "insertMatch" -> { (editor: Dyn, data: Dyn) =>
                          val text = data.value.asInstanceOf[String]
                          editor.removeWordLeft()
                          val completionStartPos = editor.getCursorPosition()
                          editor.session.insert(completionStartPos, text)
                          val completionStartCol = completionStartPos.column.asInstanceOf[Int]
                          val bracketOpenIndex = text.indexOf('(')
                          val bracketCloseIndex = text.indexOf(')')
                          val delta = if (bracketOpenIndex != -1) {
                            if (bracketCloseIndex == bracketOpenIndex + 1) bracketOpenIndex + 2 else bracketOpenIndex + 1
                          }
                          else {
                            text.length
                          }
                          editor.moveCursorTo(completionStartPos.row, completionStartCol + delta)
                          val paramsStrLen = completionParams.length
                          if (paramsStrLen != 0 && paramsStrLen != 2) {
                            // avoid no params and empty brackets
                            editor.getSelection().selectWordRight()
                          }
                        }
                      ).value
                    )
                    .value
              }
              callback(null, js.Array(aceVersion: _*))
            }
            tamamlayıcı match {
              case Some(f) => f(pos.row.asInstanceOf[Int], pos.column.asInstanceOf[Int], prefix.asInstanceOf[String], applyResults)
              case None    => callback(null, js.Array())
            }
          }
        }
      )
      .value
  )

  def getValue: String             = editor.getSession().getValue().asInstanceOf[String]
  def setValue(metin: String): Unit = editor.getSession().setValue(metin)

  /**
   * `session.setValue` Ace'in geri alma geçmişini SİLİYOR; Ctrl+Z hiçbir şey yapmıyordu.
   * `session.getDocument().setValue` ise silme + ekleme olarak belgeye yazıyor ve geçmişe
   * giriyor: tek Ctrl+Z çeviri öncesi metne döner, ikincisi önceki kullanıcı düzenlemesine
   * gider. Ace 1.2.4'te başsız Chromium'da ölçüldü (docs/ace-geri-al-olcumu.js):
   * session.setValue geçmişi siliyor; doc.setValue, editor.setValue(t, -1) ve
   * session.replace korunuyor.
   */
  def yazGeriAlinabilir(metin: String): Unit = {
    editor.getSession().getDocument().setValue(metin)
    editor.clearSelection()
    editor.moveCursorTo(0, 0)
  }

  def onInput(cb: () => Unit): Unit = {
    val f: js.Function0[Unit] = () => cb()
    editor.on("input", f)
  }
  def focus(): Unit = editor.focus()

  def getCursorPosition: (Int, Int) = {
    val p = editor.getCursorPosition()
    (p.row.asInstanceOf[Int], p.column.asInstanceOf[Int])
  }
  def moveCursorTo(row: Int, col: Int): Unit = editor.moveCursorTo(row, col)

  def setAnnotations(tanılar: Seq[Motor.Tanı]): Unit = {
    val aceAnnotations = tanılar.map { t =>
      JsVal
        .obj(
          "row"  -> t.row,
          "col"  -> t.col,
          "text" -> t.text,
          "type" -> t.tür
        )
        .value
    }.toJSArray
    editor.getSession().setAnnotations(aceAnnotations)
  }
  def clearAnnotations(): Unit = editor.getSession().clearAnnotations()

  def setCompleter(f: Motor.Tamamlayıcı): Unit = tamamlayıcı = Some(f)
  def complete(): Unit = {
    editor.completer.showPopup(editor)
    // needed for firefox on mac
    editor.completer.cancelContextMenu()
  }
}

/** CodeMirror 6: `window.KocoMotor.ac(el)`'in döndürdüğü cephe (motor-cm/cephe.js). Her ad oradakiyle aynı. */
class CmMotor(m: Dyn) extends Motor {
  def getValue: String                          = m.getValue().asInstanceOf[String]
  def setValue(metin: String): Unit             = m.setValue(metin)
  def yazGeriAlinabilir(metin: String): Unit    = m.yazGeriAlinabilir(metin)
  def onInput(cb: () => Unit): Unit = {
    val f: js.Function0[Unit] = () => cb()
    m.onInput(f)
  }
  def focus(): Unit = m.focus()

  def getCursorPosition: (Int, Int) = {
    val p = m.getCursorPosition()
    (p.row.asInstanceOf[Int], p.column.asInstanceOf[Int])
  }
  def moveCursorTo(row: Int, col: Int): Unit = m.moveCursorTo(row, col)

  def setAnnotations(tanılar: Seq[Motor.Tanı]): Unit =
    m.setAnnotations(tanılar.map(t => JsVal.obj("row" -> t.row, "col" -> t.col, "text" -> t.text, "type" -> t.tür).value).toJSArray)
  def clearAnnotations(): Unit = m.clearAnnotations()

  def setCompleter(f: Motor.Tamamlayıcı): Unit = {
    // Cephe: setCompleter((row, col, önek, geriÇağır) => ...); geriÇağır [{name, value}] dizisi bekler.
    val g: js.Function4[Int, Int, String, js.Function1[js.Array[js.Any], Unit], Unit] =
      (row: Int, col: Int, önek: String, geriÇağır: js.Function1[js.Array[js.Any], Unit]) =>
        f(row, col, önek, sonuçlar => geriÇağır(sonuçlar.map { case (imza, ad) => JsVal.obj("name" -> imza, "value" -> ad).value: js.Any }.toJSArray))
    m.setCompleter(g)
  }
  def complete(): Unit = m.complete()
}
