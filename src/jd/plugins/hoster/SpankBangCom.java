//jDownloader - Downloadmanager
//Copyright (C) 2012  JD-Team support@jdownloader.org
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
import java.util.LinkedHashMap;
import java.util.List;

import org.appwork.utils.StringUtils;
import org.jdownloader.downloader.hls.HLSDownloader;
import org.jdownloader.plugins.components.antiDDoSForHost;

import jd.PluginWrapper;
import jd.config.ConfigContainer;
import jd.config.ConfigEntry;
import jd.http.Browser;
import jd.http.URLConnectionAdapter;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginDependencies;
import jd.plugins.PluginException;
import jd.plugins.components.UserAgents.BrowserName;
import jd.plugins.decrypter.SpankBangComCrawler;
import jd.utils.locale.JDL;

@HostPlugin(revision = "$Revision$", interfaceVersion = 2, names = {}, urls = {})
@PluginDependencies(dependencies = { SpankBangComCrawler.class })
public class SpankBangCom extends antiDDoSForHost {
    public SpankBangCom(PluginWrapper wrapper) {
        super(wrapper);
        this.setConfigElements();
    }

    public static List<String[]> getPluginDomains() {
        return SpankBangComCrawler.getPluginDomains();
    }

    public static String[] getAnnotationNames() {
        return buildAnnotationNames(getPluginDomains());
    }

    @Override
    public String[] siteSupportedNames() {
        return buildSupportedNames(getPluginDomains());
    }

    public static String[] getAnnotationUrls() {
        return buildAnnotationUrls(getPluginDomains());
    }

    public static String[] buildAnnotationUrls(final List<String[]> pluginDomains) {
        return new String[] { "http://spankbangdecrypted\\.com/\\d+" };
    }

    @Override
    public String getAGBLink() {
        return "http://spankbang.com/info#dmca";
    }

    /** Settings stuff */
    private final static String FASTLINKCHECK = "FASTLINKCHECK";
    private final static String ALLOW_BEST    = "ALLOW_BEST";
    private final static String ALLOW_240p    = "ALLOW_240p";
    private final static String ALLOW_320p    = "ALLOW_320p";
    private final static String ALLOW_480p    = "ALLOW_480p";
    private final static String ALLOW_720p    = "ALLOW_720p";
    private final static String ALLOW_1080p   = "ALLOW_1080p";
    private static final String ALLOW_4k      = "ALLOW_4k";
    private String              dllink        = null;
    private boolean             server_issues = false;

    @Override
    protected boolean useRUA() {
        return true;
    }

    @Override
    public void init() {
        super.init();
        /** 2021-07-27: Important else we'll run into Cloudflare Rate-Limit prohibition after about 250 requests! */
        Browser.setRequestIntervalLimitGlobal(getHost(), 3000);
    }

    @Override
    public String getMirrorID(DownloadLink link) {
        if (link != null && StringUtils.equals(getHost(), link.getHost())) {
            return link.getLinkID();
        } else {
            return super.getMirrorID(link);
        }
    }

    @Override
    protected BrowserName setBrowserName() {
        return BrowserName.Chrome;
    }

    @Override
    public void getPage(String page) throws Exception {
        super.getPage(page);
    }

    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws Exception {
        this.setBrowserExclusive();
        server_issues = false;
        br.setFollowRedirects(true);
        br.getHeaders().put("Accept-Language", "en-US,en;q=0.5");
        final String filename = link.getStringProperty("plain_filename", null);
        dllink = link.getStringProperty("plain_directlink", null);
        if (filename == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        link.setFinalFileName(filename);
        if (isValidURL(br, link, dllink)) {
            return AvailableStatus.TRUE;
        } else {
            final String mainlink = link.getStringProperty("mainlink", null);
            final String quality = link.getStringProperty("quality", null);
            if (mainlink == null || quality == null) {
                /* Missing property - this should not happen! */
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            getPage(mainlink);
            if (SpankBangComCrawler.isOffline(this.br)) {
                /* Main videolink offline --> Offline */
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            /* Main videolink online --> Refresh directlink ... */
            final LinkedHashMap<String, String> foundQualities = SpankBangComCrawler.findQualities(this.br, mainlink);
            if (foundQualities != null) {
                dllink = foundQualities.get(quality);
            }
            if (dllink != null) {
                if (isValidURL(br, link, dllink)) {
                    link.setProperty("plain_directlink", dllink);
                    return AvailableStatus.TRUE;
                } else {
                    /* Link is still online but our directlink does not work for whatever reason ... */
                    server_issues = true;
                }
            }
        }
        return AvailableStatus.UNCHECKED;
    }

    private boolean isValidURL(final Browser br, final DownloadLink link, final String url) throws IOException {
        if (StringUtils.isEmpty(url)) {
            return false;
        }
        final Browser brc = br.cloneBrowser();
        brc.setFollowRedirects(true);
        // this request isn't behind cloudflare.
        final URLConnectionAdapter con = brc.openHeadConnection(url);
        try {
            if (url.contains("m3u8") && con.getResponseCode() == 200) {
                return true;
            } else if (!url.contains("m3u8") && looksLikeDownloadableContent(con)) {
                if (con.getCompleteContentLength() > 0) {
                    link.setVerifiedFileSize(con.getCompleteContentLength());
                }
                return true;
            } else {
                return false;
            }
        } finally {
            con.disconnect();
        }
    }

    @Override
    public void handleFree(final DownloadLink link) throws Exception, PluginException {
        requestFileInformation(link);
        if (server_issues) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Unknown server error", 10 * 60 * 1000l);
        } else if (dllink == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        if (dllink.contains("m3u8")) {
            checkFFmpeg(link, "Download a HLS Stream");
            dl = new HLSDownloader(link, br, dllink);
            dl.startDownload();
        } else {
            dl = jd.plugins.BrowserAdapter.openDownload(br, link, dllink, true, 0);
            if (!looksLikeDownloadableContent(dl.getConnection())) {
                try {
                    br.followConnection(true);
                } catch (final IOException e) {
                    logger.log(e);
                }
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            dl.startDownload();
        }
    }

    @Override
    public String getDescription() {
        return "JDownloader's SpankBang Plugin helps downloading Videoclips from spankbang.com. SpankBang provides different video qualities.";
    }

    private void setConfigElements() {
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), FASTLINKCHECK, "Fast linkcheck (filesize won't be shown in linkgrabber)?").setDefaultValue(true));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_SEPARATOR));
        final ConfigEntry cfg = new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), ALLOW_BEST, JDL.L("plugins.hoster.SpankBangCom.ALLOW_BEST", "Always only grab best available resolution?")).setDefaultValue(true);
        getConfig().addEntry(cfg);
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), ALLOW_240p, JDL.L("plugins.hoster.SpankBangCom.ALLOW_240p", "Grab 240p?")).setDefaultValue(true));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), ALLOW_320p, JDL.L("plugins.hoster.SpankBangCom.ALLOW_320p", "Grab 320p?")).setDefaultValue(true));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), ALLOW_480p, JDL.L("plugins.hoster.SpankBangCom.ALLOW_480p", "Grab 480p?")).setDefaultValue(true));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), ALLOW_720p, JDL.L("plugins.hoster.SpankBangCom.ALLOW_720p", "Grab 720p?")).setDefaultValue(true));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), ALLOW_1080p, JDL.L("plugins.hoster.SpankBangCom.ALLOW_1080p", "Grab 1080p?")).setDefaultValue(true));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), ALLOW_4k, "Grab 4k?").setDefaultValue(true));
    }

    @Override
    public void reset() {
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return -1;
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }
}