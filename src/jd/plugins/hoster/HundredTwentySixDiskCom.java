//jDownloader - Downloadmanager
//Copyright (C) 2010  JD-Team support@jdownloader.org
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

import java.io.IOException;

import jd.PluginWrapper;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;

import org.appwork.utils.formatter.SizeFormatter;

@HostPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "126disk.com" }, urls = { "https?://(?:www\\.)?126(?:disk|xy|xiazai)\\.com/(?:file|rf)view_(\\d+)\\.html" })
public class HundredTwentySixDiskCom extends PluginForHost {
    public HundredTwentySixDiskCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public String getAGBLink() {
        return "http://www.126disk.com/";
    }

    public void correctDownloadLink(DownloadLink link) {
        link.setPluginPatternMatcher(link.getPluginPatternMatcher().replace("rfview_", "fileview_"));
    }

    @Override
    public String getLinkID(final DownloadLink link) {
        final String linkid = new Regex(link.getPluginPatternMatcher(), this.getSupportedLinks()).getMatch(0);
        if (linkid != null) {
            return linkid;
        } else {
            return super.getLinkID(link);
        }
    }

    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws IOException, PluginException {
        this.setBrowserExclusive();
        br.setFollowRedirects(true);
        br.getPage(link.getPluginPatternMatcher());
        if (br.getURL().endsWith("/error.php") || br.getHttpConnection().getResponseCode() == 403 || br.containsHTML(">你访问的文件不存在。现在将转入首页！|>\\s*你访问的文件包含违规内容…\\s*<")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        String filename = br.getRegex("<h1[^<>]+>([^<>]*?)</h1>").getMatch(0);
        if (filename == null) {
            filename = br.getRegex("class=\"nowrap file-name( [a-z0-9\\-]+)?\">([^<>\"]*?)</h1>").getMatch(1);
        }
        if (filename == null) {
            /* Fallback */
            filename = getLinkID(link);
        }
        String filesize = br.getRegex("大小：<[^<>]+>([^<>]*?)( ?\\([^<>])?<").getMatch(0);
        if (filesize == null) {
            filesize = br.getRegex("<table id=\"info_table\">[\t\n\r ]+<tr>[\t\n\r ]+<td width=\"160px;\">文件大小：([^<>\"]*?)</td>").getMatch(0);
        }
        if (filename == null || filesize == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        link.setFinalFileName(Encoding.htmlDecode(filename.trim()));
        if (filesize != null) {
            link.setDownloadSize(SizeFormatter.getSize(filesize + "b"));
        }
        String md5 = br.getRegex("(?i)>M D 5值 ：</b>([a-z0-9]{32})</li>").getMatch(0);
        if (md5 == null) {
            md5 = br.getRegex("(?i)<td>文件MD5：([a-f0-9]{32})</td>").getMatch(0);
        }
        if (md5 != null) {
            link.setMD5Hash(md5);
        }
        return AvailableStatus.TRUE;
    }

    @Override
    public void handleFree(final DownloadLink link) throws Exception, PluginException {
        requestFileInformation(link);
        br.getPage("/download.php?id=" + new Regex(link.getPluginPatternMatcher(), "(\\d+)\\.html$").getMatch(0) + "&share=0&type=wt&t=" + System.currentTimeMillis());
        if (br.getHttpConnection().getResponseCode() == 404) {
            /* 2020-04-16: I was not able to find a single downloadable file, 404 also happens in browser */
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 404");
        }
        final String dllink = br.getRegex("\"(http://[a-z0-9]+\\.126(?:disk|xy)\\.com/[^<>\"]*?)\"").getMatch(0);
        if (dllink == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl = jd.plugins.BrowserAdapter.openDownload(br, link, Encoding.htmlDecode(dllink), false, 1);
        if (!this.looksLikeDownloadableContent(dl.getConnection())) {
            br.followConnection(true);
            if (br.containsHTML("<title>Error</title>|<TITLE>无法找到该页</TITLE>")) {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error");
            } else {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
        }
        dl.startDownload();
    }

    @Override
    public void reset() {
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return -1;
    }

    @Override
    public void resetDownloadlink(final DownloadLink link) {
    }
}