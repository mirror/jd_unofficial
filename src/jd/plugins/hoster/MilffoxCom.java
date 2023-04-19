//jDownloader - Downloadmanager
//Copyright (C) 2013  JD-Team support@jdownloader.org
//
//This program is free software: you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation, either version 3 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program.  If not, see <http://www.gnu.org/licenses/>.
package jd.plugins.hoster;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import jd.PluginWrapper;
import jd.http.Browser;
import jd.parser.Regex;
import jd.parser.html.HTMLSearch;
import jd.plugins.DownloadLink;
import jd.plugins.HostPlugin;
import jd.plugins.PluginException;

@HostPlugin(revision = "$Revision$", interfaceVersion = 3, names = {}, urls = {})
public class MilffoxCom extends KernelVideoSharingComV2 {
    public MilffoxCom(final PluginWrapper wrapper) {
        super(wrapper);
    }

    /** Add all KVS hosts to this list that fit the main template without the need of ANY changes to this class. */
    public static List<String[]> getPluginDomains() {
        final List<String[]> ret = new ArrayList<String[]>();
        // each entry in List<String[]> will result in one PluginForHost, Plugin.getHost() will return String[0]->main domain
        ret.add(new String[] { "milffox.com" });
        return ret;
    }

    public static String[] getAnnotationNames() {
        return buildAnnotationNames(getPluginDomains());
    }

    @Override
    public String[] siteSupportedNames() {
        return buildSupportedNames(getPluginDomains());
    }

    public static String[] getAnnotationUrls() {
        final List<String> ret = new ArrayList<String>();
        for (final String[] domains : getPluginDomains()) {
            ret.add("https?://(?:www\\.)?" + buildHostsPatternPart(domains) + "/porn-movies/([A-Za-z0-9\\-]+)/");
        }
        return ret.toArray(new String[0]);
    }

    @Override
    public String getFUID(final DownloadLink link) {
        return getURLTitle(link.getPluginPatternMatcher());
    }

    @Override
    protected String getURLTitle(final String url) {
        return new Regex(url, this.getSupportedLinks()).getMatch(0);
    }

    @Override
    protected String regexNormalTitleWebsite(final Browser br) {
        String title = HTMLSearch.searchMetaTag(br, "og:title");
        if (title != null) {
            return title;
        } else {
            /* Fallback to upper handling */
            return super.regexNormalTitleWebsite(br);
        }
    }

    @Override
    protected String generateContentURL(final String host, final String fuid, final String urlSlug) {
        return this.getProtocol() + this.appendWWWIfRequired(host) + "/porn-movies/" + fuid + "/";
    }

    @Override
    protected String getDllink(final DownloadLink link, final Browser br) throws PluginException, IOException {
        final String maybeHD_Directurl = br.getRegex("video_alt_url\\s*:\\s*'(https?://[^<>\"\\']+)'").getMatch(0);
        if (maybeHD_Directurl != null && br.containsHTML("video_alt_url_hd\\s*:\\s*'1'")) {
            return maybeHD_Directurl;
        } else {
            return super.getDllink(link, br);
        }
    }
}