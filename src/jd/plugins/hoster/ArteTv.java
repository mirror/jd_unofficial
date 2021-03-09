//    jDownloader - Downloadmanager
//    Copyright (C) 2012  JD-Team support@jdownloader.org
//
//    This program is free software: you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation, either version 3 of the License, or
//    (at your option) any later version.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with this program.  If not, see <http://www.gnu.org/licenses/>.
package jd.plugins.hoster;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.regex.Pattern;

import org.jdownloader.downloader.hls.HLSDownloader;

import jd.PluginWrapper;
import jd.config.ConfigContainer;
import jd.config.ConfigEntry;
import jd.http.Browser;
import jd.http.URLConnectionAdapter;
import jd.parser.Regex;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;
import jd.utils.locale.JDL;

@HostPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "arte.tv", "concert.arte.tv", "creative.arte.tv", "future.arte.tv", "cinema.arte.tv", "theoperaplatform.eu", "info.arte.tv" }, urls = { "http://arte\\.tv\\.artejd_decrypted_jd/\\d+", "http://concert\\.arte\\.tv\\.artejd_decrypted_jd/\\d+", "http://creative\\.arte\\.tv\\.artejd_decrypted_jd/\\d+", "http://future\\.arte\\.tv\\.artejd_decrypted_jd/\\d+", "http://cinema\\.arte\\.tv\\.artejd_decrypted_jd/\\d+", "http://theoperaplatform\\.eu\\.artejd_decrypted_jd/\\d+", "http://info\\.arte\\.tv\\.artejd_decrypted_jd/\\d+" })
public class ArteTv extends PluginForHost {
    public static final String  V_NORMAL                              = "V_NORMAL_RESET_2019_01_23";
    public static final String  V_SUBTITLED                           = "V_SUBTITLED_RESET_2019_01_23";
    public static final String  V_SUBTITLE_DISABLED_PEOPLE            = "V_SUBTITLE_DISABLED_PEOPLE_RESET_2019_01_23";
    public static final String  V_AUDIO_DESCRIPTION                   = "V_AUDIO_DESCRIPTION_RESET_2019_01_23";
    private static final String http_300                              = "http_300";
    private static final String http_800                              = "http_800";
    private static final String http_1500                             = "http_1500";
    private static final String http_2200                             = "http_2200";
    private static final String hls                                   = "hls";
    /* creative.arte.tv extern qualities */
    private static final String http_extern_1000                      = "http_extern_1000";
    private static final String hls_extern_250                        = "hls_extern_250";
    private static final String hls_extern_500                        = "hls_extern_500";
    private static final String hls_extern_1000                       = "hls_extern_1000";
    private static final String hls_extern_2000                       = "hls_extern_2000";
    private static final String hls_extern_4000                       = "hls_extern_4000";
    public static final String  LOAD_LANGUAGE_URL                     = "LOAD_LANGUAGE_URL_RESET_2019_01_23";
    private static final String LOAD_BEST                             = "LOAD_BEST";
    public static final String  LOAD_LANGUAGE_GERMAN                  = "LOAD_LANGUAGE_GERMAN_RESET_2019_01_23";
    public static final String  LOAD_LANGUAGE_FRENCH                  = "LOAD_LANGUAGE_FRENCH_RESET_2019_01_23";
    public static final String  LOAD_LANGUAGE_ENGLISH                 = "LOAD_LANGUAGE_ENGLISH_RESET_2019_01_23";
    public static final String  LOAD_LANGUAGE_POLISH                  = "LOAD_LANGUAGE_POLISH_RESET_2019_01_23";
    public static final String  LOAD_LANGUAGE_ITALIAN                 = "LOAD_LANGUAGE_ITALIAN_RESET_2019_01_23";
    public static final String  LOAD_LANGUAGE_SPANISH                 = "LOAD_LANGUAGE_SPANISH_RESET_2019_01_23";
    public static final String  CUSTOM_FILE_NAME_PATTERN              = "CUSTOM_FILE_NAME_PATTERN";
    public static final String  CUSTOM_PACKAGE_NAME_PATTERN           = "CUSTOM_PACKAGE_NAME_PATTERN";
    public static final String  CUSTOM_THUMBNAIL_NAME_PATTERN         = "CUSTOM_THUMBNAIL_NAME_PATTERN";
    private static final String THUMBNAIL                             = "THUMBNAIL";
    private static final String FAST_LINKCHECK                        = "FAST_LINKCHECK";
    private static final String TYPE_GUIDE                            = "http://www\\.arte\\.tv/guide/[a-z]{2}/.+";
    private static final String TYPE_CONCERT                          = "http://(www\\.)?concert\\.arte\\.tv/.+";
    public static final boolean default_V_NORMAL                      = true;
    public static final boolean default_V_SUBTITLED                   = true;
    public static final boolean default_V_SUBTITLE_DISABLED_PEOPLE    = true;
    public static final boolean default_V_AUDIO_DESCRIPTION           = true;
    public static final boolean default_LOAD_LANGUAGE_URL             = false;
    public static final boolean default_LOAD_LANGUAGE_GERMAN          = true;
    public static final boolean default_LOAD_LANGUAGE_FRENCH          = true;
    public static final boolean default_LOAD_LANGUAGE_ENGLISH         = true;
    public static final boolean default_LOAD_LANGUAGE_POLISH          = true;
    public static final boolean default_LOAD_LANGUAGE_ITALIAN         = true;
    public static final boolean default_LOAD_LANGUAGE_SPANISH         = true;
    public static final String  default_CUSTOM_FILE_NAME_PATTERN      = "*date*_arte_*title*_*vpi*__*language*_*shortlanguage*_*resolution*_*bitrate*";
    public static final String  default_CUSTOM_PACKAGE_NAME_PATTERN   = "*date*_arte_*title*";
    public static final String  default_CUSTOM_THUMBNAIL_NAME_PATTERN = "*title*";
    private String              dllink                                = null;
    private String              quality_intern                        = null;

