package scalafiddle.server

import javax.inject.Inject

import akka.stream.Materializer
import play.api.mvc.{Cookie, Filter, RequestHeader, Result}

import scala.concurrent.{ExecutionContext, Future}

/**
  * Yanıtın koyduğu çerezlere `SameSite=Lax` (#47).
  *
  * Silhouette 5.0.1'in çerez ayarlarında SameSite yok (`CookieAuthenticatorSettings`);
  * oturum (`authenticator`), OAuth durum ve OAuth1 çerezleri özniteliksiz gidiyordu
  * ve koruma tarayıcının varsayılanına kalıyordu. Silhouette çerezleri
  * `result.withCookies` ile koyuyor, yani hepsi `Result.newCookies`'te.
  *
  * `Lax`, `Strict` değil: GitHub girişinden dönüş üst düzey bir gezinme ve OAuth
  * durum çerezi ona gitmeli. Kendi SameSite'ı olan çereze (Play oturumu) dokunulmuyor.
  */
class SameSiteFilter @Inject()(implicit val mat: Materializer, ec: ExecutionContext) extends Filter {
  def apply(next: RequestHeader => Future[Result])(rh: RequestHeader): Future[Result] =
    next(rh).map(r => SameSiteFilter.laxYap(r))
}

object SameSiteFilter {
  def laxYap(r: Result): Result =
    if (r.newCookies.isEmpty) r
    else r.copy(newCookies = r.newCookies.map(laxCerez))

  def laxCerez(c: Cookie): Cookie =
    if (c.sameSite.isEmpty) c.copy(sameSite = Some(Cookie.SameSite.Lax)) else c
}
