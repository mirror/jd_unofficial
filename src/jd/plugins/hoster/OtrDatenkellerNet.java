//    jDownloader - Downloadmanager
//    Copyright (C) 2011  JD-Team support@jdownloader.org
//
//    This program is free software: you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation, either version 3 of the License, or
//    (at your option) any later version.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with this program.  If not, see <http://www.gnu.org/licenses/>.
package jd.plugins.hoster;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.appwork.storage.JSonMapperException;
import org.appwork.storage.TypeRef;
import org.appwork.storage.config.annotations.AboutConfig;
import org.appwork.storage.config.annotations.DefaultIntValue;
import org.appwork.storage.config.annotations.DescriptionForConfigEntry;
import org.appwork.utils.DebugMode;
import org.appwork.utils.StringUtils;
import org.appwork.utils.Time;
import org.appwork.utils.parser.UrlQuery;
import org.jdownloader.plugins.config.PluginConfigInterface;
import org.jdownloader.plugins.config.PluginJsonConfig;

import jd.PluginWrapper;
import jd.http.Browser;
import jd.http.Cookies;
import jd.http.RandomUserAgent;
import jd.nutils.JDHash;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.Account;
import jd.plugins.Account.AccountType;
import jd.plugins.AccountInfo;
import jd.plugins.AccountInvalidException;
import jd.plugins.AccountUnavailableException;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;

