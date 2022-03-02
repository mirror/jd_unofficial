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
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.hoster.UnknownPornScript8;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "pornziz.com", "xnhub.com" }, urls = { "https?://(?:www\\.)?pornziz\\.com/video/[a-z0-9\\-]+\\-\\d+\\.html", "https?://(?:www\\.)?xnhub\\.com/video/[a-z0-9\\-]+\\-\\d+\\.html" })
public class UnknownPornScript8Crawler extends PornEmbedParser {
    public UnknownPornScript8Crawler(PluginWrapper wrapper) {
        super(wrapper);
    }

    /* DEV NOTES */
    /* Porn_plugin */
    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        br.setFollowRedirects(true);
        br.getPage(param.getCryptedUrl());
        if (UnknownPornScript8.isOffline(this.br)) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        decryptedLinks.addAll(findEmbedUrls(null));
        if (decryptedLinks.isEmpty()) {
            /* Nothing found? Probably selfhosted content --> Pass to hosterplugin. */
            decryptedLinks.add(this.createDownloadlink(param.getCryptedUrl()));
        }
        return decryptedLinks;
    }
}