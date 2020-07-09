//    jDownloader - Downloadmanager
//    Copyright (C) 2012  JD-Team support@jdownloader.org
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

import java.io.File;
import java.io.IOException;
import java.util.Map;

import org.appwork.storage.JSonStorage;
import org.appwork.storage.TypeRef;
import org.appwork.utils.StringUtils;
import org.jdownloader.captcha.v2.challenge.recaptcha.v1.Recaptcha;
import org.jdownloader.controlling.filter.CompiledFiletypeFilter;
import org.jdownloader.plugins.components.config.FacebookConfig;
import org.jdownloader.plugins.config.PluginJsonConfig;
import org.jdownloader.scripting.JavaScriptEngineFactory;

import jd.PluginWrapper;
import jd.controlling.AccountController;
import jd.http.Browser;
import jd.http.Cookies;
import jd.http.URLConnectionAdapter;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.parser.html.Form;
import jd.plugins.Account;
import jd.plugins.AccountInfo;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;
import jd.plugins.components.PluginJSonUtils;

@HostPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "facebook.com" }, urls = { "https?://(?:www\\.|m\\.)?facebook\\.com/(?:.*?video\\.php\\?v=|photo\\.php\\?fbid=|video/embed\\?video_id=|.*?/videos/|watch/\\?v=|watch/live/\\?v=)(\\d+)|https?://(?:www\\.)?facebook\\.com/download/(\\d+)" })
public class FaceBookComVideos extends PluginForHost {
    private String              FACEBOOKMAINPAGE  = "http://www.facebook.com";
    /* 2020-06-05: TODO: This linktype can (also) lead to video content! */
    private static final String TYPE_SINGLE_PHOTO = "https?://(www\\.)?facebook\\.com/photo\\.php\\?fbid=\\d+";
    // private static final String TYPE_SINGLE_VIDEO_ALL = "https?://(www\\.)?facebook\\.com/video\\.php\\?v=\\d+";
    private static final String TYPE_DOWNLOAD     = "https?://(www\\.)?facebook\\.com/download/\\d+";
    private static final String REV_2             = jd.plugins.decrypter.FaceBookComGallery.REV_2;
    private static final String REV_3             = jd.plugins.decrypter.FaceBookComGallery.REV_3;
    // five minutes, not 30seconds! -raztoki20160309
    private static final long   trust_cookie_age  = 300000l;
    private String              dllink            = null;
    private boolean             loggedIN          = false;
    private boolean             accountNeeded     = false;
    private int                 maxChunks         = 0;
    private boolean             is_private        = false;

    public FaceBookComVideos(final PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium("http://www.facebook.com/r.php");
        /*
         * to prevent all downloads starting and finishing together (quite common with small image downloads), login, http request and json
         * task all happen at same time and cause small hangups and slower download speeds. raztoki20160309
         */
        setStartIntervall(200l);
    }

    public void correctDownloadLink(final DownloadLink link) {
        final String videoid = new Regex(link.getPluginPatternMatcher(), this.getSupportedLinks()).getMatch(0);
        if (videoid != null) {
            /* Use current format for all URLs! */
            link.setPluginPatternMatcher("https://www.facebook.com/watch/?v=" + videoid);
        }
    }

    @Override
    public String getLinkID(final DownloadLink link) {
        final String fid = getFID(link);
        if (fid != null) {
            return this.getHost() + "://" + fid;
        } else {
            return super.getLinkID(link);
        }
    }

    private String getFID(final DownloadLink link) {
        String fuid = new Regex(link.getPluginPatternMatcher(), this.getSupportedLinks()).getMatch(0);
        if (fuid == null) {
            fuid = new Regex(link.getPluginPatternMatcher(), this.getSupportedLinks()).getMatch(1);
        }
        return fuid;
    }

    /**
     * JD2 CODE. DO NOT USE OVERRIDE FOR JD=) COMPATIBILITY REASONS!
     */
    public boolean isProxyRotationEnabledForLinkChecker() {
        return false;
    }