@HostPlugin(revision = "$Revision$", interfaceVersion = 3, names = {}, urls = {})
public class OtrDatenkellerNet extends PluginForHost {
    private static List<String[]> getPluginDomains() {
        final List<String[]> ret = new ArrayList<String[]>();
        // each entry in List<String[]> will result in one PluginForHost, Plugin.getHost() will return String[0]->main domain
        ret.add(new String[] { "otr.datenkeller.net", "otr.datenkeller.at" });
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
            ret.add("https?://" + buildHostsPatternPart(domains) + "/\\?(?:file|getFile)=.+");
        }
        return ret.toArray(new String[0]);
    }

    public static AtomicReference<String> agent = new AtomicReference<String>(null);

    private String getUserAgent() {
        while (true) {
            String agent = OtrDatenkellerNet.agent.get();
            if (agent == null) {
                OtrDatenkellerNet.agent.compareAndSet(null, RandomUserAgent.generate());
            } else {
                return agent;
            }
        }
    }

    private final String            API_BASE_URL                 = "https://otr.datenkeller.net/api.php";
    private final String            APIVERSION                   = "1";
    private static final AtomicLong timestampLastDownloadStarted = new AtomicLong(0);
    /* Don't touch the following! */
    private static AtomicInteger    freeRunning                  = new AtomicInteger(0);
    private static AtomicInteger    accountRunning               = new AtomicInteger(0);

    public static interface OtrDatenKellerInterface extends PluginConfigInterface {
        final String                    text_MaxWaitMinutesForTicket = "Max wait for ticket (minutes)";
        public static final TRANSLATION TRANSLATION                  = new TRANSLATION();

        public static class TRANSLATION {
            public String getMaxWaitMinutesForTicket_label() {
                return text_MaxWaitMinutesForTicket;
            }
        }

        @AboutConfig
        @DefaultIntValue(600)
        @DescriptionForConfigEntry(text_MaxWaitMinutesForTicket)
        int getMaxWaitMinutesForTicket();

        void setMaxWaitMinutesForTicket(int i);
    }

    public OtrDatenkellerNet(PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium("https://" + this.getHost() + "/payment");
    }

    private String getContentURL(final DownloadLink link) throws MalformedURLException {
        return "https://otr.datenkeller.net/?getFile=" + this.getFilenameFromURL(link);
    }

    @Override
    public String getAGBLink() {
        return "https://" + this.getHost() + "/";
    }

    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws IOException, PluginException {
        this.setBrowserExclusive();
        checkLinks(new DownloadLink[] { link });
        if (!link.isAvailabilityStatusChecked()) {
            return AvailableStatus.UNCHECKED;
        } else if (!link.isAvailable()) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        } else {
            return AvailableStatus.TRUE;
        }
    }

    @Override
    public boolean checkLinks(final DownloadLink[] urls) {
        if (urls == null || urls.length == 0) {
            return false;
        }
        try {
            api_prepBrowser(br);
            br.setCookiesExclusive(true);
            final StringBuilder sb = new StringBuilder();
            final ArrayList<DownloadLink> links = new ArrayList<DownloadLink>();
            int index = 0;
            while (true) {
                links.clear();
                while (true) {
                    /* we test up to 100 links at once */
                    if (index == urls.length || links.size() > 100) {
                        break;
                    }
                    links.add(urls[index]);
                    index++;
                }
                sb.delete(0, sb.capacity());
                for (final DownloadLink dl : links) {
                    sb.append(Encoding.urlEncode(getFilenameFromURL(dl)));
                    sb.append("%2C");
                }
                final UrlQuery query = new UrlQuery();
                query.add("api_version", APIVERSION);
                query.add("action", "validate");
                query.add("file", sb.toString());
                br.postPage(API_BASE_URL, query);
                final Map<String, Object> entries = restoreFromString(br.getRequest().getHtmlCode(), TypeRef.MAP);
                /* If all checked files are offline, "file_ok" field does not exist. */
                final Map<String, Object> file_okMap = (Map<String, Object>) entries.get("file_ok");
                for (final DownloadLink link : links) {
                    final String filenameFromURL = getFilenameFromURL(link);
                    final Map<String, Object> fileinfomap = file_okMap != null ? (Map<String, Object>) file_okMap.get(filenameFromURL) : null;
                    if (fileinfomap == null) {
                        link.setAvailable(false);
                    } else {
                        link.setAvailable(true);
                        final Object filesizeO = fileinfomap.get("filesize");
                        if (filesizeO instanceof String) {
                            link.setVerifiedFileSize(Long.parseLong(filesizeO.toString()));
                        } else {
                            link.setVerifiedFileSize(((Number) filesizeO).longValue());
                        }
                    }
                    link.setFinalFileName(Encoding.htmlDecode(filenameFromURL));
                }
                if (index == urls.length) {
                    break;
                }
            }
        } catch (final Exception e) {
            return false;
        }
        return true;
    }

    public String getDllink(final Browser br) throws Exception, PluginException {
        final Regex allMatches = br.getRegex("onclick=\"startCount\\(\\d+ +, +\\d+, +\\'([^<>\"\\']+)\\', +\\'([^<>\"\\']+)\\', +\\'([^<>\"\\']+)\\'\\)");
        String firstPart = allMatches.getMatch(1);
        String secondPart = allMatches.getMatch(0);
        String thirdPart = allMatches.getMatch(2);
        if (firstPart == null || secondPart == null || thirdPart == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        String dllink = "http://" + firstPart + "/" + secondPart + "/" + thirdPart;
        return dllink;
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        final int max = 5;
        final int running = freeRunning.get();
        final int ret = Math.min(running + 1, max);
        return ret;
    }

    private final String WEBAPI_BASE = "https://waitaws.lastverteiler.net/api.php";

    private void preDownloadWaitHandling(final DownloadLink link) throws PluginException {
        synchronized (timestampLastDownloadStarted) {
            final long waitMillisBetweenDownloads = 30 * 1000l;
            final long passedTimeMillisSinceLastDownload = Time.systemIndependentCurrentJVMTimeMillis() - timestampLastDownloadStarted.get();
            if (passedTimeMillisSinceLastDownload < waitMillisBetweenDownloads) {
                this.sleep(waitMillisBetweenDownloads - passedTimeMillisSinceLastDownload, link);
            }
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void handleFree(final DownloadLink link) throws Exception, PluginException {
        requestFileInformation(link);
        /* Use random UA again here because we do not use the same API as in linkcheck for free downloads */
        br.setFollowRedirects(true);
        br.clearCookies(null);
        br.getHeaders().put("User-Agent", getUserAgent());
        final String directlinkproperty = "free_finallink";
        final String storedDirecturl = link.getStringProperty(directlinkproperty);
        String dllink = null;
        if (storedDirecturl != null) {
            logger.info("Re-using stored directurl: " + storedDirecturl);
            dllink = storedDirecturl;
        } else {
            final String dlPageURL = this.getContentURL(link);
            // if (br.containsHTML("(?i)\"action needs to specified")) {
            // /**
            // * 2023-06-19: Looks like they've disabled API downloads without account(?) </br>
            // * {"status":"fail","reason":"api","message":"action needs to specified, it can be one of 'validate, login, getpremlink,
            // * getServers'"}
            // */
            // throw new AccountRequiredException();
            // }
            /* Check for limit is not needed, also their limits are based on cookies only */
            // if (br.containsHTML(">Du kannst höchstens \\d+ Download Links pro Stunde anfordern")) {
            // final String waitUntil = br.getRegex("bitte warte bis (\\d{1,2}:\\d{1,2}) zum nächsten Download").getMatch(0);
            // if (waitUntil != null) {
            // final long wtime = TimeFormatter.getMilliSeconds(waitUntil, "HH:mm", Locale.GERMANY);
            // throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, wtime);
            // }
            // throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, 30 * 60 * 1000l);
            // }
            String positionStr = null;
            String site_lowSpeedLink = null;
            final Browser br2 = br.cloneBrowser();
            String api_otrUID = null;
            final String finalfilenameurlencoded = Encoding.urlEncode(this.getFilenameFromURL(link));
            final boolean alsoUseWebAPI = false;
            boolean api_otrUID_used = false;
            boolean api_failed = false;
            // final String jscounturl = br.getRegex("(https?://[^\"]+/countMe\\.js\\?\\d+)").getMatch(0);
            // br2.getPage("http://staticaws.lastverteiler.net/otrfuncs/countMe.js");
            link.getLinkStatus().setStatusText("Waiting for ticket...");
            final int userDefinedMaxWaitMinutes = PluginJsonConfig.get(OtrDatenKellerInterface.class).getMaxWaitMinutesForTicket();
            int loops = 0;
            final long timeBefore = Time.systemIndependentCurrentJVMTimeMillis();
            while (true) {
                getPage(this.br, dlPageURL);
                final String refreshSecondsStr = br.getRegex("http-equiv=\"refresh\" content=\"(\\d{1,2})").getMatch(0);
                final int refreshSeconds;
                if (refreshSecondsStr != null) {
                    refreshSeconds = Integer.parseInt(refreshSecondsStr);
                } else {
                    refreshSeconds = 20;
                }
                site_lowSpeedLink = br.getRegex("\"(\\?lowSpeed=[^<>\\'\"]+)\"").getMatch(0);
                if (site_lowSpeedLink == null) {
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
                if (looksLikeDownloadIsAvailable(br)) {
                    dllink = getDllink(br);
                    break;
                }
                if (api_otrUID == null) {
                    api_otrUID = br.getRegex("waitaws\\.lastverteiler\\.net/([^<>\"]*?)/").getMatch(0);
                }
                if (api_otrUID == null) {
                    /* Basically the same/not relevant */
                    api_otrUID = br.getCookie(br.getHost(), "otrUID", Cookies.NOTDELETEDPATTERN);
                }
                positionStr = br.getRegex("(?i)Deine Position in der Warteschlange\\s*:\\s*</td><td>~(\\d+)</td>").getMatch(0);
                if (api_otrUID != null && alsoUseWebAPI) {
                    logger.info("Newway: New way active");
                    final Browser brc = br.cloneBrowser();
                    api_Free_prepBrowser(brc);
                    if (!api_otrUID_used) {
                        api_otrUID_used = true;
                        logger.info("NewwayUsing free API the first time...");
                        final String api_waitaws_url = "https://waitaws.lastverteiler.net/" + api_otrUID + "/" + finalfilenameurlencoded;
                        getPage(this.br, api_waitaws_url);
                        // TODO: Remove the following two lines
                        brc.postPage(api_waitaws_url, "action=validate&otrUID=" + api_otrUID + "&file=" + finalfilenameurlencoded);
                        if (brc.containsHTML("\"status\":\"fail\",\"reason\":\"user\"")) {
                            /* One retry is usually enough if the first attempt to use the API fails! */
                            if (api_failed) {
                                /* This should never happen */
                                throw new PluginException(LinkStatus.ERROR_HOSTER_TEMPORARILY_UNAVAILABLE, "FATAL API failure", 30 * 60 * 1000l);
                            }
                            logger.info("Newway: Failed to start queue - refreshing api_otrUID");
                            br.clearCookies(null);
                            br.getPage("https://" + this.getHost() + "/");
                            br.getPage(this.getContentURL(link));
                            br.getPage(dlPageURL);
                            api_otrUID_used = false;
                            api_otrUID = null;
                            api_failed = true;
                            continue;
                        }
                        brc.postPage(WEBAPI_BASE, "action=wait&status=ok&valid=ok&file=" + finalfilenameurlencoded + "&otrUID=" + api_otrUID);
                    }
                    String postData = "";
                    String[] params = brc.toString().split(",");
                    if (params == null || params.length == 0) {
                        logger.warning("Failed to get API postparameters");
                        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                    }
                    sleep(refreshSeconds * 1000l, link);
                    for (final String postPair : params) {
                        final String key = new Regex(postPair, "\"([^<>\"]*?)\"").getMatch(0);
                        String value = new Regex(postPair, "\"([^<>\"]*?)\":\"([^<>\"]*?)\"").getMatch(1);
                        if (value == null) {
                            value = new Regex(postPair, "\"([^<>\"]*?)\":(null|true|false|\\d+)").getMatch(1);
                        }
                        postData += key + "=" + Encoding.urlEncode(value) + "&";
                    }
                    brc.postPage(WEBAPI_BASE, postData);
                    positionStr = brc.getRegex("\"wait_pos\":\"(\\d+)\"").getMatch(0);
                    dllink = brc.getRegex("\"link\":\"(http:[^<>\"]*?)\"").getMatch(0);
                    if (dllink != null) {
                        dllink = dllink.replace("\\", "");
                    }
                }
                sleep(refreshSeconds * 1000l, link);
                link.getLinkStatus().setStatusText("Warten auf Ticket...Position in der Warteschlange: " + positionStr);
                if (loops > 400 && site_lowSpeedLink != null) {
                    logger.info("Waited too long - trying to use low speed downloadlink");
                    getPage(br2, site_lowSpeedLink);
                    dllink = br2.getRegex("(?i)>\\s*Dein Download Link:<br>\\s*<a href=\"(http://[^<>\\'\"]+)\"").getMatch(0);
                    if (dllink == null) {
                        dllink = br2.getRegex("\"(http://\\d+\\.\\d+\\.\\d+\\.\\d+/low/[a-z0-9]+/[^<>\\'\"]+)\"").getMatch(0);
                    }
                    if (dllink != null) {
                        logger.info("Using lowspeed link for downloadlink: " + link.getDownloadURL());
                        break;
                    } else {
                        logger.warning("Failed to find low speed link, continuing to look for downloadticket...");
                    }
                }
                logger.info("Didn't get a ticket on try " + loops + ". Retrying...Position: " + positionStr);
                if (Time.systemIndependentCurrentJVMTimeMillis() - timeBefore > userDefinedMaxWaitMinutes * 60 * 1000) {
                    logger.info("Stopping because: Did not get a ticket");
                    break;
                }
            }
            if (dllink == null) {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Didn't get a ticket", 30 * 60 * 1000l);
            }
            link.setProperty(directlinkproperty, dllink);
        }
        this.preDownloadWaitHandling(link);
        dl = jd.plugins.BrowserAdapter.openDownload(br, link, dllink, true, 1);
        if (storedDirecturl != null && !this.looksLikeDownloadableContent(dl.getConnection())) {
            link.removeProperty(directlinkproperty);
            throw new PluginException(LinkStatus.ERROR_RETRY, "Stored directurl expired");
        }
        checkErrorsAfterDownloadAttempt();
        synchronized (timestampLastDownloadStarted) {
            timestampLastDownloadStarted.set(Time.systemIndependentCurrentJVMTimeMillis());
        }
        controlSlot(link, null, +1);
        try {
            dl.startDownload();
        } finally {
            controlSlot(link, null, -1);
        }
    }

    private void checkErrorsAfterDownloadAttempt() throws IOException, PluginException {
        if (!this.looksLikeDownloadableContent(dl.getConnection())) {
            br.followConnection(true);
            if (br.containsHTML("(?i)>\\s*Maximale Anzahl an Verbindungen verbraucht")) {
                throw new PluginException(LinkStatus.ERROR_HOSTER_TEMPORARILY_UNAVAILABLE, "Too many connections", 1 * 60 * 1000l);
            } else if (br.containsHTML("(?i)wurde wegen Missbrauch geblockt")) {
                throw new AccountInvalidException("Account wurde wegen Missbrauch geblockt");
            } else if (dl.getConnection().getResponseCode() == 403) {
                throw new PluginException(LinkStatus.ERROR_HOSTER_TEMPORARILY_UNAVAILABLE, "Server error 403", 5 * 60 * 1000l);
            } else {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Unknown server error");
            }
        }
    }

    private boolean looksLikeDownloadIsAvailable(final Browser br) {
        return br.containsHTML("onclick=\"startCount");
    }

    @SuppressWarnings("deprecation")
    private void login(Account account, boolean force) throws Exception {
        br.setCookiesExclusive(true);
        api_prepBrowser(br);
        final boolean useAPIV2 = false;
        String apikey = getAPIKEY(account);
        if (!force && apikey != null) {
            /* Re-use existing apikey */
            return;
        }
        br.setFollowRedirects(false);
        if (useAPIV2 && DebugMode.TRUE_IN_IDE_ELSE_FALSE) {
            // TODO
            final Calendar cal = Calendar.getInstance();
            final UrlQuery query = new UrlQuery();
            query.add("jdlAPI", "");
            query.add("account", Encoding.urlEncode(account.getUser()));
            query.add("password", Encoding.urlEncode(account.getPass()));
            final String str = cal.get(Calendar.DAY_OF_MONTH) + account.getUser() + account.getPass();
            System.out.println("String to hash: " + str);
            query.add("auth", JDHash.getMD5(str));
            br.getPage("https://otr.datenkeller.net/?" + query.toString());
        } else {
            br.postPage(API_BASE_URL, "api_version=" + APIVERSION + "&action=login&username=" + Encoding.urlEncode(account.getUser()) + "&password=" + Encoding.urlEncode(account.getPass()));
            handleErrorsAPI();
            apikey = getJson(br.toString(), "apikey");
        }
        if (apikey == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        this.setApikey(account, apikey);
    }

    @Override
    public AccountInfo fetchAccountInfo(final Account account) throws Exception {
        final AccountInfo ai = new AccountInfo();
        login(account, true);
        final Map<String, Object> entries = restoreFromString(br.getRequest().getHtmlCode(), TypeRef.MAP);
        final Object expiresO = entries.get("expires");
        if (expiresO == null) {
            ai.setExpired(true);
            return ai;
        }
        if (expiresO instanceof Number) {
            ai.setValidUntil(((Number) expiresO).longValue() * 1000);
        } else {
            ai.setValidUntil(Long.parseLong(expiresO.toString()) * 1000);
        }
        account.setType(AccountType.PREMIUM);
        account.setConcurrentUsePossible(true);
        ai.setUnlimitedTraffic();
        return ai;
    }

    @Override
    public void handlePremium(final DownloadLink link, final Account account) throws Exception {
        login(account, false);
        final Browser brc = br.cloneBrowser();
        final UrlQuery query = new UrlQuery();
        query.add("api_version", APIVERSION);
        query.add("action", "getpremlink");
        query.add("username", Encoding.urlEncode(account.getUser()));
        query.add("password", Encoding.urlEncode(account.getPass()));
        query.add("apikey", Encoding.urlEncode(getAPIKEY(account)));
        query.add("filename", Encoding.urlEncode(getFilenameFromURL(link)));
        brc.postPage(API_BASE_URL, query);
        final Map<String, Object> resp = this.checkErrorsAPI(brc, link, account);
        final String dllink = (String) resp.get("dllink");
        if (StringUtils.isEmpty(dllink)) {
            /* This should never happen. */
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Failed to find final downloadurl");
        }
        this.preDownloadWaitHandling(link);
        dl = jd.plugins.BrowserAdapter.openDownload(br, link, Encoding.htmlDecode(dllink), true, 1);
        checkErrorsAfterDownloadAttempt();
        synchronized (timestampLastDownloadStarted) {
            timestampLastDownloadStarted.set(Time.systemIndependentCurrentJVMTimeMillis());
        }
        controlSlot(link, account, +1);
        try {
            dl.startDownload();
        } finally {
            controlSlot(link, account, -1);
        }
    }

    private void controlSlot(final DownloadLink link, final Account account, final int num) {
        if (account == null) {
            synchronized (freeRunning) {
                final int before = freeRunning.get();
                final int after = before + num;
                freeRunning.set(after);
                logger.info("freeRunning(" + link.getName() + ")|max:" + getMaxSimultanFreeDownloadNum() + "|before:" + before + "|after:" + after + "|num:" + num);
            }
        } else {
            synchronized (accountRunning) {
                final int before = accountRunning.get();
                final int after = before + num;
                accountRunning.set(after);
                logger.info("accountRunning(" + link.getName() + ")|max:" + getMaxSimultanPremiumDownloadNum() + "|before:" + before + "|after:" + after + "|num:" + num);
            }
        }
    }

    private Map<String, Object> checkErrorsAPI(final Browser br, final DownloadLink link, final Account account) throws PluginException {
        Map<String, Object> entries = null;
        try {
            entries = restoreFromString(br.getRequest().getHtmlCode(), TypeRef.MAP);
        } catch (final JSonMapperException jse) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Invalid API response");
        }
        final String status = (String) entries.get("status");
        final String message = (String) entries.get("message");
        if ("fail".equals(status)) {
            if (message.equalsIgnoreCase("failed to login due to wrong credentials, missing or wrong apikey")) {
                throw new AccountInvalidException();
            } else if (message.equalsIgnoreCase("failed to login due to wrong credentials or expired account")) {
                throw new AccountInvalidException();
            } else if (message.equalsIgnoreCase("wrong filename supplied")) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            } else {
                /* Unknown error */
                if (link == null) {
                    throw new AccountUnavailableException(message, 5 * 60 * 1000l);
                } else {
                    throw new PluginException(LinkStatus.ERROR_FATAL, message);
                }
            }
        }
        return entries;
    }

    /* Handles API errors */
    @Deprecated
    private void handleErrorsAPI() throws PluginException {
        final String status = getJson("status");
        final String message = getJson("message");
        if ("fail".equals(status) && message != null) {
            if (message.equalsIgnoreCase("failed to login due to wrong credentials, missing or wrong apikey")) {
                /* This should never happen. */
                /* Temp disable --> We will get a new apikey next full login --> Maybe that will help */
                throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_TEMP_DISABLE);
            } else if (message.equalsIgnoreCase("failed to login due to wrong credentials or expired account")) {
                if ("de".equalsIgnoreCase(System.getProperty("user.language"))) {
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nUngültiger Benutzername oder ungültiges Passwort!\r\nDu bist dir sicher, dass dein eingegebener Benutzername und Passwort stimmen? Versuche folgendes:\r\n1. Falls dein Passwort Sonderzeichen enthält, ändere es (entferne diese) und versuche es erneut!\r\n2. Gib deine Zugangsdaten per Hand (ohne kopieren/einfügen) ein.", PluginException.VALUE_ID_PREMIUM_DISABLE);
                } else {
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nInvalid username/password!\r\nYou're sure that the username and password you entered are correct? Some hints:\r\n1. If your password contains special characters, change it (remove them) and try again!\r\n2. Type in your username/password by hand without copy & paste.", PluginException.VALUE_ID_PREMIUM_DISABLE);
                }
            }
            logger.warning("Unknown API errorstate");
        }
    }

    private void api_prepBrowser(final Browser br) {
        br.getHeaders().put("User-Agent", "JDownloader");
        br.setCustomCharset("utf-8");
    }

    private void api_Free_prepBrowser(final Browser br) {
        br.getHeaders().put("Accept", "application/json, text/javascript, */*; q=0.01");
        br.getHeaders().put("X-Requested-With", "XMLHttpRequest");
        br.getHeaders().put("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        br.getHeaders().put("Accept-Charset", null);
    }

    final String getFilenameFromURL(final DownloadLink link) throws MalformedURLException {
        final UrlQuery query = UrlQuery.parse(link.getPluginPatternMatcher());
        String filename = query.get("getFile");
        if (filename == null) {
            filename = query.get("file");
        }
        return filename;
    }

    private void setApikey(final Account account, final String apikey) {
        account.setProperty("apikey_" + account.getUser(), apikey);
    }

    private String getAPIKEY(final Account account) {
        String apikey = account.getStringProperty("apikey_" + account.getUser());
        if (apikey != null) {
            apikey = apikey.replace("\\", "");
        }
        return apikey;
    }

    private String getJson(final String source, final String parameter) {
        String result = new Regex(source, "\"" + parameter + "\":([\t\n\r ]+)?([0-9\\.]+)").getMatch(1);
        if (result == null) {
            result = new Regex(source, "\"" + parameter + "\":([\t\n\r ]+)?\"([^<>\"]*?)\"").getMatch(1);
        }
        return result;
    }

    private String getJson(final String parameter) {
        return getJson(this.br.toString(), parameter);
    }

    private void getPage(final Browser br, final String url) throws IOException {
        br.getPage(url);
    }

    @Override
    public int getMaxSimultanPremiumDownloadNum() {
        /*
         * Admin told us limit = 12 but when we checked it, only 7 with a large wait time in between were possible - anyways, works best
         * with only one
         */
        /* 2023-06-20: Changed limit from 1 to 5. */
        final int max = 5;
        final int running = accountRunning.get();
        final int ret = Math.min(running + 1, max);
        return ret;
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }

    @Override
    public boolean hasCaptcha(final DownloadLink link, final jd.plugins.Account acc) {
        return false;
    }
}