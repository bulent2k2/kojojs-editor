package scalafiddle.server

import javax.inject.Inject
import play.api.http.DefaultHttpFilters
import play.filters.cors.CORSFilter
import play.filters.gzip.GzipFilter

// SameSiteFilter en içte: eylemin koyduğu çerezleri doğrudan görsün (#47).
class Filters @Inject()(corsFilter: CORSFilter, gzipFilter: GzipFilter, sameSiteFilter: SameSiteFilter)
    extends DefaultHttpFilters(corsFilter, gzipFilter, sameSiteFilter)
