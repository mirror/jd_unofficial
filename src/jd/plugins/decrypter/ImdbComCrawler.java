//jDownloader - Downloadmanager
//Copyright (C) 2009  JD-Team support@jdownloader.org
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

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForDecrypt;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "imdb.com" }, urls = { "https?://(?:www\\.)?imdb\\.com/((?:name|title)/(?:nm|tt)\\d+/(?:mediaindex|videogallery)|media/index/rg\\d+)" })
public class ImdbComCrawler extends PluginForDecrypt {
    public ImdbComCrawler(PluginWrapper wrapper) {
        super(wrapper);
    }

    private static final String TYPE_ARTIST = "(?i)https?://(www\\.)?imdb\\.com/media/index/rg\\d+";
    private static final String TYPE_TITLE  = "(?i)https?://(www\\.)?imdb\\.com/name|title/tt\\d+/mediaindex";
    private static final String TYPE_NAME   = "(?i)https?://(www\\.)?imdb\\.com/name/nm\\d+/mediaindex";
    private static final String TYPE_VIDEO  = "(?i).+/videogallery";

    public ArrayList<DownloadLink> decryptIt(final CryptedLink param, ProgressController progress) throws Exception {
        final ArrayList<DownloadLink> ret = new ArrayList<DownloadLink>();
        final String contenturl = param.getCryptedUrl().replaceFirst("(?i)http://", "https://");
        br.setFollowRedirects(true);
        br.getPage(contenturl);
        if (br.getRequest().getHttpConnection().getResponseCode() == 404) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        } else if (this.br.containsHTML("id=\"no_content\"")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        } else if (this.br.containsHTML("class=\"ilm_notice\"")) {
            /*
             * E.g. <div class="ilm_notice"> <p>We're sorry. We don't have any videos that match your search.</p> </div>
             */
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        int maxpage = 1;
        final String[] pages = br.getRegex("\\?page=(\\d+)\\&ref_=").getColumn(0);
        if (pages != null && pages.length != 0) {
            for (final String page : pages) {
                final int curpage = Integer.parseInt(page);
                if (curpage > maxpage) {
                    maxpage = curpage;
                }
            }
        }
        String fpName = br.getRegex("itemprop=\\'url\\'>([^<>\"]*?)</a>").getMatch(0);
        if (fpName == null) {
            fpName = "imdb.com - " + new Regex(contenturl, "([a-z]{2}\\d+)").getMatch(0);
        }
        fpName = Encoding.htmlDecode(fpName.trim());
        final FilePackage fp = FilePackage.getInstance();
        fp.setName(fpName);
        for (int i = 1; i <= maxpage; i++) {
            if (this.isAbort()) {
                logger.info("Decryption aborted by user: " + contenturl);
                return ret;
            }
            if (i > 1) {
                br.getPage(contenturl + "?page=" + i);
            }
            if (contenturl.matches(TYPE_VIDEO)) {
                final String[] links = br.getRegex("\"(/(?:video/imdb|videoplayer)/vi\\d+)").getColumn(0);
                if (links == null || links.length == 0) {
                    logger.warning("Decrypter broken for link: " + contenturl);
                    return null;
                }
                for (final String link : links) {
                    final DownloadLink dl = createDownloadlink("http://www.imdb.com" + link);
                    ret.add(dl);
                }
            } else {
                final String[][] links = br.getRegex("(/[^<>\"]+mediaviewer/rm\\d+)([^<>\"/]+)?\"([\t\n\r ]*?title=\"([^<>\"]*?)\")?").getMatches();
                if (links == null || links.length == 0) {
                    logger.warning("Decrypter broken for link: " + contenturl);
                    return null;
                }
                for (final String linkinfo[] : links) {
                    final String link = "http://www.imdb.com" + linkinfo[0];
                    final DownloadLink dl = createDownloadlink(link);
                    final String id = new Regex(link, "mediaviewer/[a-z]{2}(\\d+)").getMatch(0);
                    fp.add(dl);
                    final String subtitle = linkinfo[3];
                    if (subtitle != null) {
                        dl.setName(fpName + "_" + id + "_" + Encoding.htmlDecode(subtitle.trim()) + ".jpg");
                    } else {
                        dl.setName(fpName + "_" + id + "_" + ".jpg");
                    }
                    dl.setAvailable(true);
                    distribute(dl);
                    ret.add(dl);
                }
            }
        }
        fp.addLinks(ret);
        return ret;
    }
}
