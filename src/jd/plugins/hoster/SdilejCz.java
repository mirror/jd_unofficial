//    jDownloader - Downloadmanager
//    Copyright (C) 2009  JD-Team support@jdownloader.org
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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.appwork.utils.formatter.SizeFormatter;

import jd.PluginWrapper;
import jd.http.Browser;
import jd.http.Cookie;
import jd.http.Cookies;
import jd.http.RandomUserAgent;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.Account;
import jd.plugins.Account.AccountType;
import jd.plugins.AccountInfo;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;
import jd.utils.locale.JDL;

@HostPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "sdilej.cz" }, urls = { "https?://(?:www\\.)?sdilej\\.cz/(\\d+)/([a-z0-9-\\.]+)" })
public class SdilejCz extends PluginForHost {
    /** Former czshare.com */
    private static AtomicInteger SIMULTANEOUS_PREMIUM = new AtomicInteger(-1);
    private static final String  MAINPAGE             = "http://sdilej.cz/";
    private static final String  CAPTCHATEXT          = "captcha\\.php";

    public SdilejCz(PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium("http://sdilej.cz/registrace");
    }

    @Override
    public boolean hasCaptcha(DownloadLink link, jd.plugins.Account acc) {
        if (acc != null && AccountType.PREMIUM.equals(acc.getType())) {
            return false;
        } else {
            return true;
        }
    }

