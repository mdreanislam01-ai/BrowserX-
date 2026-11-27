package com.browserx.app

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Request blocking for the WebView.
 *
 * Two layers:
 *  1. a built-in list of ad / tracker / popup networks (always available offline), and
 *  2. an optional downloaded EasyList/EasyPrivacy host list (see [FilterLists]) cached on disk.
 */
object AdBlockEngine {

    private val builtInHosts: Set<String> = hashSetOf(
        // Google ads / analytics
        "doubleclick.net", "googleadservices.com", "googlesyndication.com", "google-analytics.com",
        "googletagmanager.com", "googletagservices.com", "adservice.google.com", "pagead2.googlesyndication.com",
        "adclick.g.doubleclick.net", "stats.g.doubleclick.net", "partner.googleadservices.com",
        "fundingchoicesmessages.google.com", "imasdk.googleapis.com", "2mdn.net", "serving-sys.com",
        "googlesyndication.com", "googleads.g.doubleclick.net", "www.googletagservices.com",
        // Meta / social
        "connect.facebook.net", "graph.facebook.com", "an.facebook.com", "ads-twitter.com",
        "analytics.twitter.com", "static.ads-twitter.com", "ads.linkedin.com", "snap.licdn.com",
        "px.ads.linkedin.com", "ads.pinterest.com", "ct.pinterest.com", "ads.tiktok.com",
        "analytics.tiktok.com", "business-api.tiktok.com", "ads.youtube.com",
        // Programmatic / SSP / DSP
        "adnxs.com", "ib.adnxs.com", "adsrvr.org", "amazon-adsystem.com", "criteo.com", "criteo.net",
        "outbrain.com", "taboola.com", "scorecardresearch.com", "quantserve.com", "quantcount.com",
        "rubiconproject.com", "pubmatic.com", "openx.net", "casalemedia.com", "appnexus.com",
        "moatads.com", "moat.com", "advertising.com", "yieldmo.com", "bidswitch.net", "spotxchange.com",
        "smartadserver.com", "adform.net", "zedo.com", "revcontent.com", "mgid.com", "popads.net",
        "popcash.net", "propellerads.com", "adsterra.com", "exoclick.com", "juicyads.com",
        "trafficjunky.net", "hilltopads.net", "clickadu.com", "onclickads.net", "popmyads.com",
        "adcash.com", "adspyglass.com", "bannersnack.com", "adroll.com", "adcolony.com",
        "adsafeprotected.com", "doubleverify.com", "ads.yahoo.com", "gemini.yahoo.com",
        "adtechus.com", "adtech.de", "adition.com", "adsystem.com", "adzerk.net", "adzerk.com",
        "indexww.com", "indexexchange.com", "triplelift.com", "sharethrough.com", "teads.tv",
        "sovrn.com", "lijit.com", "gumgum.com", "contextweb.com", "pulsepoint.com", "districtm.io",
        "1rx.io", "360yield.com", "adsymptotic.com", "agkn.com", "crwdcntrl.net", "mathtag.com",
        "mediamath.com", "turn.com", "tapad.com", "krxd.net", "bluekai.com", "exelator.com",
        "demdex.net", "omtrdc.net", "2o7.net", "everesttech.net", "rlcdn.com", "krxd.net",
        "bing.com/bat.js", "clarity.ms", "bat.bing.com", "c.bing.com",
        // Analytics / session replay / marketing
        "chartbeat.com", "hotjar.com", "hotjar.io", "mixpanel.com", "segment.io", "segment.com",
        "amplitude.com", "bugsnag.com", "sentry.io", "newrelic.com", "nr-data.net",
        "fullstory.com", "mouseflow.com", "inspectlet.com", "smartlook.com", "logrocket.io",
        "lr-ingest.io", "heapanalytics.com", "kissmetrics.com", "statcounter.com", "histats.com",
        "yandex.ru/metrika", "mc.yandex.ru", "metrika.yandex.ru", "top-fwz1.mail.ru",
        "counter.yadro.ru", "hits.wrating.com", "webtrendslive.com", "clicktale.net",
        "sessioncam.com", "quantummetric.com", "decibelinsight.com", "contentsquare.net",
        "cdn.mxpnl.com", "api.segment.io", "ssl.google-analytics.com", "www.google-analytics.com",
        // Ads on news sites / taboola-like widgets
        "zergnet.com", "plista.com", "dianomi.com", "nativo.com", "adsnative.com", "adsupply.com",
        "realsrv.com", "tsyndicate.com", "adnium.com", "trafficstars.com", "adskeeper.com",
        "media.net", "contextualyield.com", "ezmob.com", "adpushup.com", "ezoic.net",
        "go.ezoic.net", "ezoicads.com", "sonobi.com", "growplow.com",
        // Popups / redirects / push-notification spam
        "pushwoosh.com", "onesignal.com", "izooto.com", "cleverpush.com", "sendpulse.com",
        "pushnami.com", "pushengage.com", "subscribers.com", "webpushs.com", "notifyvisitors.com",
        "popupdomination.com", "popt.in", "popmyads.com", "adf.ly", "shorte.st", "ouo.io",
        // Misc trackers
        "branch.io", "appsflyer.com", "adjust.com", "kochava.com", "singular.net", "tenjin.io",
        "smartbanner.io", "onesignal.com", "optimizely.com", "vwo.com", "abtasty.com",
        "dynamicyield.com", "monetate.net", "crazyegg.com", "clicky.com", "getclicky.com"
    )

