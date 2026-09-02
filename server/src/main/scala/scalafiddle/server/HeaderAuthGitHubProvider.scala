package scalafiddle.server

// ExecutionContext SocialProvider trait'inden geliyor; ayrıca import etmek
// "ambiguous implicit values" hatası veriyor.
import scala.concurrent.Future

import com.mohiva.play.silhouette.api.util.HTTPLayer
import com.mohiva.play.silhouette.impl.exceptions.ProfileRetrievalException
import com.mohiva.play.silhouette.impl.providers.OAuth2Info
import com.mohiva.play.silhouette.impl.providers.OAuth2Settings
import com.mohiva.play.silhouette.impl.providers.SocialStateHandler
import com.mohiva.play.silhouette.impl.providers.oauth2.GitHubProvider

/**
 * Silhouette 5.0.1 (2017) fetches the GitHub profile with the token in the query
 * string:
 *
 *   val API = "https://api.github.com/user?access_token=%s"
 *   httpLayer.url(urls("api").format(authInfo.accessToken)).get()
 *
 * GitHub deprecated that in 2020 and removed it in 2021; the token must now be
 * sent in an Authorization header. Without this override the OAuth dance
 * completes -- GitHub redirects back with a valid code and Silhouette exchanges
 * it for an access token -- and then the very last step fails with:
 *
 *   ProfileRetrievalException: [Silhouette][github] Error retrieving profile
 *   information. Error message: Requires authentication
 *
 * which surfaces to the user as "login silently did nothing".
 *
 * Overriding `buildProfile` is the smallest fix. Upgrading Silhouette is not an
 * option here: 5.0.x is the Play 2.6 line, and 6.x/7.x need Play 2.7+.
 */
class HeaderAuthGitHubProvider(
    protected val layer: HTTPLayer,
    protected val handler: SocialStateHandler,
    val oauthSettings: OAuth2Settings
) extends GitHubProvider(layer, handler, oauthSettings) {

  override protected def buildProfile(authInfo: OAuth2Info): Future[Profile] = {
    httpLayer
      .url("https://api.github.com/user")
      .withHttpHeaders(
        "Authorization" -> s"token ${authInfo.accessToken}",
        "Accept" -> "application/vnd.github+json"
      )
      .get()
      .flatMap { response =>
        val json = response.json
        (json \ "message").asOpt[String] match {
          case Some(msg) =>
            val docURL = (json \ "documentation_url").asOpt[String]
            throw new ProfileRetrievalException(
              GitHubProvider.SpecifiedProfileError.format(id, msg, docURL)
            )
          case _ => profileParser.parse(json, authInfo)
        }
      }
  }

  // Keep the override alive when Silhouette rebuilds the provider with new settings.
  override def withSettings(f: Settings => Settings) =
    new HeaderAuthGitHubProvider(layer, handler, f(oauthSettings))
}
