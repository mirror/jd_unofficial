package jd.plugins.decrypter;

import java.util.ArrayList;

import org.appwork.utils.StringUtils;

import jd.controlling.ProgressController;
import jd.http.Browser;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.PluginForDecrypt;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "ilikecomix.io" }, urls = { "https?://(?:www\\.)?(?:porncomix\\.info|bestporncomix\\.com|porncomix\\.one|ilikecomix\\.io)/([a-z]{2}/)?comic-g/([a-z0-9\\-]+)/?" })
public class PornComixInfo extends PluginForDecrypt {
    @Override
    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        final ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        br.setFollowRedirects(true);
        String addedurl = param.getCryptedUrl();
        /* 2020-11-13: Main domain has changed from porncomix.info --> ilikecomix.io */
        addedurl = addedurl.replace(Browser.getHost(addedurl) + "/", this.getHost() + "/");
        br.getPage(addedurl);
        if (br.getHttpConnection().getResponseCode() == 404) {
            decryptedLinks.add(this.createOfflinelink(param.getCryptedUrl()));
            return decryptedLinks;
        }
        final String urltitle = new Regex(br.getURL(), "/([^/]+)/?$").getMatch(0);
        /* Allow to pickup quotes */
        String postTitle = br.getRegex("\"description\":\"(.*?)\",\"").getMatch(0);
        if (StringUtils.isEmpty(postTitle)) {
            /* Fallback */
            postTitle = urltitle;
        }
        String[] images = br.getRegex("<li><a href=\"(https?://[^/]+/img/[^<>\"]+)").getColumn(0);
        if (images != null) {
            for (final String imageurl : images) {
                /* 2020-11-13: Not needed anymore */
                // imageurl = Encoding.htmlDecode(imageurl).replaceFirst("(-\\d+x\\d+)\\.(jpe?g|gif|png)$", ".$2");
                final DownloadLink link = createDownloadlink(imageurl);
                link.setAvailable(true);
                link.setContainerUrl(param.getCryptedUrl());
                decryptedLinks.add(link);
            }
        }
        if (postTitle != null) {
            final FilePackage fp = FilePackage.getInstance();
            fp.setName(Encoding.htmlDecode(postTitle));
            fp.addLinks(decryptedLinks);
        }
        return decryptedLinks;
    }
}