    @Volatile
    private var downloadedHosts: Set<String> = emptySet()

    /** Number of domains in the downloaded filter list, for the Settings screen. */
    @Volatile
    var downloadedHostCount: Int = 0
        private set

    private fun filterFile(context: Context): File = File(context.filesDir, "filter_hosts.txt")

    /** Loads the cached downloaded list (call once at startup). */
    fun loadDownloadedHosts(context: Context) {
        val file = filterFile(context)
        downloadedHosts = if (file.exists()) {
            runCatching {
                file.readLines()
                    .map { it.trim().lowercase() }
                    .filter { it.isNotEmpty() }
                    .toHashSet()
            }.getOrDefault(emptySet())
        } else {
            emptySet()
        }
        downloadedHostCount = downloadedHosts.size
    }

    /** Replaces the cached list with a freshly downloaded one. */
    fun saveDownloadedHosts(context: Context, hosts: Set<String>) {
        runCatching { filterFile(context).writeText(hosts.joinToString("\n")) }
        downloadedHosts = hosts
        downloadedHostCount = hosts.size
    }

    fun clearDownloadedHosts(context: Context) {
        runCatching { filterFile(context).delete() }
        downloadedHosts = emptySet()
        downloadedHostCount = 0
    }

    /**
     * Returns the blocked domain when [url] belongs to an ad / tracker host, else null.
     * Sub-domains of a blocked domain are blocked too.
     */
    fun matchedBlockedDomain(url: String, includeDownloaded: Boolean = true): String? {
        val host = try {
            Uri.parse(url).host?.lowercase() ?: return null
        } catch (_: Exception) {
            return null
        }
        if (host.isEmpty()) return null
        val downloaded = if (includeDownloaded) downloadedHosts else emptySet()
        var current = host
        while (current.isNotEmpty()) {
            if (builtInHosts.contains(current)) return current
            if (downloaded.isNotEmpty() && downloaded.contains(current)) return current
            val dot = current.indexOf('.')
            if (dot == -1) break
            current = current.substring(dot + 1)
        }
        return null
    }

    /** Total domains the engine can block (built-in + downloaded). */
    fun totalHostCount(): Int = builtInHosts.size + downloadedHostCount

    fun builtInHostCount(): Int = builtInHosts.size

