package jd.plugins.hoster;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.appwork.utils.Regex;
import org.appwork.utils.StringUtils;
import org.appwork.utils.formatter.SizeFormatter;
import org.appwork.utils.formatter.TimeFormatter;
import org.jdownloader.captcha.v2.challenge.recaptcha.v2.CaptchaHelperHostPluginRecaptchaV2;
import org.jdownloader.plugins.components.antiDDoSForHost;
import org.jdownloader.plugins.controller.host.LazyHostPlugin.FEATURE;

import jd.PluginWrapper;
import jd.config.ConfigContainer;
import jd.config.ConfigEntry;
import jd.http.Browser;
import jd.http.Cookies;
import jd.http.URLConnectionAdapter;
import jd.http.requests.PostRequest;
import jd.parser.html.Form;
import jd.plugins.Account;
import jd.plugins.Account.AccountType;
import jd.plugins.AccountInfo;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.components.MultiHosterManagement;

@HostPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "proleech.link" }, urls = { "https?://proleech\\.link/download/[a-zA-Z0-9]+(/.*)?" })
public class ProLeechLink extends antiDDoSForHost {
    public ProLeechLink(PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium("https://proleech.link/signup");
        setConfigElements();
    }

    private static MultiHosterManagement mhm = new MultiHosterManagement("proleech.link");

    @Override
    public String getAGBLink() {
        return "https://proleech.link/page/terms";
    }

    @Override
    public AvailableStatus requestFileInformation(DownloadLink parameter) throws Exception {
        final String fileName = new Regex(parameter.getPluginPatternMatcher(), "download/[a-zA-Z0-9]+/([^/\\?]+)").getMatch(0);
        if (fileName != null && !parameter.isNameSet()) {
            parameter.setName(fileName);
        }
        return AvailableStatus.UNCHECKABLE;
    }

