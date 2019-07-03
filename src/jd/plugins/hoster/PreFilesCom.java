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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.appwork.utils.StringUtils;
import org.jdownloader.captcha.v2.challenge.recaptcha.v2.CaptchaHelperHostPluginRecaptchaV2;
import org.jdownloader.plugins.components.XFileSharingProBasic;

import jd.PluginWrapper;
import jd.http.Cookies;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.parser.html.Form;
import jd.plugins.Account;
import jd.plugins.Account.AccountType;
import jd.plugins.AccountUnavailableException;
import jd.plugins.DownloadLink;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;

@HostPlugin(revision = "$Revision$", interfaceVersion = 3, names = {}, urls = {})
public class PreFilesCom extends XFileSharingProBasic {
    public PreFilesCom(final PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium(super.getPurchasePremiumURL());
    }

    /**
     * DEV NOTES XfileSharingProBasic Version SEE SUPER-CLASS<br />
     * mods: See overridden functions<br />
     * limit-info: 2019-07-03: no limits at all<br />
     * captchatype-info: 2019-07-03: reCaptchaV2<br />
     * other:<br />
     */
    private static String[] domains = new String[] { "prefiles.com" };

    @Override
    public boolean isResumeable(final DownloadLink link, final Account account) {
        if (account != null && account.getType() == AccountType.FREE) {
            /* Free Account */
            return true;
        } else if (account != null && account.getType() == AccountType.PREMIUM) {
            /* Premium account */
            return true;
        } else {
            /* Free(anonymous) and unknown account type */
            return true;
        }
    }

    @Override
    public int getMaxChunks(final Account account) {
        if (account != null && account.getType() == AccountType.FREE) {
            /* Free Account */
            return 0;
        } else if (account != null && account.getType() == AccountType.PREMIUM) {
            /* Premium account */
            return 0;
        } else {
            /* Free(anonymous) and unknown account type */
            return 0;
        }
    }

    @Override
    public int getMaxSimultaneousFreeAnonymousDownloads() {
        return -1;
    }

    @Override
    public int getMaxSimultaneousFreeAccountDownloads() {
        return -1;
    }

    @Override
    public int getMaxSimultanPremiumDownloadNum() {
        return -1;
    }

    @Override
    public boolean isPremiumOnlyHTML() {
        /* 2019-07-03: Special */
        boolean premiumonly = super.isPremiumOnlyHTML();
        if (!premiumonly) {
            premiumonly = new Regex(correctedBR, "<p>Sorry, This file only can be downloaded by PRO Membership").matches();
        }
        return premiumonly;
    }

    @Override
    public boolean isLoggedin() {
        /* 2019-07-03: Special */
        boolean loggedin = super.isLoggedin();
        if (!loggedin) {
            final boolean login_xfss_CookieOkay = br.getCookie(getMainPage(), "xfss", Cookies.NOTDELETEDPATTERN) != null;
            /* 2019-06-21: Example website which uses email cookie: filefox.cc (so far the only known!) */
            final boolean logoutOkay = br.containsHTML("/logout");
            final boolean myAccount = br.containsHTML("/my-account");
            loggedin = (login_xfss_CookieOkay) && (logoutOkay || myAccount);
        }
        return loggedin;
    }

    @Override
    public void handleCaptcha(final DownloadLink link, final Form captchaForm) throws Exception {
        /* 2019-07-03: Special */
        if (br.containsHTML("google.com/recaptcha")) {
            logger.info("Detected captcha method \"RecaptchaV2\" type 'normal' for this host");
            final CaptchaHelperHostPluginRecaptchaV2 rc2 = new CaptchaHelperHostPluginRecaptchaV2(this, br);
            if (!this.preDownloadWaittimeSkippable()) {
                final String waitStr = regexWaittime();
                if (waitStr != null && waitStr.matches("\\d+")) {
                    final int waitSeconds = Integer.parseInt(waitStr);
                    final int reCaptchaV2TimeoutSeconds = rc2.getSolutionTimeout();
                    /* TODO: Remove hardcoded value, get reCaptchaV2 timeout from upper class as it can change in the future! */
                    if (waitSeconds > reCaptchaV2TimeoutSeconds) {
                        /*
                         * Admins may sometimes setup waittimes that are higher than the reCaptchaV2 timeout so lets say they set up 180
                         * seconds of pre-download-waittime --> User solves captcha immediately --> Captcha-solution times out after 120
                         * seconds --> User has to re-enter it (and it would fail in JD)! If admins set it up in a way that users can solve
                         * the captcha via the waittime counts down, this failure may even happen via browser (example: xubster.com)! See
                         * workaround below!
                         */
                        /*
                         * This is basically a workaround which avoids running into reCaptchaV2 timeout: Make sure that we wait less than
                         * 120 seconds after the user has solved the captcha. If the waittime is higher than 120 seconds, we'll wait two
                         * times: Before AND after the captcha!
                         */
                        final int prePreWait = waitSeconds % reCaptchaV2TimeoutSeconds;
                        logger.info("Waittime is higher than reCaptchaV2 timeout --> Waiting a part of it before solving captcha to avoid timeouts");
                        logger.info("Pre-pre download waittime seconds: " + prePreWait);
                        this.sleep(prePreWait * 1000l, link);
                    }
                }
            }
            final String recaptchaV2Response = rc2.getToken();
            captchaForm.put("g-recaptcha-response", Encoding.urlEncode(recaptchaV2Response));
        } else {
            super.handleCaptcha(link, captchaForm);
        }
    }