    @Override
    public boolean hasAutoCaptcha() {
        return false;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void correctDownloadLink(final DownloadLink link) {
        link.setUrlDownload(link.getDownloadURL().replace("https://", "http://"));
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
        return new Regex(link.getPluginPatternMatcher(), this.getSupportedLinks()).getMatch(0);
    }

    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws IOException, PluginException {
        if (!link.isNameSet()) {
            final String urlTitle = new Regex(link.getPluginPatternMatcher(), this.getSupportedLinks()).getMatch(1);
            link.setName(urlTitle.replace("-", " "));
        }
        this.setBrowserExclusive();
        prepBR(this.br);
        br.setFollowRedirects(true);
        br.getHeaders().put("User-Agent", RandomUserAgent.generate());
        br.getPage(link.getPluginPatternMatcher());
        if (br.containsHTML("(?i)Omluvte, prosím, výpadek databáze\\. Na opravě pracujeme")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        } else if (!this.canHandle(br.getURL())) {
            /* E.g. /error.php?co=4 */
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        final String filename = br.getRegex("<h1[^<>]+>([^<>\"]*?)<").getMatch(0);
        String filesize = br.getRegex("Velikost:</b> (.*?)<").getMatch(0);
        /* Set final filename here because server sends html encoded filenames */
        if (filename != null) {
            link.setFinalFileName(Encoding.htmlDecode(filename).trim());
        }
        if (filesize != null) {
            filesize = filesize.trim();
            if (filesize.equals("0 B")) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            link.setDownloadSize(SizeFormatter.getSize(filesize.replace(",", ".").replace(" ", "")));
        }
        return AvailableStatus.TRUE;
    }

    @Override
    public String getAGBLink() {
        return "http://sdilej.cz/VOP.pdf";
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return 1;
    }

    @Override
    public int getMaxSimultanPremiumDownloadNum() {
        return SIMULTANEOUS_PREMIUM.get();
    }

    private void handleErrors() throws PluginException {
        if (br.containsHTML("Z Vaší IP adresy momentálně probíhá jiné stahování\\. Využijte PROFI")) {
            throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, JDL.L("plugins.hoster.czsharecom.ipalreadydownloading", "IP already downloading"), 12 * 60 * 1001);
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void handleFree(final DownloadLink link) throws Exception {
        requestFileInformation(link);
        handleErrors();
        String dllink = br.getRegex("<a href=\"([^<>\"]+?)\"[^<>]+?>Stáhnout FREE<").getMatch(0);
        if (dllink == null) {
            if (!br.containsHTML("Stáhnout FREE(</span>)?</a><a href=\"/download\\.php\\?id=")) {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, JDL.L("plugins.hoster.CZShareCom.nofreeslots", "No free slots available"), 60 * 1000);
            }
            br.setFollowRedirects(true);
            String freeLink = br.getRegex("allowTransparency=\"true\"></iframe><a href=\"(/.*?)\"").getMatch(0);
            if (freeLink == null) {
                freeLink = br.getRegex("\"(/download\\.php\\?id=\\d+.*?code=.*?)\"").getMatch(0);
            }
            if (freeLink == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            br.getPage("http://sdilej.cz" + Encoding.htmlDecode(freeLink));
            handleErrors();
            String file = br.getRegex("name=\"file\" value=\"(.*?)\"").getMatch(0);
            String size = br.getRegex("name=\"size\" value=\"(\\d+)\"").getMatch(0);
            String server = br.getRegex("name=\"server\" value=\"(.*?)\"").getMatch(0);
            if (!br.containsHTML(CAPTCHATEXT) || file == null || size == null || server == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            String code = getCaptchaCode("http://sdilej.cz/captcha.php", link);
            br.postPage("http://sdilej.cz/download.php", "id=" + new Regex(link.getDownloadURL(), "sdilej\\.cz/(\\d+)/.*?").getMatch(0) + "&file=" + file + "&size=" + size + "&server=" + server + "&captchastring2=" + Encoding.urlEncode(code) + "&freedown=Ov%C4%9B%C5%99it+a+st%C3%A1hnout");
            if (br.containsHTML("Chyba 6 / Error 6")) {
                throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, 60 * 60 * 1000);
            }
            if (br.containsHTML(">Zadaný ověřovací kód nesouhlasí") || br.containsHTML(CAPTCHATEXT)) {
                throw new PluginException(LinkStatus.ERROR_CAPTCHA);
            }
            dllink = br.getRegex("<p class=\"button2\" id=\"downloadbtn\" style=\"display:none\">[\t\n\r ]+<a href=\"(https?://[^<>\"]*?)\"").getMatch(0);
            if (dllink == null) {
                dllink = br.getRegex("\"(https?://www\\d+\\.sdilej\\.cz/download\\.php\\?id=[^<>\"]*?)\"").getMatch(0);
            }
            if (dllink == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            /** Waittime can be skipped */
            // int wait = 50;
            // final String waittime =
            // br.getRegex("countdown_number = (\\d+);").getMatch(0);
            // if (waittime != null) wait = Integer.parseInt(waittime);
            // sleep(wait * 1001l, downloadLink);
        }
        dl = jd.plugins.BrowserAdapter.openDownload(br, link, dllink, false, 1);
        if (!looksLikeDownloadableContent(dl.getConnection())) {
            br.followConnection(true);
            if (br.containsHTML("Soubor byl smazan")) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            } else if (br.containsHTML("Soubor je dočasně nedostupný\\.") || dl.getConnection().getContentType().contains("unknown")) {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error");
            } else if (dl.getConnection().getResponseCode() == 403) {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 403", 60 * 60 * 1000l);
            } else if (dl.getConnection().getResponseCode() == 404) {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 404", 60 * 60 * 1000l);
            } else {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
        }
        dl.startDownload();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void handlePremium(final DownloadLink link, final Account account) throws Exception {
        requestFileInformation(link);
        login(account, false);
        br.setFollowRedirects(true);
        br.getPage(link.getPluginPatternMatcher());
        br.setFollowRedirects(false);
        final String contentID = new Regex(link.getDownloadURL(), "sdilej\\.cz/(\\d+)/").getMatch(0);
        if (contentID == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        String dllink = br.getRegex("\"\\s*(https?://(?:\\w+\\.)?sdilej.cz/sdilej_(?:profi|down)\\.php\\?id=" + contentID + "&.*?)\\s*\"").getMatch(0);
        if (dllink == null) {
            String code = br.getRegex("<input type=\"hidden\" name=\"code\" value=\"(.*?)\"").getMatch(0);
            if (code == null) {
                code = br.getRegex("\\&amp;code=(.*?)\"").getMatch(0);
                if (code == null) {
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
            }
            br.postPage("http://sdilej.cz/profi_down.php", "id=" + contentID + "&code=" + code);
            dllink = br.getRedirectLocation();
        }
        if (dllink == null) {
            logger.warning("dllink is null...");
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl = jd.plugins.BrowserAdapter.openDownload(br, link, dllink, true, -10);
        if (!looksLikeDownloadableContent(dl.getConnection())) {
            br.followConnection(true);
            if (br.containsHTML("Soubor byl smazan")) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            } else if (br.containsHTML("Soubor je dočasně nedostupný\\.") || dl.getConnection().getContentType().contains("unknown")) {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error");
            } else if (dl.getConnection().getResponseCode() == 403) {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 403", 60 * 60 * 1000l);
            } else if (dl.getConnection().getResponseCode() == 404) {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 404", 60 * 60 * 1000l);
            } else {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
        }
        dl.startDownload();
    }

    @SuppressWarnings("unchecked")
    private void login(final Account account, final boolean force) throws Exception {
        synchronized (account) {
            try {
                prepBR(br);
                br.setCookiesExclusive(true);
                final Object ret = account.getProperty("cookies", null);
                boolean acmatch = Encoding.urlEncode(account.getUser()).equals(account.getStringProperty("name", Encoding.urlEncode(account.getUser())));
                if (acmatch) {
                    acmatch = Encoding.urlEncode(account.getPass()).equals(account.getStringProperty("pass", Encoding.urlEncode(account.getPass())));
                }
                if (acmatch && ret != null && ret instanceof Map<?, ?> && !force) {
                    final Map<String, String> cookies = (Map<String, String>) ret;
                    /* Removed 2015-10-14 */
                    // if (cookies.containsKey("SSL") && account.isValid()) {
                    if (account.isValid()) {
                        for (final Map.Entry<String, String> cookieEntry : cookies.entrySet()) {
                            final String key = cookieEntry.getKey();
                            final String value = cookieEntry.getValue();
                            this.br.setCookie(MAINPAGE, key, value);
                        }
                        return;
                    }
                }
                br.setFollowRedirects(true);
                br.getPage("http://sdilej.cz/prihlasit");
                // final URLConnectionAdapter con = this.br.openPostConnection("/index.php",
                // "trvale=on&Prihlasit=P%C5%99ihl%C3%A1sit&login-name=" + Encoding.urlEncode(account.getUser()) + "&login-password=" +
                // Encoding.urlEncode(account.getPass()));
                // con.disconnect();
                br.postPage("/sql.php", "login=" + Encoding.urlEncode(account.getUser()) + "&heslo=" + Encoding.urlEncode(account.getPass()));
                if (br.getCookie(MAINPAGE, "SDILEJ") == null) {
                    final String lang = System.getProperty("user.language");
                    if ("de".equalsIgnoreCase(lang)) {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nUngültiger Benutzername oder ungültiges Passwort!\r\nSchnellhilfe: \r\nDu bist dir sicher, dass dein eingegebener Benutzername und Passwort stimmen?\r\nFalls dein Passwort Sonderzeichen enthält, ändere es und versuche es erneut!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    } else {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nInvalid username/password!\r\nQuick help:\r\nYou're sure that the username and password you entered are correct?\r\nIf your password contains special characters, change it (remove them) and try again!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    }
                }
                // Save cookies
                final HashMap<String, String> cookies = new HashMap<String, String>();
                final Cookies add = this.br.getCookies(MAINPAGE);
                for (final Cookie c : add.getCookies()) {
                    cookies.put(c.getKey(), c.getValue());
                }
                account.setProperty("name", Encoding.urlEncode(account.getUser()));
                account.setProperty("pass", Encoding.urlEncode(account.getPass()));
                account.setProperty("cookies", cookies);
            } catch (PluginException e) {
                if (e.getLinkStatus() == LinkStatus.ERROR_PREMIUM) {
                    account.setProperty("cookies", null);
                }
                throw e;
            }
        }
    }

    @Override
    public AccountInfo fetchAccountInfo(final Account account) throws Exception {
        final AccountInfo ai = new AccountInfo();
        login(account, true);
        br.getPage("/upload/");
        final String trafficleft = br.getRegex("kredit: <strong>([^<>\"]*?)<").getMatch(0);
        if (trafficleft == null) {
            account.setType(AccountType.FREE);
        } else {
            account.setType(AccountType.PREMIUM);
            ai.setTrafficLeft(trafficleft.replace(",", "."));
        }
        return ai;
    }

    private Browser prepBR(final Browser br) {
        br.setCustomCharset("utf-8");
        br.setLoadLimit(br.getLoadLimit() * 5);
        return br;
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }

    @Override
    public void resetPluginGlobals() {
    }
}