package com.browserx.app

import android.net.Uri

object AdBlockEngine {
    private val blockedHosts = hashSetOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "google-analytics.com",
        "googletagmanager.com",
        "googletagservices.com",
        "adservice.google.com",
        "pagead2.googlesyndication.com",
        "facebook.net",
        "connect.facebook.net",
        "analytics.twitter.com",
        "ads-twitter.com",
        "adnxs.com",
        "adsrvr.org",
        "amazon-adsystem.com",
        "criteo.com",
        "criteo.net",
        "outbrain.com",
        "taboola.com",
        "scorecardresearch.com",
        "quantserve.com",
        "rubiconproject.com",
        "pubmatic.com",
        "openx.net",
        "casalemedia.com",
        "appnexus.com",
        "moatads.com",
        "chartbeat.com",
        "hotjar.com",
        "mixpanel.com",
        "segment.io",
        "segment.com",
        "amplitude.com",
        "bugsnag.com",
        "sentry.io",
        "newrelic.com",
        "nr-data.net",
        "demdex.net",
        "omtrdc.net",
        "2o7.net",
        "krxd.net",
        "bluekai.com",
        "exelator.com",
        "turn.com",
        "mathtag.com",
        "mediamath.com",
        "rlcdn.com",
        "tapad.com",
        "sharethrough.com",
        "triplelift.com",
        "indexexchange.com",
        "advertising.com",
        "yieldmo.com",
        "bidswitch.net",
        "spotxchange.com",
        "smartadserver.com",
        "adform.net",
        "zedo.com",
        "revcontent.com",
        "mgid.com",
        "popads.net",
        "popcash.net",
        "propellerads.com",
        "adsterra.com"
    )

    fun matchedBlockedDomain(url: String): String? {
        val host = try {
            Uri.parse(url).host?.lowercase() ?: return null
        } catch (_: Exception) {
            return null
        }
        var current = host
        while (current.isNotEmpty()) {
            if (blockedHosts.contains(current)) return current
            val dot = current.indexOf('.')
            if (dot == -1) break
            current = current.substring(dot + 1)
        }
        return null
    }

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
            return { title: title, byline: byline, content: out.join('\n\n').slice(0, 120000) };
        })();
    """
}