    @SuppressWarnings("deprecation")
    public ArteTv(PluginWrapper wrapper) {
        super(wrapper);
        setConfigElements();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void correctDownloadLink(DownloadLink link) {
        link.setUrlDownload(link.getDownloadURL().replaceFirst("jd_decrypted_jd", ""));
    }

    @Override
    public String getAGBLink() {
        return "http://www.arte.tv/de/Allgemeine-Nutzungsbedingungen/3664116.html";
    }

    /** Important information: RTMP player: http://www.arte.tv/player/v2//jwplayer6/mediaplayer.6.3.3242.swf */
    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws Exception {
        quality_intern = link.getStringProperty("quality_intern", null);
        br.setFollowRedirects(true);
        if (link.getBooleanProperty("offline", false)) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        final String apiurl = link.getStringProperty("apiurl", null);
        final String mainlink = link.getStringProperty("mainlink", null);
        final String lang = link.getStringProperty("langShort", null);
        String expiredBefore = null, expiredAfter = null, status = null, fileName = null, ext = "";
        br.getPage(apiurl);
        if (br.getHttpConnection().getResponseCode() == 404) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        expiredBefore = link.getStringProperty("VRA", null);
        expiredAfter = link.getStringProperty("VRU", null);
        fileName = link.getStringProperty("directName", null);
        dllink = link.getStringProperty("directURL", null);
        if (expiredBefore != null && expiredAfter != null) {
            status = getExpireMessage(lang, expiredBefore, expiredAfter);
            /* TODO: Improve this case! */
            if (status != null) {
                logger.warning(status);
                link.setName(status + "_" + fileName);
                return AvailableStatus.FALSE;
            }
        }
        if (fileName == null || dllink == null) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        if (!mainlink.matches(TYPE_GUIDE) && !mainlink.matches(TYPE_CONCERT)) {
            ext = dllink.substring(dllink.lastIndexOf("."), dllink.length());
            if (ext.length() > 4) {
                ext = new Regex(ext, Pattern.compile("\\w/(mp4):", Pattern.CASE_INSENSITIVE)).getMatch(0);
            }
            ext = ext == null ? ".flv" : "." + ext;
        }
        /* We can only check the filesizes of http urls */
        if (quality_intern.contains("http_")) {
            URLConnectionAdapter con = null;
            final Browser br2 = br.cloneBrowser();
            // In case the link redirects to the finallink
            br2.setFollowRedirects(true);
            try {
                con = br2.openHeadConnection(dllink);
                if (this.looksLikeDownloadableContent(con)) {
                    if (con.getCompleteContentLength() > 0) {
                        link.setVerifiedFileSize(con.getCompleteContentLength());
                    }
                } else {
                    throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
                }
            } finally {
                try {
                    con.disconnect();
                } catch (Throwable e) {
                }
            }
        } else if (quality_intern.contains("hls_")) {
        }
        link.setFinalFileName(fileName);
        return AvailableStatus.TRUE;
    }

    public static String getExpireMessage(final String lang, final String expiredBefore, final String expiredAfter) {
        String expired_message = null;
        if (expiredBefore != null && !checkDateExpiration(expiredBefore)) {
            expired_message = String.format(getPhrase("ERROR_CONTENT_NOT_AVAILABLE_YET"), getNiceDate(expiredBefore));
        }
        if (checkDateExpiration(expiredAfter)) {
            expired_message = String.format(getPhrase("ERROR_CONTENT_NOT_AVAILABLE_ANYMORE_COPYRIGHTS_EXPIRED"), getNiceDate(expiredAfter));
        }
        return expired_message;
    }

    /** Checks if a date is expired or not yet passed. */
    public static boolean checkDateExpiration(String s) {
        if (s == null) {
            return false;
        }
        SimpleDateFormat df = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.getDefault());
        try {
            Date date = null;
            try {
                date = df.parse(s);
            } catch (Throwable e) {
                df = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH);
                date = df.parse(s);
            }
            if (date.getTime() < System.currentTimeMillis()) {
                return true;
            }
        } catch (Throwable e) {
            return false;
        }
        return false;
    }

    public static String getNiceDate(final String input) {
        String nicedate = null;
        SimpleDateFormat df = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.getDefault());
        SimpleDateFormat convdf;
        if ("de".equalsIgnoreCase(System.getProperty("user.language"))) {
            convdf = new SimpleDateFormat("dd.MMMM.yyyy '_' HH-mm 'Uhr'", Locale.GERMANY);
        } else {
            convdf = new SimpleDateFormat("MMMM.dd.yyyy '_' hh-mm 'o clock'", Locale.ENGLISH);
        }
        try {
            Date date = null;
            try {
                date = df.parse(input);
                nicedate = convdf.format(date);
            } catch (Throwable e) {
            }
        } catch (Throwable e) {
        }
        return nicedate;
    }

    public static String getNiceDate2(final String input) {
        String nicedate = null;
        SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss ZZZ", Locale.getDefault());
        SimpleDateFormat convdf;
        if ("de".equalsIgnoreCase(System.getProperty("user.language"))) {
            convdf = new SimpleDateFormat("dd.MMMM.yyyy '_' HH-mm 'Uhr'", Locale.GERMANY);
        } else {
            convdf = new SimpleDateFormat("MMMM.dd.yyyy '_' hh-mm 'o clock'", Locale.ENGLISH);
        }
        try {
            Date date = null;
            try {
                date = df.parse(input);
                nicedate = convdf.format(date);
            } catch (Throwable e) {
            }
        } catch (Throwable e) {
        }
        return nicedate;
    }

    private void download(final DownloadLink link) throws Exception {
        if (quality_intern.startsWith("http_")) {
            br.setFollowRedirects(true);
            dl = jd.plugins.BrowserAdapter.openDownload(br, link, dllink, true, 0);
            if (dl.getConnection().getResponseCode() == 403) {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 403", 60 * 60 * 1000l);
            } else if (dl.getConnection().getResponseCode() == 404) {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 404", 60 * 60 * 1000l);
            }
            if (!this.looksLikeDownloadableContent(dl.getConnection())) {
                br.followConnection();
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            dl.startDownload();
        } else if (quality_intern.startsWith("hls_")) {
            checkFFmpeg(link, "Download a HLS Stream");
            dl = new HLSDownloader(link, br, dllink);
            dl.startDownload();
        } else {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return -1;
    }

    @Override
    public void handleFree(final DownloadLink link) throws Exception {
        requestFileInformation(link);
        download(link);
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }

    @Override
    public void resetPluginGlobals() {
    }

    @Override
    public String getDescription() {
        return "JDownloader's Arte Plugin helps downloading videoclips from arte.tv. Arte+7 provides different video qualities.";
    }

    public static HashMap<String, String> phrasesEN = new HashMap<String, String>(new HashMap<String, String>() {
                                                        {
                                                            put("ERROR_USER_NEEDS_TO_CHANGE_FORMAT_SELECTION", "Check_your_plugin_settings_activate_missing_formats_e_g_subtitled_versions_or_other_language_versions_");
                                                            put("ERROR_CONTENT_NOT_AVAILABLE_ANYMORE_COPYRIGHTS_EXPIRED", "This video is not available anymore since %s!_");
                                                            put("ERROR_CONTENT_NOT_AVAILABLE_YET", "This content is not available yet. It will be available from the %s!_");
                                                        }
                                                    });
    public static HashMap<String, String> phrasesDE = new HashMap<String, String>(new HashMap<String, String>() {
                                                        {
                                                            put("ERROR_USER_NEEDS_TO_CHANGE_FORMAT_SELECTION", "Überprüfe_deine_Plugineinstellungen_aktiviere_fehlende_Formate_z_B_Untertitelte_Version_oder_andere_Sprachversionen_");
                                                            put("ERROR_CONTENT_NOT_AVAILABLE_ANYMORE_COPYRIGHTS_EXPIRED", "Dieses Video ist seit dem %s nicht mehr verfügbar!_");
                                                            put("ERROR_CONTENT_NOT_AVAILABLE_YET", "Dieses Video ist noch nicht verfügbar. Es ist erst ab dem %s verfügbar!_");
                                                        }
                                                    });

    /**
     * Returns a German/English translation of a phrase. We don't use the JDownloader translation framework since we need only German and
     * English.
     *
     * @param key
     * @return
     */
    public static String getPhrase(String key) {
        if ("de".equals(System.getProperty("user.language")) && phrasesDE.containsKey(key)) {
            return phrasesDE.get(key);
        } else if (phrasesEN.containsKey(key)) {
            return phrasesEN.get(key);
        }
        return "Translation not found!";
    }

    private void setConfigElements() {
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), LOAD_BEST, JDL.L("plugins.hoster.arte.loadbest", "Nur die beste Qualität(aus der Auswahl) wählen?")).setDefaultValue(false));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_LABEL, "Auswahl der Qualitätsstufen für externe creative.arte.tv Videos:"));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_LABEL, "Auswahl der immer verfügbaren http Qualitätsstufen:"));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), http_extern_1000, JDL.L("plugins.hoster.arte.http_extern_1000", "1000kBit/s 504x284")).setDefaultValue(true));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_LABEL, "Auswahl der immer verfügbaren hls Qualitätsstufen für spezielle externe creative.arte.tv Videos:"));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), hls_extern_250, JDL.L("plugins.hoster.arte.hls_extern_250", "250kBit/s 192x144 oder ähnlich")).setDefaultValue(true));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), hls_extern_500, JDL.L("plugins.hoster.arte.hls_extern_500", "500kBit/s 308x232 oder ähnlich")).setDefaultValue(true));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), hls_extern_1000, JDL.L("plugins.hoster.arte.hls_extern_1000", "1000kBit/s 496x372 oder ähnlich")).setDefaultValue(false));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), hls_extern_2000, JDL.L("plugins.hoster.arte.hls_extern_2000", "2000kBit/s 720x408 oder ähnlich")).setDefaultValue(true));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), hls_extern_4000, JDL.L("plugins.hoster.arte.hls_extern_4000", "4000kBit/s oder ähnlich")).setDefaultValue(true));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_SEPARATOR));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_LABEL, "Auswahl der Qualitätsstufen für normale creative.arte.tv/concert.arte.tv/arte.tv Videos:"));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), hls, JDL.L("plugins.hoster.arte.hls", "HLS")).setDefaultValue(false));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_LABEL, "Auswahl der manchmal verfügbaren Qualitätsstufen:"));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), http_300, JDL.L("plugins.hoster.arte.http_300", "300kBit/s 384x216 (http)")).setDefaultValue(true));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_LABEL, "Auswahl der immer verfügbaren Qualitätsstufen:"));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), http_800, JDL.L("plugins.hoster.arte.http_800", "800kBit/s 720x406 (http)")).setDefaultValue(true));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), http_1500, JDL.L("plugins.hoster.arte.http_1500", "1500kBit/s 720x406 (http)")).setDefaultValue(true));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), http_2200, JDL.L("plugins.hoster.arte.http_2200", "2200kBit/s 1280x720 (http)")).setDefaultValue(true));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_SEPARATOR));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_LABEL, "Folgende Version(en) laden sofern verfügbar:"));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), V_NORMAL, JDL.L("plugins.hoster.arte.V_NORMAL", "Normale Version (ohne Untertitel)")).setDefaultValue(default_V_NORMAL));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), V_SUBTITLED, JDL.L("plugins.hoster.arte.V_SUBTITLED", "Untertitelt")).setDefaultValue(default_V_SUBTITLED));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), V_SUBTITLE_DISABLED_PEOPLE, JDL.L("plugins.hoster.arte.V_SUBTITLE_DISABLED_PEOPLE", "Untertitelt für Hörgeschädigte")).setDefaultValue(default_V_SUBTITLE_DISABLED_PEOPLE));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), V_AUDIO_DESCRIPTION, JDL.L("plugins.hoster.arte.V_AUDIO_DESCRIPTION", "Audio Deskription")).setDefaultValue(default_V_AUDIO_DESCRIPTION));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_SEPARATOR));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_LABEL, "Auswahl der Sprachversionen:"));
        final ConfigEntry cfge = new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), LOAD_LANGUAGE_URL, JDL.L("plugins.hoster.arte.LOAD_LANGUAGE_URL", "Sprachausgabe der URL laden?\r\n<html><nobr>Dies ist abhängig davon, ob bspw. '/de/' oder '/fr/' im eingefügten Link steht</nobr></html>")).setDefaultValue(default_LOAD_LANGUAGE_URL);
        getConfig().addEntry(cfge);
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_LABEL, "<html><b>WICHTIG: Falls nur eine Sprachversion verfügbar ist, aber beide ausgewählt sind kann es passieren, dass diese doppelt (als deutsch und französisch gleicher Inhalt mit verschiedenen Dateinamen) im Linkgrabber landet!</b></html>"));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), LOAD_LANGUAGE_GERMAN, JDL.L("plugins.hoster.arte.LOAD_LANGUAGE_GERMAN", "Sprachausgabe Deutsch laden?")).setDefaultValue(default_LOAD_LANGUAGE_GERMAN).setEnabledCondidtion(cfge, false));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), LOAD_LANGUAGE_FRENCH, JDL.L("plugins.hoster.arte.LOAD_LANGUAGE_FRENCH", "Sprachausgabe Französisch laden?")).setDefaultValue(default_LOAD_LANGUAGE_FRENCH).setEnabledCondidtion(cfge, false));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), LOAD_LANGUAGE_ENGLISH, JDL.L("plugins.hoster.arte.LOAD_LANGUAGE_ENGLISH", "Sprachausgabe Englisch laden?")).setDefaultValue(default_LOAD_LANGUAGE_ENGLISH).setEnabledCondidtion(cfge, false));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), LOAD_LANGUAGE_POLISH, JDL.L("plugins.hoster.arte.LOAD_LANGUAGE_POLISH", "Sprachausgabe Polnisch laden?")).setDefaultValue(default_LOAD_LANGUAGE_POLISH).setEnabledCondidtion(cfge, false));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), LOAD_LANGUAGE_ITALIAN, JDL.L("plugins.hoster.arte.LOAD_LANGUAGE_ITALIAN", "Sprachausgabe Italienisch laden?")).setDefaultValue(default_LOAD_LANGUAGE_ITALIAN).setEnabledCondidtion(cfge, false));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), LOAD_LANGUAGE_SPANISH, JDL.L("plugins.hoster.arte.LOAD_LANGUAGE_SPANISH", "Sprachausgabe Spanisch laden?")).setDefaultValue(default_LOAD_LANGUAGE_SPANISH).setEnabledCondidtion(cfge, false));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_SEPARATOR));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_LABEL, "Paket- und Dateinamen:"));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_TEXTFIELD, getPluginConfig(), CUSTOM_PACKAGE_NAME_PATTERN, JDL.L("plugins.hoster.arte.CUSTOM_PACKAGE_NAME_PATTERN", "Muster für Paketname:")).setDefaultValue(default_CUSTOM_PACKAGE_NAME_PATTERN));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_TEXTFIELD, getPluginConfig(), CUSTOM_FILE_NAME_PATTERN, JDL.L("plugins.hoster.arte.CUSTOM_FILE_NAME_PATTERN", "Muster für Dateiname:")).setDefaultValue(default_CUSTOM_FILE_NAME_PATTERN));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_TEXTFIELD, getPluginConfig(), CUSTOM_THUMBNAIL_NAME_PATTERN, JDL.L("plugins.hoster.arte.CUSTOM_THUMBNAIL_NAME_PATTERN", "Muster für Thumbnail-Dateiname:")).setDefaultValue(default_CUSTOM_THUMBNAIL_NAME_PATTERN));
        final StringBuilder sb = new StringBuilder();
        sb.append("Explanation of the available tags:\r\n");
        sb.append("<html><table>");
        sb.append("<tr><td>*title* </td><td>:</td><td>Title of the video</td></tr>");
        sb.append("<tr><td>*date* </td><td>:</td><td>Date when the video aired formatted as YYYY-MM-DD, e.g. 2018-12-31</td></tr>");
        sb.append("<tr><td>*vpi* </td><td>:</td><td>ID of the video</td></tr>");
        sb.append("<tr><td>*language* </td><td>:</td><td>Language of the video, e.g. Deutsch</td></tr>");
        sb.append("<tr><td>*shortlanguage* </td><td>:</td><td>Short formatted language of the video, e.g. DE</td></tr>");
        sb.append("<tr><td>*resolution* </td><td>:</td><td>Size of video in pixel (width x height), e.g. 1280x720</td></tr>");
        sb.append("<tr><td>*width* </td><td>:</td><td>Width of video in pixel, e.g. 1280</td></tr>");
        sb.append("<tr><td>*height* </td><td>:</td><td>Height of video in pixel, e.g. 720</td></tr>");
        sb.append("<tr><td>*bitrate* </td><td>:</td><td>Bitrate of video in kbit/s, e.g. 2200</td></tr>");
        sb.append("<tr><td>*ext* </td><td>:</td><td>File-extension (optional), e.g. mp4 or jpg</td></tr>");
        sb.append("</table>");
        sb.append("For package and thumbnail patterns only *title* and *date* are available.");
        sb.append("</html>");
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_LABEL, sb.toString()));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_SEPARATOR));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_LABEL, "Sonstiges:"));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), THUMBNAIL, JDL.L("plugins.hoster.arte.loadthumbnail", "Thumbnail laden?")).setDefaultValue(false));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), FAST_LINKCHECK, JDL.L("plugins.hoster.arte.fastlinkcheck", "Schnellen Linkcheck aktivieren?")).setDefaultValue(true));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_LABEL, "<html><b>WICHTIG: Dadurch erscheinen die Links schneller im Linkgrabber, aber die Dateigröße wird erst beim Downloadstart (oder manuellem Linkcheck) angezeigt.</b></html>"));
    }
}