    @Override
    protected String regExTrafficLeft() {
        /* 2019-07-03: Special */
        String trafficleft = super.regExTrafficLeft();
        if (StringUtils.isEmpty(trafficleft)) {
            /* 2019-07-03: Free Accounts: According to this place, 5 GB (per day?) but another hint states 2 GB/day */
            trafficleft = new Regex(correctedBR, "Traffic Remaining</td>\\s*?<td>([^<>\"]+)</td>").getMatch(0);
        }
        return trafficleft;
    }

    @Override
    protected void checkErrors(final DownloadLink link, final Account account, final boolean checkAll) throws NumberFormatException, PluginException {
        /* 2019-07-03: Special */
        super.checkErrors(link, account, checkAll);
        if (new Regex(correctedBR, ">Your subsequent download will be started in").matches()) {
            /* adjust this regex to catch the wait time string for COOKIE_HOST */
            String wait = new Regex(correctedBR, ">Your subsequent download will be started in([^<>]+)").getMatch(0);
            String tmphrs = new Regex(wait, "\\s+(\\d+)\\s+hours?").getMatch(0);
            if (tmphrs == null) {
                tmphrs = new Regex(correctedBR, "You have to wait.*?\\s+(\\d+)\\s+hours?").getMatch(0);
            }
            String tmpmin = new Regex(wait, "\\s+(\\d+)\\s+minutes?").getMatch(0);
            if (tmpmin == null) {
                tmpmin = new Regex(correctedBR, "You have to wait.*?\\s+(\\d+)\\s+minutes?").getMatch(0);
            }
            String tmpsec = new Regex(wait, "\\s+(\\d+)\\s+seconds?").getMatch(0);
            String tmpdays = new Regex(wait, "\\s+(\\d+)\\s+days?").getMatch(0);
            if (tmphrs == null && tmpmin == null && tmpsec == null && tmpdays == null) {
                logger.info("Waittime regexes seem to be broken");
                if (account != null) {
                    throw new AccountUnavailableException("Download limit reached", 60 * 60 * 1000l);
                } else {
                    throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, null, 60 * 60 * 1000l);
                }
            } else {
                int minutes = 0, seconds = 0, hours = 0, days = 0;
                if (tmphrs != null) {
                    hours = Integer.parseInt(tmphrs);
                }
                if (tmpmin != null) {
                    minutes = Integer.parseInt(tmpmin);
                }
                if (tmpsec != null) {
                    seconds = Integer.parseInt(tmpsec);
                }
                if (tmpdays != null) {
                    days = Integer.parseInt(tmpdays);
                }
                int waittime = ((days * 24 * 3600) + (3600 * hours) + (60 * minutes) + seconds + 1) * 1000;
                logger.info("Detected waittime #2, waiting " + waittime + "milliseconds");
                /* Not enough wait time to reconnect -> Wait short and retry */
                if (waittime < 180000) {
                    throw new PluginException(LinkStatus.ERROR_HOSTER_TEMPORARILY_UNAVAILABLE, "Wait until new downloads can be started", waittime);
                }
                if (account != null) {
                    throw new AccountUnavailableException("Download limit reached", waittime);
                } else {
                    throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, null, waittime);
                }
            }
        }
    }

    @Override
    public boolean supports_https() {
        return super.supports_https();
    }

    @Override
    public boolean isVideohosterEmbed() {
        return super.isVideohosterEmbed();
    }

    @Override
    public boolean isVideohoster_enforce_video_filename() {
        return super.isVideohoster_enforce_video_filename();
    }

    @Override
    public boolean supports_availablecheck_alt() {
        /* 2019-07-03: Special */
        return false;
    }

    @Override
    public boolean supports_availablecheck_filename_abuse() {
        return super.supports_availablecheck_filename_abuse();
    }

    @Override
    public boolean supports_availablecheck_filesize_html() {
        return super.supports_availablecheck_filesize_html();
    }

    @Override
    public boolean supports_availablecheck_filesize_via_embedded_video() {
        return super.supports_availablecheck_filesize_via_embedded_video();
    }

    public static String[] getAnnotationNames() {
        return domains;
    }

    @Override
    public String[] siteSupportedNames() {
        return domains;
    }

    public static String[] getAnnotationUrls() {
        final List<String> ret = new ArrayList<String>();
        for (int i = 0; i < domains.length; i++) {
            if (i == 0) {
                /* Match all URLs on first (=current) domain */
                ret.add("https?://(?:www\\.)?" + getHostsPatternPart() + XFileSharingProBasic.getDefaultAnnotationPatternPart());
            } else {
                ret.add("");
            }
        }
        return ret.toArray(new String[0]);
    }

    /** Returns '(?:domain1|domain2)' */
    public static String getHostsPatternPart() {
        final StringBuilder pattern = new StringBuilder();
        pattern.append("(?:");
        for (final String name : domains) {
            pattern.append((pattern.length() > 0 ? "|" : "") + Pattern.quote(name));
        }
        pattern.append(")");
        return pattern.toString();
    }
}