    // ---------------------------------------------------------------------------------------
    // In-page cleanup: hides leftover ad slots, sticky ad bars and push-permission nag boxes.
    // ---------------------------------------------------------------------------------------
    const val AD_SLOT_HIDER_JS = """
        (function() {
            if (window.__browserxAdHider) return;
            window.__browserxAdHider = true;
            var selectors = [
                'ins.adsbygoogle', '.adsbygoogle', '[id^="google_ads_iframe"]', '[id^="div-gpt-ad"]',
                '[id*="div-gpt-ad"]', '[class*="gpt-ad"]', '[data-ad-slot]', '[data-ad-client]',
                'iframe[src*="doubleclick.net"]', 'iframe[src*="googlesyndication"]',
                'iframe[src*="adservice"]', 'iframe[src*="amazon-adsystem"]',
                '.advertisement', '.advertising', '.advert', '.ad-banner', '.ads-banner',
                '.ad-container', '.ad-wrapper', '.ad-slot', '.ad-unit', '.adsbox',
                '#ad-top', '#ad-bottom', '#ad-sidebar', '#banner-ad', '[id^="ad-"]',
                '[class^="ad-"]', '[class*=" ad-"]', '[class*="-ad "]', '[class*="_ad_"]',
                '.sponsored-content', '.sponsored-post', '.taboola', '#taboola-below-article',
                '.OUTBRAIN', '[class*="outbrain"]', '[id*="outbrain"]', '[class*="mgid"]',
                '[id*="push-notification"]', '[class*="push-notification"]',
                '[id*="onesignal"]', '#onesignal-slidedown-container'
            ];
            var css = selectors.join(',') + ' { display: none !important; visibility: hidden !important; }';
            var style = document.createElement('style');
            style.textContent = css;
            (document.head || document.documentElement).appendChild(style);

            function sweep() {
                try {
                    var frames = document.querySelectorAll('iframe');
                    for (var i = 0; i < frames.length; i++) {
                        var src = (frames[i].src || '').toLowerCase();
                        if (src.indexOf('doubleclick') !== -1 || src.indexOf('googlesyndication') !== -1 ||
                            src.indexOf('adservice') !== -1 || src.indexOf('amazon-adsystem') !== -1) {
                            frames[i].style.display = 'none';
                        }
                    }
                } catch (e) {}
            }
            sweep();
            setTimeout(sweep, 1200);
            setTimeout(sweep, 3500);
        })();
    """

    // ---------------------------------------------------------------------------------------
    // Page scripts
    // ---------------------------------------------------------------------------------------
    const val COOKIE_BANNER_HIDER_JS = """
        (function() {
            if (window.__browserxCookieHider) return;
            window.__browserxCookieHider = true;
            var style = document.createElement('style');
            style.textContent = [
                '#onetrust-banner-sdk', '#onetrust-consent-sdk',
                '.qc-cmp2-container', '#CybotCookiebotDialog',
                '.cc-window', '.cc-banner', '#cookie-law-info-bar',
                '[id*="cookie-banner"]', '[class*="cookie-banner"]',
                '[id*="cookie-consent"]', '[class*="cookie-consent"]',
                '[aria-label*="cookie" i][role="dialog"]',
                '.fc-consent-root'
            ].join(', ') + ' { display: none !important; visibility: hidden !important; pointer-events: none !important; }';
            (document.head || document.documentElement).appendChild(style);
        })();
    """

    const val READER_EXTRACTOR_JS = """
        (function() {
            var title = document.title || '';
            var h1 = document.querySelector('h1');
            if (h1 && h1.innerText.trim().length > 5) title = h1.innerText.trim();
            var bylineEl = document.querySelector('[rel="author"], .author, .byline, [itemprop="author"]');
            var byline = bylineEl ? bylineEl.innerText.trim() : '';
            var root = document.querySelector('article, main, [role="main"], .post-content, .article-content, .entry-content') || document.body;
            var blocks = root.querySelectorAll('p, h2, h3, blockquote, li');
            var out = [];
            for (var i = 0; i < blocks.length; i++) {
                var txt = (blocks[i].innerText || '').trim();
                if (txt.length > 25) {
                    out.push(txt);
                }
            }
            if (out.length === 0 && root.innerText) {
                out.push(root.innerText.trim().slice(0, 12000));
            }
            return JSON.stringify({ title: title, byline: byline, content: out.join('\n\n') });
        })();
    """
}
