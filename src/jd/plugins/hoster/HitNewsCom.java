package jd.plugins.hoster;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import jd.PluginWrapper;
import jd.http.Cookies;
import jd.nutils.encoding.Encoding;
import jd.parser.html.Form;
import jd.plugins.Account;
import jd.plugins.Account.AccountType;
import jd.plugins.AccountInfo;
import jd.plugins.AccountUnavailableException;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;

import org.appwork.utils.Regex;
import org.appwork.utils.StringUtils;
import org.appwork.utils.formatter.TimeFormatter;
import org.jdownloader.plugins.components.usenet.UsenetAccountConfigInterface;
import org.jdownloader.plugins.components.usenet.UsenetServer;

@HostPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "hitnews.com" }, urls = { "" })
public class HitNewsCom extends UseNet {
    public HitNewsCom(PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium("https://member.hitnews.com/signup.php");
    }

    public static interface HitNewsConfigInterface extends UsenetAccountConfigInterface {
    };

    @Override
    public String getAGBLink() {
        return "http://www.hitnews.com/index.php?id=41";
    }

    @Override
    public AccountInfo fetchAccountInfo(Account account) throws Exception {
        setBrowserExclusive();
        final AccountInfo ai = new AccountInfo();
        br.setFollowRedirects(true);
        final Cookies cookies = account.loadCookies("");
        try {
            Form login = null;
            if (cookies != null) {
                br.setCookies(getHost(), cookies);
                br.getPage("https://member.hitnews.com/member.php");
                login = br.getFormbyActionRegex("/member\\.php");
                if (login != null && login.containsHTML("amember_login") && login.containsHTML("amember_pass")) {
                    br.getCookies(getHost()).clear();
                } else if (br.getCookie(getHost(), "PHPSESSID") == null) {
                    br.getCookies(getHost()).clear();
                } else if (br.getRegex(">Your payment history<.*?<tr>.*?</tr>\\s*<tr.*?>\\s*<td>\\s*(.*?)\\s*</td>\\s*<td.*?>\\s*(\\d+/\\d+/\\d+)\\s*</td>\\s*<td.*?>\\s*(\\d+/\\d+/\\d+)\\s*</td>").getRow(0) == null) {
                    br.getCookies(getHost()).clear();
                }
            }
            if (br.getCookie(getHost(), "PHPSESSID") == null) {
                account.clearCookies("");
                br.getPage("https://member.hitnews.com/login");
                final String pleaseWait = br.getRegex("Please wait\\s*(\\d+)\\s*seconds before next login attempt").getMatch(0);
                if (pleaseWait != null) {
                    throw new AccountUnavailableException("Please wait before next login attempt", Integer.parseInt(pleaseWait) * 1000l);
                }
                login = br.getFormbyKey("login_attempt_id");
                login.put("amember_login", Encoding.urlEncode(account.getUser()));
                login.put("amember_pass", Encoding.urlEncode(account.getPass()));
                br.submitForm(login);
                login = br.getFormbyKey("login_attempt_id");
                if (login != null && login.containsHTML("amember_login") && login.containsHTML("amember_pass")) {
                    final String errmsg = br.getRegex("class\\s*=\\s*\"am-errors\">\\s*<li>\\s*(.*?)\\s*</li>").getMatch(0);
                    if (errmsg != null) {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, errmsg, PluginException.VALUE_ID_PREMIUM_DISABLE);
                    } else {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_DISABLE);
                    }
                } else if (br.getCookie(getHost(), "PHPSESSID", Cookies.NOTDELETEDPATTERN) == null) {
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_DISABLE);
                }
            }
            account.saveCookies(br.getCookies(getHost()), "");
            final String paymentHistory = br.getRegex(">\\s*Your payment history\\s*<.*<table.*?>(.*?)</table>").getMatch(0);
            final String packageInfos[][] = new Regex(paymentHistory, "<tr[^>]*>\\s*<td>\\s*(.*?)\\s*</td>\\s*<td[^>]*>\\s*(\\d+/\\d+/\\d+)\\s*</td>\\s*<td[^>]*>\\s*(\\d+/\\d+/\\d+)\\s*</td>").getMatches();
            if (packageInfos == null || packageInfos.length == 0) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            for (String packageInfo[] : packageInfos) {
                final String product = packageInfo[0];
                ai.setStatus(product);
                final String expireDate = packageInfo[2];
                if (StringUtils.containsIgnoreCase(product, "free")) {
                    account.setMaxSimultanDownloads(30);
                    account.setType(AccountType.FREE);
                    ai.setStatus("Premium: " + product);
                } else {
                    account.setType(AccountType.PREMIUM);
                    if (StringUtils.contains(product, "1000 mbit")) {
                        account.setMaxSimultanDownloads(30);
                    } else if (StringUtils.contains(product, "120 mbit")) {
                        account.setMaxSimultanDownloads(20);
                    } else if (StringUtils.contains(product, "10 mbit")) {
                        account.setMaxSimultanDownloads(8);
                    } else if (StringUtils.contains(product, "500")) {
                        account.setMaxSimultanDownloads(30);
                    } else if (StringUtils.contains(product, "200")) {
                        account.setMaxSimultanDownloads(30);
                    } else if (StringUtils.contains(product, "120")) {
                        account.setMaxSimultanDownloads(30);
                    } else if (StringUtils.contains(product, "80")) {
                        account.setMaxSimultanDownloads(30);
                    } else if (StringUtils.contains(product, "50")) {
                        account.setMaxSimultanDownloads(20);
                    } else if (StringUtils.contains(product, "25")) {
                        account.setMaxSimultanDownloads(8);
                    } else if (StringUtils.contains(product, "10")) {
                        account.setMaxSimultanDownloads(8);
                    } else if (StringUtils.contains(product, "2,5")) {
                        account.setMaxSimultanDownloads(2);
                    } else if (StringUtils.contains(product, "5")) {
                        account.setMaxSimultanDownloads(4);
                    } else if (StringUtils.containsIgnoreCase(product, "night")) {
                        account.setMaxSimultanDownloads(30);
                    } else if (StringUtils.containsIgnoreCase(product, "high")) {
                        account.setMaxSimultanDownloads(30);
                    } else {
                        // unknown
                        account.setMaxSimultanDownloads(2);
                    }
                }
                ai.setValidUntil(TimeFormatter.getMilliSeconds(expireDate, "dd'/'MM'/'yyyy", Locale.ENGLISH) + (24 * 60 * 60 * 1000l));
                if (!ai.isExpired()) {
                    break;
                }
            }
        } catch (final PluginException e) {
            if (e.getLinkStatus() == LinkStatus.ERROR_PREMIUM) {
                account.clearCookies("");
            }
            throw e;
        }
        ai.setProperty("multiHostSupport", Arrays.asList(new String[] { "usenet" }));
        return ai;
    }

    @Override
    protected UsenetServer getUseNetServer(final Account account) throws Exception {
        final UsenetServer ret = super.getUseNetServer(account);
        if (AccountType.FREE.equals(account.getType())) {
            if (ret.isSSL()) {
                return new UsenetServer("free.hitnews.com", 563, true);
            } else {
                return new UsenetServer("free.hitnews.com", 119, false);
            }
        }
        return ret;
    }

    @Override
    public List<UsenetServer> getAvailableUsenetServer() {
        final List<UsenetServer> ret = new ArrayList<UsenetServer>();
        ret.addAll(UsenetServer.createServerList("news.hitnews.com", false, 119, 80));
        ret.addAll(UsenetServer.createServerList("ssl.hitnews.com", true, 563, 995));
        return ret;
    }
}