    public AvailableStatus requestFileInformation(final DownloadLink link) throws Exception {
        return requestFileInformation(link, false);
    }

    public AvailableStatus requestFileInformation(final DownloadLink link, final boolean isDownload) throws Exception {
        is_private = link.getBooleanProperty("is_private", false);
        dllink = link.getStringProperty("directlink", null);
        final String fid = this.getFID(link);
        if (!link.isNameSet()) {
            link.setName(fid);
        }
        br.setCookie("http://www.facebook.com", "locale", "en_GB");
        br.setFollowRedirects(true);
        final Account aa = AccountController.getInstance().getValidAccount(this);
        if (aa != null && aa.isValid()) {
            login(aa, br);
            loggedIN = true;
        }
        final boolean fastLinkcheck = PluginJsonConfig.get(this.getConfigInterface()).isEnableFastLinkcheck();
        String filename = null;
        URLConnectionAdapter con = null;
        if (link.getDownloadURL().matches(TYPE_SINGLE_PHOTO) && is_private) {
            accountNeeded = true;
            if (!loggedIN) {
                return AvailableStatus.UNCHECKABLE;
            }
            br.getPage(FACEBOOKMAINPAGE);
            final String user = getUser(br);
            final String image_id = this.getFID(link);
            if (user == null || image_id == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            final String getdata = "?photo_id=" + image_id + "&__pc=EXP1%3ADEFAULT&__user=" + user + "&__a=1&__dyn=" + jd.plugins.decrypter.FaceBookComGallery.getDyn() + "&__req=11&__rev=" + jd.plugins.decrypter.FaceBookComGallery.getRev(this.br);
            br.getPage("https://www.facebook.com/mercury/attachments/photo/" + getdata);
            br.getRequest().setHtmlCode(this.br.toString().replace("\\", ""));
            dllink = br.getRegex("\"(https?://[^/]+\\.fbcdn\\.net/[^<>\"]+)\"").getMatch(0);
            if (dllink == null) {
                dllink = br.getRegex("(https?://[^<>\"]+\\&dl=1)").getMatch(0);
            }
            if (dllink == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            maxChunks = 1;
            try {
                con = br.openHeadConnection(dllink);
                if (!con.getContentType().contains("html")) {
                    filename = Encoding.htmlDecode(getFileNameFromHeader(con));
                    link.setDownloadSize(con.getLongContentLength());
                } else {
                    throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
                }
            } finally {
                try {
                    con.disconnect();
                } catch (Throwable e) {
                }
            }
        } else if (link.getDownloadURL().matches(TYPE_DOWNLOAD)) {
            maxChunks = 1;
            try {
                dllink = link.getDownloadURL();
                con = br.openGetConnection(dllink);
                if (!con.getContentType().contains("html")) {
                    filename = Encoding.htmlDecode(getFileNameFromHeader(con));
                    link.setDownloadSize(con.getLongContentLength());
                } else {
                    throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
                }
            } finally {
                try {
                    con.disconnect();
                } catch (Throwable e) {
                }
            }
        } else {
            link.setMimeHint(CompiledFiletypeFilter.VideoExtensions.MP4);
            final String videoID = this.getFID(link);
            /* Embed = no filenames given */
            final boolean useEmbedOnly = false;
            if (useEmbedOnly) {
                accessVideoEmbed(videoID, true);
            } else {
                /* Use mobile website */
                br.setAllowedResponseCodes(new int[] { 500 });
                br.getPage("https://m.facebook.com/video.php?v=" + videoID);
                /* 2020-06-12: Website does never return appropriate 404 code so we have to check for strings in html :/ */
                if (br.getHttpConnection().getResponseCode() == 404 || br.getHttpConnection().getResponseCode() == 500 || br.containsHTML("<title>\\s*Content not found\\s*</title>")) {
                    throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
                }
                /* Use whatever is in this variable as a fallback downloadurl if we fail to find one via embedded video call. */
                String fallback_downloadurl = null;
                /* Get standardized json object "VideoObject" */
                final String json = br.getRegex("<script[^>]*?type=\"application/ld\\+json\"[^>]*>(.*?)</script>").getMatch(0);
                try {
                    final Map<String, Object> entries = JSonStorage.restoreFromString(json, TypeRef.HASHMAP);
                    final String title = (String) entries.get("name");
                    final String uploadDate = (String) entries.get("uploadDate");
                    final String uploader = (String) JavaScriptEngineFactory.walkJson(entries, "author/name");
                    if (StringUtils.isAllNotEmpty(title, uploadDate, uploader)) {
                        String date_formatted = new Regex(uploadDate, "(\\d{4}-\\d{2}-\\d{2})").getMatch(0);
                        if (date_formatted == null) {
                            date_formatted = uploadDate;
                        }
                        filename = date_formatted + "_";
                        /* 2020-06-12: Uploader is not always given in json */
                        if (uploader != null) {
                            filename += uploader + "_";
                        }
                        filename += title;
                    }
                    /*
                     * 2020-06-12: We avoid using this as final downloadurl [use only as fallback] as it is lower quality than via the
                     * "embed" way. Also the given filesize is usually much higher than any stream we get --> That might be the original
                     * filesize of the uploaded content ...
                     */
                    fallback_downloadurl = (String) entries.get("contentUrl");
                    // final String contentSize = (String) entries.get("contentSize");
                    // if (contentSize != null) {
                    // link.setDownloadSize(SizeFormatter.getSize(contentSize));
                    // }
                } catch (final Throwable e) {
                    logger.log(e);
                }
                if (filename == null) {
                    /* Fallback - json is not always given */
                    filename = br.getRegex("<title>(.*?)</title>").getMatch(0);
                }
                /*
                 * 2020-06-12: Get downloadurl from embedded URL --> Best possible http quality --> Use this one only as a fallback e.g. for
                 * content which is not available embedded because it is officially only available via MDP streaming:
                 * https://svn.jdownloader.org/issues/88438 </br> fallback_downloadurl is generally lower quality than e.g. possible via MDP
                 * streaming!
                 */
                if (StringUtils.isEmpty(fallback_downloadurl)) {
                    fallback_downloadurl = br.getRegex("property=\"og:video\" content=\"(https?://[^<>\"]+)\"").getMatch(0);
                    if (fallback_downloadurl != null) {
                        fallback_downloadurl = Encoding.htmlDecode(fallback_downloadurl);
                    }
                }
                /* Find downloadurl */
                if (!fastLinkcheck && !isDownload) {
                    accessVideoEmbed(videoID, false);
                }
                if (StringUtils.isEmpty(this.dllink) && !StringUtils.isEmpty(fallback_downloadurl)) {
                    logger.info("Using fallback downloadurl --> This video is probably usually only streamable via MDP streaming!");
                    this.dllink = fallback_downloadurl;
                }
            }
            if (filename == null) {
                /* Fallback */
                filename = videoID;
            }
            if (filename == null) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            filename = Encoding.htmlDecode(filename.trim());
            // ive seen new lines within filename!
            filename = filename.replaceAll("[\r\n]+", " ");
            if (br.containsHTML(">You must log in to continue")) {
                accountNeeded = true;
                if (!loggedIN) {
                    logger.info("You must log in to continue");
                    return AvailableStatus.UNCHECKABLE;
                }
            }
            if (!filename.contains(fid)) {
                filename = filename + "_" + fid;
            }
            filename += ".mp4";
        }
        link.setFinalFileName(filename);
        if (this.dllink != null && this.dllink.startsWith("http") && !isDownload && !fastLinkcheck) {
            try {
                con = br.openHeadConnection(this.dllink);
                if (con.getContentType().contains("text") || !con.isOK() || con.getLongContentLength() == -1) {
                    throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
                } else {
                    link.setDownloadSize(con.getCompleteContentLength());
                }
            } finally {
                try {
                    con.disconnect();
                } catch (final Throwable e) {
                }
            }
        }
        return AvailableStatus.TRUE;
    }

    private void accessVideoEmbed(final String videoID, final boolean checkForOffline) throws PluginException, IOException {
        if (videoID == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        br.getPage("https://www.facebook.com/video/embed?video_id=" + videoID);
        /*
         * 2020-06-05: <div class="pam uiBoxRed"><div class="fsl fwb fcb">Video nicht verfügbar</div>Dieses Video wurde entweder entfernt
         * oder ist aufgrund der ‎Privatsphäre-Einstellungen nicht sichtbar.</div>
         */
        if (checkForOffline && (br.getHttpConnection().getResponseCode() == 404 || br.containsHTML("class=\"pam uiBoxRed\""))) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        this.dllink = getDllinkVideo();
    }

    // private String checkDllink(final String flink) throws Exception {
    // URLConnectionAdapter con = null;
    // final Browser br3 = br.cloneBrowser();
    // br3.setFollowRedirects(true);
    // try {
    // con = br3.openHeadConnection(flink);
    // if (!con.getContentType().contains("text")) {
    // dllink = flink;
    // } else {
    // dllink = null;
    // }
    // } catch (final Exception e) {
    // } finally {
    // try {
    // con.disconnect();
    // } catch (final Exception e) {
    // }
    // }
    // return dllink;
    // }
    /**
     * Checks for offline html in all kinds of supported urls. Keep in mind that these "offline" message can also mean that access is
     * restricted only for certain users. Basically user would at least need an account and even when he has one but not the rights to view
     * the content we get the same message. in over 90% of all cases, content will be offline so we should simply treat it as offline.
     */
    public static boolean isOffline(final Browser br) {
        /* TODO: Add support for more languages here */
        /* Example: https://www.facebook.com/photo.php?fbid=624011957634791 */
        return br.containsHTML(">The link you followed may have expired|>Leider ist dieser Inhalt derzeit nicht");
    }

    @Override
    public AccountInfo fetchAccountInfo(final Account account) throws Exception {
        final AccountInfo ai = new AccountInfo();
        try {
            login(account, br);
        } catch (final PluginException e) {
            throw e;
        }
        ai.setStatus("Valid Facebook account is active");
        ai.setUnlimitedTraffic();
        return ai;
    }

    @Override
    public String getAGBLink() {
        return "http://www.facebook.com/terms.php";
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return -1;
    }

    @Override
    public int getMaxSimultanPremiumDownloadNum() {
        return -1;
    }

    @Override
    public void handleFree(final DownloadLink link) throws Exception, PluginException {
        requestFileInformation(link, true);
        if (dllink != null) {
            dl = jd.plugins.BrowserAdapter.openDownload(br, link, dllink, true, maxChunks);
            if (dl.getConnection().getContentType().contains("html")) {
                br.followConnection();
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            dl.startDownload();
        } else {
            if (accountNeeded && !this.loggedIN) {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_ONLY);
            } else {
                handleVideo(link);
            }
        }
    }

    private String getHigh() {
        final String result = PluginJSonUtils.getJsonValue(br, "hd_src");
        return result;
    }

    private String getLow() {
        final String result = PluginJSonUtils.getJsonValue(br, "sd_src");
        return result;
    }

    @Override
    public void handlePremium(final DownloadLink link, final Account account) throws Exception {
        requestFileInformation(link, true);
        if (dllink != null) {
            dl = jd.plugins.BrowserAdapter.openDownload(br, link, dllink, true, 0);
            if (dl.getConnection().getContentType().contains("html")) {
                br.followConnection();
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            dl.startDownload();
        } else {
            handleVideo(link);
        }
    }

    public String getDllinkVideo() {
        String dllink = null;
        final boolean preferHD = PluginJsonConfig.get(this.getConfigInterface()).isPreferHD();
        if (preferHD) {
            dllink = getHigh();
            if (dllink == null || "null".equals(dllink)) {
                dllink = getLow();
            }
        } else {
            dllink = getLow();
            if (dllink == null || "null".equals(dllink)) {
                dllink = getHigh();
            }
        }
        return dllink;
    }

    public void handleVideo(final DownloadLink downloadLink) throws Exception {
        if (this.dllink == null || !this.dllink.startsWith("http")) {
            getDllinkVideo();
        }
        if (dllink == null || "null".equals(dllink)) {
            logger.warning("Final downloadlink \"dllink\" is null");
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        logger.info("Final downloadlink = " + dllink + " starting download...");
        final String Vollkornkeks = downloadLink.getDownloadURL().replace(FACEBOOKMAINPAGE, "");
        br.setCookie(FACEBOOKMAINPAGE, "x-referer", Encoding.urlEncode(FACEBOOKMAINPAGE + Vollkornkeks + "#" + Vollkornkeks));
        dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, dllink, true, 0);
        if (dl.getConnection().getContentType().contains("html")) {
            br.followConnection();
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl.startDownload();
    }

    private static final String LOGINFAIL_GERMAN  = "\r\nEvtl. ungültiger Benutzername oder ungültiges Passwort!\r\nSchnellhilfe: \r\nDu bist dir sicher, dass dein eingegebener Benutzername und Passwort stimmen?\r\nFalls dein Passwort Sonderzeichen enthält, ändere es und versuche es erneut!\r\nBedenke, dass die Facebook Anmeldung per JD nur funktioniert, wenn Facebook\r\nkeine zusätzlichen Sicherheitsabfragen beim Login deines Accounts verlangt.\r\nPrüfe das und versuchs erneut!";
    private static final String LOGINFAIL_ENGLISH = "\r\nMaybe invalid username/password!\r\nQuick help:\r\nYou're sure that the username and password you entered are correct?\r\nIf your password contains special characters, change it (remove them) and try again!\r\nNote that the Facebook login via JD will only work if there are no additional\r\nsecurity questions when logging in your account.\r\nCheck that and try again!";

    private void setHeaders(Browser br) {
        br.getHeaders().put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        br.getHeaders().put("Accept-Language", "en-gb, en;q=0.9");
        br.getHeaders().put("Accept-Encoding", "gzip, deflate");
        br.getHeaders().put("Accept-Charset", "ISO-8859-1,utf-8;q=0.7,*;q=0.7");
        br.setCookie("http://www.facebook.com", "locale", "en_GB");
    }

    public void login(final Account account, Browser br) throws Exception {
        synchronized (account) {
            try {
                setHeaders(br);
                // Load cookies
                br.setCookiesExclusive(true);
                final Cookies cookies = account.loadCookies("");
                if (cookies != null) {
                    br.setCookies(FACEBOOKMAINPAGE, cookies);
                    if (System.currentTimeMillis() - account.getCookiesTimeStamp("") <= trust_cookie_age) {
                        /* We trust these cookies --> Do not check them */
                        return;
                    }
                    final boolean follow = br.isFollowingRedirects();
                    try {
                        br.setFollowRedirects(true);
                        br.getPage(FACEBOOKMAINPAGE);
                    } finally {
                        br.setFollowRedirects(follow);
                    }
                    if (br.containsHTML("id=\"logoutMenu\"")) {
                        /* Save cookies to save new valid cookie timestamp */
                        account.saveCookies(br.getCookies(FACEBOOKMAINPAGE), "");
                        return;
                    }
                    /* Get rid of old cookies / headers */
                    br = new Browser();
                    br.setCookiesExclusive(true);
                    setHeaders(br);
                }
                br.setFollowRedirects(true);
                final boolean prefer_mobile_login = false;
                // better use the website login. else the error handling below might be broken.
                if (prefer_mobile_login) {
                    /* Mobile login = no crypto crap */
                    br.getPage("https://m.facebook.com/");
                    final Form loginForm = br.getForm(0);
                    if (loginForm == null) {
                        if ("de".equalsIgnoreCase(System.getProperty("user.language"))) {
                            throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nPlugin defekt, bitte den JDownloader Support kontaktieren!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                        } else {
                            throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nPlugin broken, please contact the JDownloader Support!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                        }
                    }
                    loginForm.remove(null);
                    loginForm.put("email", Encoding.urlEncode(account.getUser()));
                    loginForm.put("pass", Encoding.urlEncode(account.getPass()));
                    br.submitForm(loginForm);
                    br.getPage("https://www.facebook.com/");
                } else {
                    br.getPage("https://www.facebook.com/login.php");
                    final String lang = System.getProperty("user.language");
                    final Form loginForm = br.getForm(0);
                    if (loginForm == null) {
                        if ("de".equalsIgnoreCase(lang)) {
                            throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nPlugin defekt, bitte den JDownloader Support kontaktieren!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                        } else {
                            throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nPlugin broken, please contact the JDownloader Support!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                        }
                    }
                    loginForm.remove("persistent");
                    loginForm.put("persistent", "1");
                    loginForm.remove(null);
                    loginForm.remove("login");
                    loginForm.remove("trynum");
                    loginForm.remove("profile_selector_ids");
                    loginForm.remove("legacy_return");
                    loginForm.remove("enable_profile_selector");
                    loginForm.remove("display");
                    String _js_datr = br.getRegex("\"_js_datr\"\\s*,\\s*\"([^\"]+)").getMatch(0);
                    br.setCookie("https://facebook.com", "_js_datr", _js_datr);
                    br.setCookie("https://facebook.com", "_js_reg_fb_ref", Encoding.urlEncode("https://www.facebook.com/login.php"));
                    br.setCookie("https://facebook.com", "_js_reg_fb_gate", Encoding.urlEncode("https://www.facebook.com/login.php"));
                    loginForm.put("email", Encoding.urlEncode(account.getUser()));
                    loginForm.put("pass", Encoding.urlEncode(account.getPass()));
                    br.submitForm(loginForm);
                }
                /**
                 * Facebook thinks we're an unknown device, now we prove we're not ;)
                 */
                if (br.containsHTML(">Your account is temporarily locked")) {
                    final String nh = br.getRegex("name=\"nh\" value=\"([a-z0-9]+)\"").getMatch(0);
                    final String dstc = br.getRegex("name=\"fb_dtsg\" value=\"([^<>\"]*?)\"").getMatch(0);
                    if (nh == null || dstc == null) {
                        if ("de".equalsIgnoreCase(System.getProperty("user.language"))) {
                            throw new PluginException(LinkStatus.ERROR_PREMIUM, LOGINFAIL_GERMAN, PluginException.VALUE_ID_PREMIUM_DISABLE);
                        } else {
                            throw new PluginException(LinkStatus.ERROR_PREMIUM, LOGINFAIL_ENGLISH, PluginException.VALUE_ID_PREMIUM_DISABLE);
                        }
                    }
                    br.postPage(br.getURL(), "fb_dtsg=" + Encoding.urlEncode(dstc) + "&nh=" + nh + "&submit%5BContinue%5D=Continue");
                    final DownloadLink dummyLink = new DownloadLink(this, "Account", "facebook.com", "http://facebook.com", true);
                    String achal = br.getRegex("name=\"achal\" value=\"([a-z0-9]+)\"").getMatch(0);
                    final String captchaPersistData = br.getRegex("name=\"captcha_persist_data\" value=\"([^<>\"]*?)\"").getMatch(0);
                    if (captchaPersistData == null || achal == null) {
                        if ("de".equalsIgnoreCase(System.getProperty("user.language"))) {
                            throw new PluginException(LinkStatus.ERROR_PREMIUM, LOGINFAIL_GERMAN, PluginException.VALUE_ID_PREMIUM_DISABLE);
                        } else {
                            throw new PluginException(LinkStatus.ERROR_PREMIUM, LOGINFAIL_ENGLISH, PluginException.VALUE_ID_PREMIUM_DISABLE);
                        }
                    }
                    // Normal captcha handling
                    for (int i = 1; i <= 3; i++) {
                        String captchaLink = br.getRegex("\"(https?://(www\\.)?facebook\\.com/captcha/tfbimage\\.php\\?captcha_challenge_code=[^<>\"]*?)\"").getMatch(0);
                        if (captchaLink == null) {
                            break;
                        }
                        captchaLink = Encoding.htmlDecode(captchaLink);
                        String code;
                        try {
                            code = getCaptchaCode(captchaLink, dummyLink);
                        } catch (final Exception e) {
                            continue;
                        }
                        br.postPage(br.getURL(), "fb_dtsg=" + Encoding.urlEncode(dstc) + "&nh=" + nh + "&geo=true&captcha_persist_data=" + Encoding.urlEncode(captchaPersistData) + "&captcha_response=" + Encoding.urlEncode(code) + "&achal=" + achal + "&submit%5BSubmit%5D=Submit");
                    }
                    // reCaptcha handling
                    for (int i = 1; i <= 3; i++) {
                        final String rcID = br.getRegex("\"recaptchaPublicKey\":\"([^<>\"]*?)\"").getMatch(0);
                        if (rcID == null) {
                            break;
                        }
                        final String extraChallengeParams = br.getRegex("name=\"extra_challenge_params\" value=\"([^<>\"]*?)\"").getMatch(0);
                        final String captchaSession = br.getRegex("name=\"captcha_session\" value=\"([^<>\"]*?)\"").getMatch(0);
                        if (extraChallengeParams == null || captchaSession == null) {
                            break;
                        }
                        final Recaptcha rc = new Recaptcha(br, this);
                        rc.setId(rcID);
                        rc.load();
                        final File cf = rc.downloadCaptcha(getLocalCaptchaFile());
                        String c;
                        try {
                            c = getCaptchaCode("recaptcha", cf, dummyLink);
                        } catch (final Exception e) {
                            continue;
                        }
                        br.postPage(br.getURL(), "fb_dtsg=" + Encoding.urlEncode(dstc) + "&nh=" + nh + "&geo=true&captcha_persist_data=" + Encoding.urlEncode(captchaPersistData) + "&captcha_session=" + Encoding.urlEncode(captchaSession) + "&extra_challenge_params=" + Encoding.urlEncode(extraChallengeParams) + "&recaptcha_type=password&recaptcha_challenge_field=" + Encoding.urlEncode(rc.getChallenge()) + "&captcha_response=" + Encoding.urlEncode(c) + "&achal=1&submit%5BSubmit%5D=Submit");
                    }
                    for (int i = 1; i <= 3; i++) {
                        if (br.containsHTML(">To confirm your identity, please enter your birthday")) {
                            achal = br.getRegex("name=\"achal\" value=\"([a-z0-9]+)\"").getMatch(0);
                            if (achal == null) {
                                break;
                            }
                            String birthdayVerificationAnswer;
                            try {
                                birthdayVerificationAnswer = getUserInput("Enter your birthday (dd:MM:yyyy)", dummyLink);
                            } catch (final Exception e) {
                                continue;
                            }
                            final String[] bdSplit = birthdayVerificationAnswer.split(":");
                            if (bdSplit == null || bdSplit.length != 3) {
                                continue;
                            }
                            int bdDay = 0, bdMonth = 0, bdYear = 0;
                            try {
                                bdDay = Integer.parseInt(bdSplit[0]);
                                bdMonth = Integer.parseInt(bdSplit[1]);
                                bdYear = Integer.parseInt(bdSplit[2]);
                            } catch (final Exception e) {
                                continue;
                            }
                            br.postPage(br.getURL(), "fb_dtsg=" + Encoding.urlEncode(dstc) + "&nh=" + nh + "&geo=true&birthday_captcha_month=" + bdMonth + "&birthday_captcha_day=" + bdDay + "&birthday_captcha_year=" + bdYear + "&captcha_persist_data=" + Encoding.urlEncode(captchaPersistData) + "&achal=" + achal + "&submit%5BSubmit%5D=Submit");
                        } else {
                            break;
                        }
                    }
                    if (br.containsHTML("/captcha/friend_name_image\\.php\\?")) {
                        // unsupported captcha challange.
                        logger.warning("Unsupported captcha challenge.");
                    }
                } else if (br.containsHTML("/checkpoint/")) {
                    br.getPage("https://www.facebook.com/checkpoint/");
                    final String postFormID = br.getRegex("name=\"post_form_id\" value=\"(.*?)\"").getMatch(0);
                    final String nh = br.getRegex("name=\"nh\" value=\"(.*?)\"").getMatch(0);
                    if (nh == null) {
                        if ("de".equalsIgnoreCase(System.getProperty("user.language"))) {
                            throw new PluginException(LinkStatus.ERROR_PREMIUM, LOGINFAIL_GERMAN, PluginException.VALUE_ID_PREMIUM_DISABLE);
                        } else {
                            throw new PluginException(LinkStatus.ERROR_PREMIUM, LOGINFAIL_ENGLISH, PluginException.VALUE_ID_PREMIUM_DISABLE);
                        }
                    }
                    br.postPage("https://www.facebook.com/checkpoint/", "post_form_id=" + postFormID + "&lsd=GT_Up&submit%5BContinue%5D=Weiter&nh=" + nh);
                    br.postPage("https://www.facebook.com/checkpoint/", "post_form_id=" + postFormID + "&lsd=GT_Up&submit%5BThis+is+Okay%5D=Das+ist+OK&nh=" + nh);
                    br.postPage("https://www.facebook.com/checkpoint/", "post_form_id=" + postFormID + "&lsd=GT_Up&machine_name=&submit%5BDon%27t+Save%5D=Nicht+speichern&nh=" + nh);
                    br.postPage("https://www.facebook.com/checkpoint/", "post_form_id=" + postFormID + "&lsd=GT_Up&machine_name=&submit%5BDon%27t+Save%5D=Nicht+speichern&nh=" + nh);
                }
                if (br.getCookie(FACEBOOKMAINPAGE, "c_user") == null || br.getCookie(FACEBOOKMAINPAGE, "xs") == null) {
                    if ("de".equalsIgnoreCase(System.getProperty("user.language"))) {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, LOGINFAIL_GERMAN, PluginException.VALUE_ID_PREMIUM_DISABLE);
                    } else {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, LOGINFAIL_ENGLISH, PluginException.VALUE_ID_PREMIUM_DISABLE);
                    }
                }
                /* Save cookies */
                account.saveCookies(br.getCookies(FACEBOOKMAINPAGE), "");
                account.setValid(true);
            } catch (PluginException e) {
                if (e.getLinkStatus() == PluginException.VALUE_ID_PREMIUM_DISABLE) {
                    account.removeProperty("");
                }
                throw e;
            }
        }
    }

    /* NO OVERRIDE!! We need to stay 0.9*compatible */
    public boolean allowHandle(final DownloadLink downloadLink, final PluginForHost plugin) {
        return downloadLink.getHost().equalsIgnoreCase(plugin.getHost());
    }

    @Override
    public Class<? extends FacebookConfig> getConfigInterface() {
        return FacebookConfig.class;
    }

    private static String getUser(final Browser br) {
        return jd.plugins.decrypter.FaceBookComGallery.getUser(br);
    }

    private String getajaxpipeToken() {
        return PluginJSonUtils.getJsonValue(br, "ajaxpipe_token");
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(final DownloadLink link) {
    }

    @Override
    public boolean hasCaptcha(DownloadLink link, jd.plugins.Account acc) {
        /* Only login captcha sometimes */
        return false;
    }
}