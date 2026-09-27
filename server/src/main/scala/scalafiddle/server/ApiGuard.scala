package scalafiddle.server

import play.api.http.HeaderNames
import play.api.mvc.Headers

import scalafiddle.shared.ApiHeader

/**
  * Başka bir siteden, oturum açmış kullanıcının çereziyle atılan isteklere
  * karşı (CSRF, #47).
  */
object ApiGuard {

  /**
    * `/api` isteği kabul edilsin mi?
    *
    * - `ApiHeader` şart. Tarayıcıda başka bir sayfa özel başlık ancak ön-uçuşla
    *   ekleyebilir; `/api` CORS'a açık olmadığı için ön-uçuş reddediliyor. Bir
    *   form ya da `text/plain` POST bu başlığı taşıyamıyor.
    * - `Origin: null` (opak kökenli çerçeve: editörün kendi sonuç çerçevesi de
    *   dahil) açıkça reddediliyor; kullanıcı kodu API'ye hiç ulaşmasın.
    */
  def izinli(headers: Headers): Boolean =
    headers.get(ApiHeader.Name).contains(ApiHeader.Value) &&
      !headers.get(HeaderNames.ORIGIN).contains("null")

  /** Fiddle kimliği `Persistence.createId`'nin ürettiği biçimde mi (7 karakter, 0-9A-Za-z)? */
  def fiddleKimligiMi(id: String): Boolean = id.matches("[0-9A-Za-z]{7}")
}