    @Override
    public AccountInfo fetchAccountInfo(Account account) throws Exception {
        final AccountInfo ai = new AccountInfo();
        login(account, ai, true);
        /* Contains all filehosts available for free account users regardless of their status */
        String[] filehosts_free = null;
        /* Contains all filehosts available for premium users and listed as online/working */
        String[] filehosts_premium_online = null;
        List<String> filehosts_premium_onlineArray = new ArrayList<String>();
        /* Contains all filehosts available for premium users and listed as online/working */
        List<String> filehosts_free_onlineArray = new ArrayList<String>();
        String maxTrafficPremiumDailyStr = null;
        {
            /* Grab free hosts */
            if (br.getURL() == null || !br.getURL().contains("/downloader")) {
                this.getPage("/downloader");
            }
            final String html_free_filehosts = br.getRegex("<section id=\"content\">.*?Free Filehosters</div>.*?</section>").getMatch(-1);
            filehosts_free = new Regex(html_free_filehosts, "domain=([^\"]+)").getColumn(0);
        }
        {
            /* Grab premium hosts */
            getPage("/page/hostlist");
            filehosts_premium_online = br.getRegex("<td>\\s*\\d+\\s*</td>\\s*<td>\\s*<img[^<]+/?>\\s*([^<]*?)\\s*</td>\\s*<td>\\s*<span\\s*class\\s*=\\s*\"label\\s*label-success\"\\s*>\\s*Online").getColumn(0);
            if (filehosts_premium_online == null || filehosts_premium_online.length == 0) {
                logger.warning("Failed to find list of supported hosts");
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            for (final String filehost_premium_online : filehosts_premium_online) {
                if (filehost_premium_online.contains("/")) {
                    /* 2019-11-11: WTF They sometimes display multiple domains of one filehost in one entry, separated by ' / ' */
                    logger.info("Special case: Multiple domains of one filehost given: " + filehost_premium_online);
                    final String[] filehost_domains = filehost_premium_online.split("/");
                    for (String filehost_domain : filehost_domains) {
                        filehost_domain = filehost_domain.trim();
                        filehosts_premium_onlineArray.add(filehost_domain);
                    }
                } else {
                    filehosts_premium_onlineArray.add(filehost_premium_online);
                }
            }
            /* 2019-11-11: New: Max daily traffic value [80 GB at this moment] */
            maxTrafficPremiumDailyStr = br.getRegex("(\\d+(?:\\.\\d+)? GB) Daily Traff?ic").getMatch(0);
        }
        /* Set supported hosts depending on account type */
        if (account.getType() == AccountType.PREMIUM && !ai.isExpired()) {
            /* Premium account - bigger list of supported hosts */
            ai.setMultiHostSupport(this, filehosts_premium_onlineArray);
            if (maxTrafficPremiumDailyStr != null) {
                ai.setTrafficLeft(SizeFormatter.getSize(maxTrafficPremiumDailyStr));
                // ai.setTrafficMax(SizeFormatter.getSize(maxTrafficPremiumDailyStr));
            }
        } else {
            /* Free & Expired[=Free] accounts - they support much less hosts */
            if (filehosts_free != null && filehosts_free.length != 0) {
                /*
                 * We only see the online status of each host in the big list. Now we have to find out which of all free filehosts are
                 * currently online/supported.
                 */
                for (final String host : filehosts_free) {
                    if (filehosts_premium_onlineArray.contains(host)) {
                        /* Filehost should be supported/online --> Add to the list we will later set */
                        filehosts_free_onlineArray.add(host);
                    }
                }
                ai.setMultiHostSupport(this, filehosts_free_onlineArray);
            }
        }
        return ai;
    }

    private boolean isLoggedin(final Browser br) throws PluginException {
        final boolean cookie_ok_amember_nr = br.getCookie("amember_nr", Cookies.NOTDELETEDPATTERN) != null;
        final boolean cookie_ok_amember_ru = br.getCookie("amember_ru", Cookies.NOTDELETEDPATTERN) != null;
        final boolean cookie_ok_amember_rp = br.getCookie("amember_rp", Cookies.NOTDELETEDPATTERN) != null;
        final boolean cookies_ok = cookie_ok_amember_nr && cookie_ok_amember_ru && cookie_ok_amember_rp;
        final boolean html_ok = br.containsHTML("/logout");
        logger.info("cookies_ok = " + cookies_ok);
        logger.info("html_ok = " + html_ok);
        /* 2019-11-11: Allow validation via cookies OR html code! */
        if (cookies_ok || html_ok) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * @param validateCookies
     *            true = Check whether stored cookies are still valid, if not, perform full login <br/>
     *            false = Set stored cookies and trust them if they're not older than 300000l
     *
     */
    private boolean login(Account account, AccountInfo ai, final boolean validateCookies) throws Exception {
        synchronized (account) {
            try {
                final Cookies cookies = account.loadCookies("");
                boolean loggedIN = false;
                if (cookies != null) {
                    br.setCookies(getHost(), cookies);
                    if (System.currentTimeMillis() - account.getCookiesTimeStamp("") <= 300000l && !validateCookies) {
                        /* We trust these cookies as they're not that old --> Do not check them */
                        logger.info("Trust login-cookies without checking as they should still be fresh");
                        return false;
                    }
                    getPage("https://" + this.getHost() + "/member");
                    br.followRedirect();
                    loggedIN = this.isLoggedin(this.br);
                }
                if (!loggedIN) {
                    logger.info("Performing full login");
                    br.clearCookies(getHost());
                    getPage("https://" + this.getHost());
                    getPage("/login");
                    final Form loginform = br.getFormbyAction("/login");
                    if (loginform == null) {
                        logger.warning("Failed to find loginform");
                        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                    }
                    loginform.put("amember_login", URLEncoder.encode(account.getUser(), "UTF-8"));
                    loginform.put("amember_pass", URLEncoder.encode(account.getPass(), "UTF-8"));
                    /* 2019-11-10: Captcha required for logging in RE: admin (this was a reaction to a DDoS attack) */
                    final boolean force_login_captcha = true;
                    if (loginform.containsHTML("recaptcha") || force_login_captcha) {
                        /* 2019-11-10: New */
                        final DownloadLink dlinkbefore = this.getDownloadLink();
                        try {
                            final DownloadLink dl_dummy;
                            if (dlinkbefore != null) {
                                dl_dummy = dlinkbefore;
                            } else {
                                dl_dummy = new DownloadLink(this, "Account", this.getHost(), "https://" + account.getHoster(), true);
                                this.setDownloadLink(dl_dummy);
                            }
                            final String recaptchaV2Response = new CaptchaHelperHostPluginRecaptchaV2(this, br).getToken();
                            loginform.put("g-recaptcha-response", URLEncoder.encode(recaptchaV2Response, "UTF-8"));
                        } finally {
                            this.setDownloadLink(dlinkbefore);
                        }
                    }
                    submitForm(loginform);
                    br.followRedirect();
                    if (!br.getURL().contains("/member")) {
                        getPage("/member");
                    }
                    if (!isLoggedin(this.br)) {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_DISABLE);
                    }
                }
                final String activeSubscription = br.getRegex("am-list-subscriptions\">\\s*<li[^<]*>(.*?)</li>").getMatch(0);
                String accountStatus = null;
                if (activeSubscription != null) {
                    final String expireDate = new Regex(activeSubscription, "([a-zA-Z]+\\s*\\d+,\\s*\\d{4})").getMatch(0);
                    if (expireDate != null) {
                        final long validUntil = TimeFormatter.getMilliSeconds(expireDate, "MMM' 'dd', 'yyyy", Locale.ENGLISH);
                        if (ai != null) {
                            ai.setValidUntil(validUntil);
                        }
                        account.setType(AccountType.PREMIUM);
                        accountStatus = "Premium user";
                        account.setConcurrentUsePossible(true);
                        account.setMaxSimultanDownloads(-1);
                    }
                } else {
                    account.setType(AccountType.FREE);
                    accountStatus = "Free user";
                    account.setConcurrentUsePossible(true);
                    if (ai != null) {
                        /* Only get/set hostlist if we're not currently trying to download a file (quick login) */
                        getPage("/downloader");
                        int maxfiles_per_day_used = 0;
                        int maxfiles_per_day_maxvalue = 0;
                        final Regex maxfiles_per_day = br.getRegex("<li>Files per day:\\s*?<b>\\s*?(\\d+)?\\s*?/\\s*?(\\d+)\\s*?</li>");
                        final String maxfiles_per_day_usedStr = maxfiles_per_day.getMatch(0);
                        final String maxfiles_per_day_maxvalueStr = maxfiles_per_day.getMatch(1);
                        final String max_free_filesize = br.getRegex("<li>Max\\. Filesize: <b>(\\d+ [^<>\"]+)</li>").getMatch(0);
                        if (maxfiles_per_day_usedStr != null) {
                            maxfiles_per_day_used = Integer.parseInt(maxfiles_per_day_usedStr);
                        }
                        if (maxfiles_per_day_maxvalueStr != null) {
                            maxfiles_per_day_maxvalue = Integer.parseInt(maxfiles_per_day_maxvalueStr);
                        }
                        /* ok = "/2" or "1/2", not ok = "2/2" */
                        if (max_free_filesize != null && maxfiles_per_day_maxvalue > 0) {
                            final int files_this_day_remaining = maxfiles_per_day_maxvalue - maxfiles_per_day_used;
                            if (files_this_day_remaining > 0) {
                                ai.setTrafficLeft(SizeFormatter.getSize(max_free_filesize));
                                /* 2019-08-14: Max files per day = 2 so a limit of 1 should be good! */
                                account.setMaxSimultanDownloads(1);
                            } else {
                                /*
                                 * No links remaining --> Display account with ZERO traffic left as it cannot be used to download at the
                                 * moment!
                                 */
                                logger.info("Cannot use free account because the max number of dail downloads has already been reached");
                                ai.setTrafficLeft(0);
                                account.setMaxSimultanDownloads(0);
                            }
                            accountStatus += " [" + files_this_day_remaining + "/" + maxfiles_per_day_maxvalueStr + " daily downloads remaining]";
                        } else {
                            logger.info("Cannot use free account because we failed to find any information about the current account limits");
                            account.setMaxSimultanDownloads(0);
                            ai.setTrafficLeft(0);
                            accountStatus += " [No downloads possible]";
                        }
                    }
                }
                if (ai != null) {
                    ai.setStatus(accountStatus);
                }
                account.saveCookies(br.getCookies(br.getHost()), "");
                return true;
            } catch (PluginException e) {
                if (e.getLinkStatus() == LinkStatus.ERROR_PREMIUM) {
                    account.clearCookies("");
                }
                throw e;
            }
        }
    }

    @Override
    public void handleMultiHost(final DownloadLink link, final Account account) throws Exception {
        mhm.runCheck(account, link);
        /* 2019-11-11: Login is not required to check previously generated directurls */
        // login(account, null, false);
        final String generatedDownloadURL = link.getStringProperty(getHost(), null);
        String downloadURL = null;
        if (generatedDownloadURL != null) {
            /*
             * 2019-11-11: Seems like generated downloadurls are only valid for some seconds after genration but let's try to re-use them
             * anyways!
             */
            logger.info("Trying to re-use old generated downloadlink");
            try {
                dl = jd.plugins.BrowserAdapter.openDownload(br, link, generatedDownloadURL, true, 0);
                final boolean isOkay = isDownloadConnection(dl.getConnection());
                if (!isOkay) {
                    /* 2019-11-11: E.g. "Link expired! Please leech again." */
                    logger.info("Saved downloadurl did not work");
                    try {
                        br.followConnection(true);
                    } catch (final IOException e) {
                        logger.log(e);
                    }
                } else {
                    downloadURL = generatedDownloadURL;
                }
            } catch (InterruptedException e) {
                throw e;
            } catch (Exception e) {
                logger.log(e);
            }
        }
        if (downloadURL == null) {
            logger.info("Trying to generate new downloadlink");
            final long userDefinedWaitHours = this.getPluginConfig().getLongProperty("DOWNLOADLINK_GENERATION_LIMIT", 0);
            final long timestamp_next_downloadlink_generation_allowed = link.getLongProperty("PROLEECH_TIMESTAMP_LAST_SUCCESSFUL_DOWNLOADLINK_CREATION", 0) + (userDefinedWaitHours * 60 * 60 * 1000);
            if (userDefinedWaitHours > 0 && timestamp_next_downloadlink_generation_allowed > System.currentTimeMillis()) {
                final long waittime_until_next_downloadlink_generation_is_allowed = timestamp_next_downloadlink_generation_allowed - System.currentTimeMillis();
                final String waittime_until_next_downloadlink_generation_is_allowed_Str = TimeFormatter.formatSeconds(waittime_until_next_downloadlink_generation_is_allowed / 1000, 0);
                logger.info("Next downloadlink generation is allowed in: " + waittime_until_next_downloadlink_generation_is_allowed_Str);
                /*
                 * 2019-08-14: Set a small waittime here so links can be tried earlier again - so not set the long waittime
                 * waittime_until_next_downloadlink_generation_is_allowed!
                 */
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Next downloadlink generation is allowed in " + waittime_until_next_downloadlink_generation_is_allowed_Str, 5 * 60 * 1000l);
            }
            /* Login - first try without validating cookies! */
            final boolean validatedCookies = login(account, null, false);
            final PostRequest post = new PostRequest("https://" + this.getHost() + "/dl/debrid/deb_process.php");
            post.setContentType("application/x-www-form-urlencoded; charset=UTF-8");
            post.getHeaders().put("X-Requested-With", "XMLHttpRequest");
            final String url = link.getDefaultPlugin().buildExternalDownloadURL(link, this);
            post.put("urllist", URLEncoder.encode(url, "UTF-8"));
            final String pass = link.getDownloadPassword();
            if (StringUtils.isEmpty(pass)) {
                post.put("pass", "");
            } else {
                post.put("pass", URLEncoder.encode(pass, "UTF-8"));
            }
            post.put("boxlinklist", "0");
            sendRequest(post);
            downloadURL = getDllink();
            if (StringUtils.isEmpty(downloadURL) && !validatedCookies && !this.isLoggedin(this.br)) {
                /* Bad login - try again with fresh / validated cookies! */
                login(account, null, true);
                sendRequest(post);
                downloadURL = getDllink();
            }
            if (StringUtils.isEmpty(downloadURL)) {
                final String danger = br.getRegex("class=\"[^\"]*danger\".*?<b>\\s*(.*?)\\s*</b>").getMatch(0);
                if (danger != null) {
                    /* 2019-11-11: Too many requests! Please try again in a few seconds. */
                    logger.info("Found errormessage on website:");
                    logger.info(danger);
                }
                if (br.containsHTML(">\\s*?No link entered\\.?\\s*<")) {
                    mhm.handleErrorGeneric(account, link, "no_link_entered", 50, 2 * 60 * 1000l);
                } else if (br.containsHTML(">\\s*Error getting the link from this account")) {
                    mhm.putError(account, link, 2 * 60 * 1000l, "Error getting the link from this account");
                } else if (br.containsHTML(">\\s*Our account has reached traffic limit")) {
                    mhm.putError(account, link, 2 * 60 * 1000l, "Error getting the link from this account");
                } else if (br.containsHTML(">\\s*This filehost is only enabled in")) {
                    mhm.putError(account, link, 10 * 60 * 1000l, "This filehost is only available in premium mode");
                } else if (br.containsHTML(">\\s*You can only generate this link during Happy Hours")) {
                    /* 2019-08-15: Can happen in free account mode - no idea when this "Happy Hour" is. Tested with uptobox.com URLs. */
                    throw new PluginException(LinkStatus.ERROR_HOSTER_TEMPORARILY_UNAVAILABLE, "You can only generate this link during Happy Hours", 5 * 60 * 1000l);
                }
                mhm.handleErrorGeneric(account, link, "dllinknull", 50, 2 * 60 * 1000l);
            }
            link.setProperty("PROLEECH_TIMESTAMP_LAST_SUCCESSFUL_DOWNLOADLINK_CREATION", System.currentTimeMillis());
            dl = jd.plugins.BrowserAdapter.openDownload(br, link, downloadURL, true, 0);
            final boolean isOkay = isDownloadConnection(dl.getConnection());
            if (!isOkay) {
                try {
                    br.followConnection(true);
                } catch (final IOException e) {
                    logger.log(e);
                }
                mhm.handleErrorGeneric(account, link, "unknowndlerror", 50, 2 * 60 * 1000l);
            }
        }
        link.setProperty(getHost(), downloadURL);
        dl.startDownload();
    }

    private String getDllink() {
        return br.getRegex("class=\"[^\"]*success\".*?<a href\\s*=\\s*\"(https?://.*?)\"").getMatch(0);
    }

    private boolean isDownloadConnection(URLConnectionAdapter con) throws IOException {
        final boolean ret = con.isOK() && (con.isContentDisposition() || StringUtils.containsIgnoreCase(con.getContentType(), "application/force-download"));
        return ret;
    }

    @Override
    public FEATURE[] getFeatures() {
        return new FEATURE[] { FEATURE.MULTIHOST };
    }

    @Override
    public void handlePremium(final DownloadLink link, final Account account) throws Exception {
        /** TODO: Maybe optimize this to not always validate cookies! */
        login(account, null, true);
        dl = jd.plugins.BrowserAdapter.openDownload(br, link, link.getPluginPatternMatcher(), true, 0);
        final boolean isOkay = isDownloadConnection(dl.getConnection());
        if (!isOkay) {
            try {
                br.followConnection(true);
            } catch (final IOException e) {
                logger.log(e);
            }
            if (StringUtils.endsWithCaseInsensitive(br.getURL(), "/downloader")) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            } else {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Unknown server error");
            }
        }
        dl.startDownload();
    }

    private void setConfigElements() {
        /* Crawler settings */
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_SPINNER, getPluginConfig(), "DOWNLOADLINK_GENERATION_LIMIT", "Allow new downloadlink generation every X hours (default = 0 = unlimited/disabled)\r\nThis can save traffic but this can also slow down the download process", 0, 72, 1).setDefaultValue(0));
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return 0;
    }

    @Override
    public void handleFree(DownloadLink link) throws Exception {
        throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_ONLY);
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }
}
