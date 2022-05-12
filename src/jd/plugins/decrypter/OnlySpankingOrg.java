package jd.plugins.decrypter;

import java.util.ArrayList;
import java.util.List;

import org.jdownloader.captcha.v2.challenge.recaptcha.v2.CaptchaHelperCrawlerPluginRecaptchaV2;
import org.jdownloader.plugins.components.antiDDoSForDecrypt;

import jd.PluginWrapper;
import jd.controlling.AccountController;
import jd.controlling.ProgressController;
import jd.http.Browser;
import jd.nutils.encoding.Encoding;
import jd.parser.html.Form;
import jd.parser.html.Form.MethodType;
import jd.plugins.Account;
import jd.plugins.Account.AccountType;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.components.SiteType.SiteTemplate;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 3, names = {}, urls = {})
public class OnlySpankingOrg extends antiDDoSForDecrypt {
    public OnlySpankingOrg(PluginWrapper wrapper) {
        super(wrapper);
    }

    public static List<String[]> getPluginDomains() {
        final List<String[]> ret = new ArrayList<String[]>();
        // each entry in List<String[]> will result in one PluginForDecrypt, Plugin.getHost() will return String[0]->main domain
        ret.add(new String[] { "onlyspanking.video", "onlyspanking.org" });
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
        return buildAnnotationUrls(getPluginDomains());
    }

    public static String[] buildAnnotationUrls(final List<String[]> pluginDomains) {
        final List<String> ret = new ArrayList<String>();
        for (final String[] domains : pluginDomains) {
            ret.add("https?://(?:www\\.)?" + buildHostsPatternPart(domains) + "/\\d+-[a-zA-Z0-9\\-_]+\\.html");
        }
        return ret.toArray(new String[0]);
    }

    @Override
    public ArrayList<DownloadLink> decryptIt(final CryptedLink param, ProgressController progress) throws Exception {
        final ArrayList<DownloadLink> ret = new ArrayList<DownloadLink>();
        br.setFollowRedirects(true);
        param.setCryptedUrl(param.getCryptedUrl().replace("http://", "https://"));
        getPage(param.getCryptedUrl());
        final String startURL = br.getURL();
        final String dle_skin = br.getRegex("var\\s*dle_skin\\s*=\\s*'(.*?)'").getMatch(0);
        if (dle_skin == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        } else if (!br.containsHTML("<meta property\\s*=\\s*\"og:title\"")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        String ajax_action = null;
        boolean success = false;
        for (int i = 0; i <= 2; i++) {
            /* Important! */
            br.getHeaders().put("Referer", startURL);
            getPage(br, "/engine/ajax/getlink2.php");
            ajax_action = br.getURL();
            final Form captchaform = getCaptchaForm(br);
            if (captchaform == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            if (captchaform.containsHTML("id\\s*=\\s*\"getlink\"") && captchaform.containsHTML("data-sitekey")) {
                final String recaptchaV2Response = new CaptchaHelperCrawlerPluginRecaptchaV2(this, br, "6Le7b3AUAAAAADGhizVG-ZB_jxfOha9WgXP-ahZd").getToken();
                captchaform.put("g-recaptcha-response", Encoding.urlEncode(recaptchaV2Response));
                captchaform.put("skin", Encoding.urlEncode(dle_skin));
                captchaform.put("sec_code", Encoding.urlEncode(recaptchaV2Response));
                /* Important! */
                br.getHeaders().put("Referer", startURL);
                submitForm(br, captchaform);
                /* Do not allow retries for reCaptcha and assume it is solved correctly with one attempt. */
                success = true;
                break;
            } else {
                /* Simple image captcha */
                // captchaform.setAction(ajax_action);
                final String code = getCaptchaCode(br, getHost(), "/engine/modules/antibot/antibot.php?rndval=" + System.currentTimeMillis(), param);
                captchaform.put("sec_code", code);
                captchaform.put("skin", Encoding.urlEncode(dle_skin));
                /* Important! */
                br.getHeaders().put("Referer", startURL);
                submitForm(br, captchaform);
            }
            if (br.getRequest().getHtmlCode().length() == 0) {
                /* Empty page = Wrong captcha */
                logger.info("Wrong captcha | Run: " + i);
                continue;
            } else {
                success = true;
                break;
            }
        }
        if (!success) {
            throw new PluginException(LinkStatus.ERROR_CAPTCHA);
        }
        String finallink = null;
        final String redirect = br.getRegex("(https?://[^/]+/(video|file|vfile)/[a-zA-Z0-9]+)").getMatch(0);
        if (redirect == null) {
            if (br.containsHTML("(?i)To access the exclusive category you need to purchase")) {
                /*
                 * Special case: Users who own a premium account of a specified OCH can auth themselves as premium here to get the
                 * downloadlinks!
                 */
                String ubiqfile_premium_mail = null;
                final ArrayList<Account> accs = AccountController.getInstance().getValidAccounts("ubiqfile.com");
                for (final Account acc : accs) {
                    final String accMailTmp = acc.getStringProperty("PROPERTY_UBIQFILE_MAIL", null);
                    if (acc.getType() == AccountType.PREMIUM && accMailTmp != null) {
                        ubiqfile_premium_mail = accMailTmp;
                        break;
                    }
                }
                if (ubiqfile_premium_mail == null) {
                    logger.info("Content is premiumonly and user does not own premium access");
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_ONLY);
                }
                logger.info("Content is premiumonly and user should have premium access via mail: " + ubiqfile_premium_mail);
                final Form premiumForm = new Form();
                premiumForm.setMethod(MethodType.POST);
                premiumForm.setAction(ajax_action);
                premiumForm.put("skin", Encoding.urlEncode(dle_skin));
                premiumForm.put("email", Encoding.urlEncode(ubiqfile_premium_mail));
                /* Important! */
                br.getHeaders().put("Referer", startURL);
                this.submitForm(br, premiumForm);
                finallink = br.getRegex("href=\"(https?[^\"]+)\"[^<>\"]*?target=\"_blank\" rel=\"external noopener\"").getMatch(0);
                if (finallink == null) {
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
            } else {
                throw new PluginException(LinkStatus.ERROR_CAPTCHA);
            }
        }
        if (finallink == null) {
            br.setFollowRedirects(false);
            this.getPage(redirect);
            finallink = br.getRedirectLocation();
        }
        if (finallink == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        } else {
            ret.add(createDownloadlink(finallink));
            return ret;
        }
    }

    private Form getCaptchaForm(final Browser br) {
        return br.getFormbyProperty("name", "getlink");
    }

    @Override
    public SiteTemplate siteTemplateType() {
        return SiteTemplate.AntiBotCMS;
    }
}
