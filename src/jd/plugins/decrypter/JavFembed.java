//jDownloader - Downloadmanager
//Copyright (C) 2015  JD-Team support@jdownloader.org
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
package jd.plugins.decrypter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.appwork.storage.JSonStorage;
import org.appwork.storage.TypeRef;
import org.appwork.utils.StringUtils;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.http.Browser;
import jd.http.requests.PostRequest;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForDecrypt;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "fembed.com" }, urls = { "https?://(www\\.)?dunjav\\.com/video/.*" })
public class JavFembed extends PluginForDecrypt {
    public JavFembed(PluginWrapper wrapper) {
        super(wrapper);
    }

    private String title      = null;
    private String fembedHost = null;

    @SuppressWarnings("deprecation")
    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        ArrayList<DownloadLink> crawledLinks = new ArrayList<DownloadLink>();
        final String parameter = param.toString();
        br.setFollowRedirects(true);
        br.getPage(parameter);
        if (br.getHttpConnection() == null || br.getHttpConnection().getResponseCode() == 404 || br.containsHTML("404 Not Found<|Page not found")) {
            crawledLinks.add(createOfflinelink(parameter));
            return crawledLinks;
        }
        title = Encoding.htmlDecode(br.getRegex("<title>(.*?)( - JAPANESE ADULT VIDEOS)?</title>").getMatch(0)).trim();
        final FilePackage fp = FilePackage.getInstance();
        fp.setName(title);
        // <iframe src="/embed/?id=771" allowfullscreen="true"
        final String[] iframes = br.getRegex("<iframe src=\"(/embed/[^/]+?)\"").getColumn(0);
        if ((iframes == null || iframes.length == 0 && br.containsHTML("<iframe src=\"\""))) {
            crawledLinks.add(createOfflinelink(parameter));
            return crawledLinks;
        }
        if ((iframes == null || iframes.length == 0)) {
            logger.warning("Decrypter broken (items regex) for link: " + parameter);
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        // <img class="img-thumbnail" width="600" alt="RIX023" title="RIX023"
        // src="https://dunjav.com/img/dunjav_c11946_v84127_118rix023pl.jpg"><
        final String poster = br.getRegex("<img class=.*?src=\"([^\"]+?)\"").getMatch(0);
        for (final String iframe : iframes) {
            logger.info("iframe: " + iframe);
            br.getPage(iframe);
            // <script>document.getElementById("embed").src=atob('aHR0cHM6Ly93d3cuZHVuYmVkLnh5ei92LzRkOWpycW1neW8x');</script>
            // https://www.dunbed.xyz/v/4d9jrqmgyo1
            final String atob = br.getRegex("src=atob\\('([^']+?)'\\)").getMatch(0);
            logger.info("atob: " + atob);
            if (atob == null) {
                continue; // Location: https://www.fembed.com
            }
            String fembed = Encoding.Base64Decode(atob);
            logger.info("fembed: " + fembed);
            crawlFembedLink(crawledLinks, fembed);
            fp.addLinks(crawledLinks);
        }
        return crawledLinks;
    }

    private void crawlFembedLink(final ArrayList<DownloadLink> crawledLinks, final String fembed) throws Exception {
        // Copied from FEmbedDecrypter, thanks to Sebbu.
        fembedHost = Browser.getHost(fembed);
        String file_id = new Regex(fembed, "/(?:f|v|api/sources?)/([a-zA-Z0-9_-]+)").getMatch(0);
        final PostRequest postRequest = new PostRequest("https://" + fembedHost + "/api/source/" + file_id);
        final Map<String, Object> response = JSonStorage.restoreFromString(br.getPage(postRequest), TypeRef.HASHMAP);
        if (!Boolean.TRUE.equals(response.get("success"))) {
            final DownloadLink link = createDownloadlink(fembed.replaceAll("https?://", "decryptedforFEmbedHosterPlugin://"));
            link.setAvailable(false);
            crawledLinks.add(link);
            return;
        }
        final FilePackage fp = FilePackage.getInstance();
        fp.setName(title);
        final List<Map<String, Object>> videos;
        if (response.get("data") instanceof String) {
            videos = (List<Map<String, Object>>) JSonStorage.restoreFromString((String) response.get("data"), TypeRef.OBJECT);
        } else {
            videos = (List<Map<String, Object>>) response.get("data");
        }
        for (final Map<String, Object> video : videos) {
            final DownloadLink link = createDownloadlink(fembed.replaceAll("https?://", "decryptedforFEmbedHosterPlugin://"));
            final String label = (String) video.get("label");
            final String type = (String) video.get("type");
            link.setProperty("label", label);
            link.setProperty("fembedid", file_id);
            link.setProperty("fembedHost", fembedHost);
            if (!StringUtils.isEmpty(title)) {
                link.setFinalFileName(title + "-" + label + "." + type);
            } else {
                link.setName(file_id + "-" + label + "." + type);
            }
            link.setAvailable(true);
            crawledLinks.add(link);
            fp.addLinks(crawledLinks);
        }
    }

    public boolean hasCaptcha(CryptedLink link, jd.plugins.Account acc) {
        return false;
    }
}