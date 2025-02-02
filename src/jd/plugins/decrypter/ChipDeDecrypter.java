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

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jdownloader.scripting.JavaScriptEngineFactory;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.http.URLConnectionAdapter;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForDecrypt;
import jd.plugins.hoster.DirectHTTP;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "chip.de" }, urls = { "https?://(?:[A-Za-z0-9\\-]+\\.)?chip\\.de/(?!downloads|video)[^/]+/[^/]+_\\d+\\.html" })
public class ChipDeDecrypter extends PluginForDecrypt {
    public ChipDeDecrypter(PluginWrapper wrapper) {
        super(wrapper);
    }

    private static final boolean use_api_for_pictures = true;

    @SuppressWarnings({ "unused", "unchecked", "rawtypes" })
    public ArrayList<DownloadLink> decryptIt(final CryptedLink param, ProgressController progress) throws Exception {
        ArrayList<DownloadLink> ret = new ArrayList<DownloadLink>();
        final String contenturl = param.getCryptedUrl();
        final String linkid = new Regex(contenturl, "(\\d+)\\.html$").getMatch(0);
        String fpName = null;
        if (contenturl.matches(jd.plugins.hoster.ChipDe.type_chip_de_pictures) && !use_api_for_pictures) {
            /* Old website picture handling */
            br.setFollowRedirects(true);
            URLConnectionAdapter con = null;
            try {
                con = br.openGetConnection(contenturl);
                if (con.getResponseCode() == 410) {
                    throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
                }
                br.followConnection();
            } finally {
                try {
                    con.disconnect();
                } catch (Throwable e) {
                }
            }
            fpName = br.getRegex("<meta property=\"og:title\" content=\"(.*?) \\- Bildergalerie\"/>").getMatch(0);
            if (fpName == null) {
                fpName = br.getRegex("<title>(.*?) \\- Bilder \\-").getMatch(0);
            }
            if (fpName == null) {
                logger.warning("Decrypter broken for link:" + contenturl);
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            fpName = fpName.trim();
            String[] pictureNames = br.getRegex("bGrossversion\\[\\d+\\] = \"(.*?)\";").getColumn(0);
            if (pictureNames == null || pictureNames.length == 0) {
                pictureNames = br.getRegex("url \\+= \"/ii/grossbild_v2\\.html\\?grossbild=(.*?)\";").getColumn(0);
            }
            if (pictureNames == null || pictureNames.length == 0) {
                logger.warning("Decrypter broken for link: " + contenturl);
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            final DecimalFormat df = new DecimalFormat("000");
            int counter = 1;
            for (String picName : pictureNames) {
                // Skip invalid links, most times only the last link is invalid
                picName = Encoding.htmlDecode(picName);
                picName = picName.trim();
                if (picName.equals("")) {
                    continue;
                }
                final DownloadLink dl = createDownloadlink(DirectHTTP.createURLForThisPlugin("http://www.chip.de/ii/" + picName));
                dl.setFinalFileName(fpName + "_" + df.format(counter) + picName.substring(picName.lastIndexOf(".")));
                dl.setAvailable(true);
                ret.add(dl);
                counter++;
            }
        } else {
            jd.plugins.hoster.ChipDe.prepBRAPI(this.br);
            jd.plugins.hoster.ChipDe.accesscontainerIdBeitrag(this.br, linkid);
            /* We're using an API here so whatever goes wrong - it is probably a website issue / offline content. */
            try {
                Map<String, Object> entries = (Map<String, Object>) JavaScriptEngineFactory.jsonToJavaObject(this.br.toString());
                fpName = (String) entries.get("title");
                final List<Object> resource_data_list;
                if (contenturl.matches(jd.plugins.hoster.ChipDe.type_chip_de_pictures)) {
                    final DecimalFormat df = new DecimalFormat("000");
                    int counter = 1;
                    resource_data_list = (List) entries.get("pictures");
                    for (final Object oo : resource_data_list) {
                        try {
                            entries = (Map<String, Object>) oo;
                            final String description = (String) entries.get("image_text");
                            final String url = (String) entries.get("url");
                            if (inValidate(url)) {
                                continue;
                            }
                            String url_name = new Regex(url, "([^/]+)$").getMatch(0);
                            final DownloadLink dl = this.createDownloadlink(DirectHTTP.createURLForThisPlugin(url));
                            dl.setAvailable(true);
                            if (!inValidate(description)) {
                                dl.setComment(description);
                            }
                            if (!inValidate(url_name)) {
                                url_name = df.format(counter) + "_" + url_name;
                                dl.setFinalFileName(url_name);
                            }
                            ret.add(dl);
                        } finally {
                            counter++;
                        }
                    }
                } else {
                    /* User added an article - try to find embedded videos! */
                    resource_data_list = (List) entries.get("videos");
                    for (final Object oo : resource_data_list) {
                        entries = (Map<String, Object>) oo;
                        final String url = (String) entries.get("url");
                        if (inValidate(url) || !url.matches(jd.plugins.hoster.ChipDe.type_chip_de_video)) {
                            continue;
                        }
                        final DownloadLink dl = this.createDownloadlink(url);
                        ret.add(dl);
                    }
                }
            } catch (final Throwable e) {
            }
        }
        if (fpName != null) {
            FilePackage fp = FilePackage.getInstance();
            fp.setName(Encoding.htmlDecode(fpName));
            fp.addLinks(ret);
        }
        return ret;
    }

    /**
     * Validates string to series of conditions, null, whitespace, or "". This saves effort factor within if/for/while statements
     *
     * @param s
     *            Imported String to match against.
     * @return <b>true</b> on valid rule match. <b>false</b> on invalid rule match.
     * @author raztoki
     */
    protected boolean inValidate(final String s) {
        if (s == null || s.matches("\\s+") || s.equals("")) {
            return true;
        } else {
            return false;
        }
    }

    /* NO OVERRIDE!! */
    public boolean hasCaptcha(CryptedLink link, jd.plugins.Account acc) {
        return false;
    }
}