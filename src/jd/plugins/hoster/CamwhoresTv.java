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

import jd.PluginWrapper;
import jd.http.Browser;
import jd.plugins.HostPlugin;

@HostPlugin(revision = "$Revision$", interfaceVersion = 3, names = {}, urls = {})
public class CamwhoresTv extends KernelVideoSharingComV2 {
    public CamwhoresTv(final PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium("https://www.camwhores.tv/");
    }

    /** Sync this between camwhores hoster + crawler plugins!! */
    public static List<String[]> getPluginDomains() {
        final List<String[]> ret = new ArrayList<String[]>();
        // each entry in List<String[]> will result in one PluginForHost, Plugin.getHost() will return String[0]->main domain
        ret.add(new String[] { "camwhores.tv", "camwhores.video", "camwhores.biz", "camwhores.sc", "camwhores.io", "camwhores.adult", "camwhores.cc", "camwhores.org", "camwhores.lol", "camwhorestv.co", "camwhorestv.org" });
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
        return KernelVideoSharingComV2.buildAnnotationUrlsDefaultVideosPattern(getPluginDomains());
    }

    @Override
    protected boolean enableFastLinkcheck() {
        /* 2020-10-30 */
        return true;
    }

    @Override
    protected boolean isOfflineWebsite(final Browser br) {
        if (super.isOfflineWebsite(br)) {
            return true;
        } else if (br.containsHTML("(?i)>\\s*404 / Page not found")) {
            return true;
        } else {
            return false;
        }
    }
}