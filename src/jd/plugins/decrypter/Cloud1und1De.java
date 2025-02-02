package jd.plugins.decrypter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.appwork.storage.TypeRef;
import org.appwork.utils.Regex;
import org.appwork.utils.StringUtils;
import org.appwork.utils.os.CrossSystem;
import org.appwork.utils.parser.UrlQuery;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.http.Browser;
import jd.nutils.encoding.Encoding;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterException;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForDecrypt;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "cloud.1und1.de" }, urls = { "https?://cloud\\.1und1\\.de/ngcloud/external\\?.+" })
public class Cloud1und1De extends PluginForDecrypt {
    public Cloud1und1De(final PluginWrapper wrapper) {
        super(wrapper);
    }

    private final String                   brokenPlaceholder = "";
    private static AtomicReference<String> APPKEY            = new AtomicReference<String>(null);

    @Override
    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        final ArrayList<DownloadLink> ret = new ArrayList<DownloadLink>();
        br.setFollowRedirects(true);
        final UrlQuery query = UrlQuery.parse(param.getCryptedUrl());
        final String guestToken = query.get("guestToken");
        final String loginName = query.get("loginName");
        if (guestToken == null || loginName == null) {
            /* Invalid link */
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        br.getPage(param.getCryptedUrl());
        if (br.getHttpConnection().getResponseCode() == 404) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        final String version = br.getRegex("\"version\"\\s*:\\s*\"(.*?)\"").getMatch(0);
        if (version == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        String appKey = null;
        synchronized (APPKEY) {
            appKey = APPKEY.get();
            if (appKey == null) {
                final String cloudAppMin = br.getRegex("src=\"(cloud-app.*?\\.js.*?)\"").getMatch(0);
                if (cloudAppMin != null) {
                    final Browser br2 = br.cloneBrowser();
                    br2.getPage(cloudAppMin);
                    appKey = br2.getRegex("X-UI-API-KEY\"\\s*:.*?:\"(.*?)\",").getMatch(0);
                    if (appKey == null) {
                        APPKEY.set(brokenPlaceholder);// broken
                        logger.warning("Failed to find appKey");
                        // throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                    } else {
                        APPKEY.set(appKey);
                    }
                }
            } else if (brokenPlaceholder.equals(appKey)) {
                logger.warning("appKey missing");
                // throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
        }
        Browser br2 = br.cloneBrowser();
        if (appKey != null) {
            br2.setHeader("X-UI-API-KEY", appKey);
        }
        if (version != null) {
            br2.setHeader("X-UI-APP", "1&1access.web.onlinespeichernebula/" + version);
        }
        br2.setAllowedResponseCodes(new int[] { 401 });
        br2.getPage("https://cloud.1und1.de/restfs/guest/" + loginName + "/share/external/shareinfo?option=thumbnails&option=metadata&option=displayresource&option=props");
        if (br2.getHttpConnection().getResponseCode() == 404) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        br2.getPage("https://cloud.1und1.de/ngcloud/restfs/guest/" + loginName + "/share/" + guestToken + "/resourceAlias/ROOT?option=shares&option=download&option=thumbnails&option=metadata&option=props&option=displayresource&sort=ui:meta:user.created-a,name-a-i&option=props&length=1000&offset=0");
        if (br2.getHttpConnection().getResponseCode() == 404) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        if (br2.getRequest().getHttpConnection().getResponseCode() == 404) {
            return ret;
        } else if (br2.getRequest().getHttpConnection().getResponseCode() == 401) {
            final String pin = getUserInput("Pin?", param);
            if (pin == null || "".equals(pin)) {
                throw new DecrypterException(DecrypterException.PASSWORD);
            }
            br2 = br.cloneBrowser();
            br2.setHeader("X-UI-API-KEY", appKey);
            br2.setHeader("X-UI-APP", "1&1access.web.onlinespeichernebula/" + version);
            br2.setAllowedResponseCodes(new int[] { 401 });
            br2.setHeader("Authorization", "Basic " + Encoding.Base64Encode(guestToken + ":" + pin));
            br2.getPage("https://cloud.1und1.de/ngcloud/restfs/guest/" + loginName + "/share/" + guestToken + "/resourceAlias/ROOT?option=shares&option=download&option=thumbnails&option=metadata&option=props&option=displayresource&sort=ui:meta:user.created-a,name-a-i&option=props&length=1000&offset=0");
            if (br2.getRequest().getHttpConnection().getResponseCode() == 401) {
                throw new DecrypterException(DecrypterException.PASSWORD);
            }
        }
        final Map<String, Object> response = restoreFromString(br2.toString(), TypeRef.MAP);
        final Map<String, Object> ui_fs = (Map<String, Object>) response.get("ui:fs");
        ret.addAll(parse(br2, guestToken, loginName, CrossSystem.alleviatePathParts(""), ui_fs));
        return ret;
    }

    private ArrayList<DownloadLink> parse(final Browser br, final String shareID, final String userName, final String path, Map<String, Object> ui_fs) throws IOException {
        final ArrayList<DownloadLink> ret = new ArrayList<DownloadLink>();
        final List<Map<String, Object>> children = (List<Map<String, Object>>) ui_fs.get("children");
        for (final Map<String, Object> child : children) {
            if (isAbort()) {
                break;
            } else {
                final Map<String, Object> child_ui_fs = (Map<String, Object>) child.get("ui:fs");
                if ("file".equalsIgnoreCase((String) child_ui_fs.get("resourceType"))) {
                    final Map<String, Object> child_ui_link = (Map<String, Object>) child.get("ui:link");
                    final DownloadLink link = createDownloadlink("directhttp://" + (String) child_ui_link.get("downloadURI"));
                    link.setVerifiedFileSize(((Number) child_ui_fs.get("size")).longValue());
                    link.setAvailable(true);
                    link.setFinalFileName((String) child_ui_fs.get("name"));
                    final String resourceID = new Regex((String) child_ui_fs.get("resourceURI"), "/?(\\d+)").getMatch(0);
                    link.setProperty("resourceURI", Long.parseLong(resourceID));
                    link.setProperty("shareId", shareID);
                    link.setProperty("userName", userName);
                    link.setLinkID("cloud.1und1.de://" + shareID + "/" + resourceID);
                    if (!StringUtils.isEmpty(path)) {
                        link.setRelativeDownloadFolderPath(path);
                    }
                    ret.add(link);
                }
            }
        }
        String name = (String) ui_fs.get("name");
        if (name != null && ret.size() > 0) {
            final FilePackage fp = FilePackage.getInstance();
            fp.setName(name);
            fp.addLinks(ret);
        }
        for (final Map<String, Object> child : children) {
            if (isAbort()) {
                break;
            } else {
                final Map<String, Object> child_ui_fs = (Map<String, Object>) child.get("ui:fs");
                if ("container".equalsIgnoreCase((String) child_ui_fs.get("resourceType"))) {
                    final boolean isRoot = "ROOT".equals(child_ui_fs.get("downloadURI"));
                    String resourceURI = (String) child_ui_fs.get("resourceURI");
                    name = (String) ui_fs.get("name");
                    if (!isRoot && resourceURI != null) {
                        final Browser br2 = br.cloneBrowser();
                        try {
                            resourceURI = resourceURI.replaceAll("^\\.+", "");
                            if (!resourceURI.startsWith("/resource")) {
                                resourceURI = "/resource/" + resourceURI;
                            }
                            br2.getPage("https://cloud.1und1.de/ngcloud/restfs/guest/" + userName + "/share/" + shareID + resourceURI + "?option=shares&option=download&option=thumbnails&option=metadata&option=props&option=displayresource&sort=ui:meta:user.created-a,name-a-i&option=props&length=1000&offset=0");
                            final Map<String, Object> response = restoreFromString(br2.toString(), TypeRef.HASHMAP);
                            final Map<String, Object> node_ui_fs = (Map<String, Object>) response.get("ui:fs");
                            ret.addAll(parse(br2, shareID, userName, path + "/" + CrossSystem.alleviatePathParts(name), node_ui_fs));
                        } catch (final IOException e) {
                            logger.log(e);
                            return ret;
                        }
                    }
                }
            }
        }
        return ret;
    }
}
