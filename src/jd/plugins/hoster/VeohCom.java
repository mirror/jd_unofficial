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
package jd.plugins.hoster;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;
import java.net.URL;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedByInterruptException;
import java.security.InvalidKeyException;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.appwork.utils.StringUtils;
import org.appwork.utils.formatter.SizeFormatter;
import org.jdownloader.plugins.controller.LazyPlugin;

import jd.PluginWrapper;
import jd.crypt.Base64;
import jd.crypt.JDCrypt;
import jd.gui.UserIO;
import jd.http.Browser;
import jd.http.URLConnectionAdapter;
import jd.nutils.JDHash;
import jd.nutils.encoding.Encoding;
import jd.nutils.nativeintegration.LocalBrowser;
import jd.parser.Regex;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;
import jd.plugins.components.PluginJSonUtils;
import jd.plugins.download.RAFDownload;
import jd.utils.JDHexUtils;

@HostPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "veoh.com" }, urls = { "https?://(?:www\\.)?veohdecrypted\\.com/(browse/videos/category/.*?/)?watch/([A-Za-z0-9]+)" })
public class VeohCom extends PluginForHost {
    private static final String  APIKEY          = "NEQzRTQyRUMtRjEwQy00MTcyLUExNzYtRDMwQjQ2OEE2OTcy";
    private URLConnectionAdapter DL;
    private byte[]               IV;
    private InputStream          INPUTSTREAM;
    private byte[]               BUFFER;
    private long                 BYTESLOADED;
    private long                 BYTES2DO        = -1;
    private BufferedOutputStream FILEOUT;
    private boolean              CONNECTIONCLOSE = false;
    private int                  FAILCOUNTER     = 0;

    public VeohCom(final PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public LazyPlugin.FEATURE[] getFeatures() {
        return new LazyPlugin.FEATURE[] { LazyPlugin.FEATURE.VIDEO_STREAMING };
    }

    public void correctDownloadLink(DownloadLink link) {
        link.setUrlDownload(link.getDownloadURL().replace("veohdecrypted.com/", "veoh.com/"));
    }

    private String getFID(final DownloadLink link) {
        return new Regex(link.getPluginPatternMatcher(), "([A-Za-z0-9]+)$").getMatch(0);
    }

    private byte[] AESdecrypt(final byte[] plain, final byte[] key, final byte[] iv) throws Exception {
        final Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
        final IvParameterSpec ivSpec = new IvParameterSpec(iv);
        final SecretKeySpec skeySpec = new SecretKeySpec(key, "AES");
        cipher.init(Cipher.DECRYPT_MODE, skeySpec, ivSpec);
        return cipher.doFinal(plain);
    }

    private class BouncyCastleAESdecrypt {
        private byte[] decrypt(final byte[] plain, final byte[] key, final byte[] iv) throws Exception {
            // Prepare the cipher (AES, CBC, no padding)
            final org.bouncycastle.crypto.BufferedBlockCipher cipher = new org.bouncycastle.crypto.BufferedBlockCipher(new org.bouncycastle.crypto.modes.CBCBlockCipher(new org.bouncycastle.crypto.engines.AESEngine()));
            cipher.reset();
            cipher.init(false, new org.bouncycastle.crypto.params.ParametersWithIV(new org.bouncycastle.crypto.params.KeyParameter(key), iv));
            // Perform the decryption
            final byte[] decrypted = new byte[cipher.getOutputSize(plain.length)];
            int decLength = cipher.processBytes(plain, 0, plain.length, decrypted, 0);
            cipher.doFinal(decrypted, decLength);
            return decrypted;
        }
    }

    public void closeConnections() {
        CONNECTIONCLOSE = true;
        try {
            INPUTSTREAM.close();
        } catch (final Throwable e) {
        } finally {
            INPUTSTREAM = null;
        }
        try {
            DL.disconnect();
        } catch (final Throwable e) {
        }
        logger.info("Closed connection before closing file");
    }

    private String decryptUrl(final String[] T, String baseUrl, final String fHash, final String hexTime, final String hexvidID, final String hexSize) throws Exception {
        final byte[] cipher = Base64.decode(T[0]);
        baseUrl = baseUrl.replaceAll("\\$1", T[1]);
        baseUrl = baseUrl.replaceAll("\\$F", fHash);
        final byte[] key = JDHexUtils.getByteArray(hexTime + hexvidID + fHash.substring(0, 16) + "00000000" + hexSize + fHash.substring(24));
        String result = null;
        try {
            result = JDHexUtils.getHexString(new BouncyCastleAESdecrypt().decrypt(cipher, key, IV)).toLowerCase();
        } catch (Throwable e) {
            /* Fallback for stable version */
            result = JDHexUtils.getHexString(AESdecrypt(cipher, key, IV)).toLowerCase();
        }
        if (result == null || result.length() < 64) {
            return null;
        }
        baseUrl = baseUrl.replaceAll("\\$P", result.substring(0, 40));
        baseUrl = baseUrl.replaceAll("\\$3", T[3]);
        baseUrl = baseUrl.replaceAll("\\$T", result.substring(44));
        baseUrl = baseUrl.replaceAll("\\$2", T[2]);
        // neuer IV für nächste VideoUrl aus den letzten 16 Byte des EID
        IV = JDHexUtils.getByteArray(JDHexUtils.getHexString(cipher).substring(32));
        return Encoding.htmlDecode(baseUrl);
    }

    @Override
    public String getAGBLink() {
        return "http://www.veoh.com/corporate/termsofuse";
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return -1;
    }

    private void getPolicyFiles() throws Exception {
        int ret = -100;
        UserIO.setCountdownTime(120);
        ret = UserIO.getInstance().requestConfirmDialog(UserIO.STYLE_LARGE, "Java Cryptography Extension (JCE) Error: 32 Byte keylength is not supported!", "At the moment your Java version only supports a maximum keylength of 16 Bytes but the veoh plugin needs support for 32 byte keys.\r\nFor such a case Java offers so called \"Policy Files\" which increase the keylength to 32 bytes. You have to copy them to your Java-Home-Directory to do this!\r\nExample path: \"jre6\\lib\\security\\\". The path is different for older Java versions so you might have to adapt it.\r\n\r\nBy clicking on CONFIRM a browser instance will open which leads to the downloadpage of the file.\r\n\r\nThanks for your understanding.", null, "CONFIRM", "Cancel");
        if (ret != -100) {
            if (UserIO.isOK(ret)) {
                LocalBrowser.openDefaultURL(new URL("http://www.oracle.com/technetwork/java/javase/downloads/jce-6-download-429243.html"));
            } else {
                return;
            }
        }
    }

    @Override
    public void handleFree(final DownloadLink link) throws Exception {
        requestFileInformation(link);
        final String fid = getFID(link);
        String dllink = null;
        if (use_api_for_availablecheck) {
            /* 2019-09-24: New */
            dllink = PluginJSonUtils.getJson(br, "HQ");
            if (StringUtils.isEmpty(dllink)) {
                dllink = PluginJSonUtils.getJson(br, "Regular");
            }
        }
        if (StringUtils.isEmpty(dllink)) {
            br.getPage("https://www." + this.getHost() + "/rest/v2/execute.xml?method=veoh.video.findByPermalink&apiKey=" + Encoding.Base64Decode(APIKEY) + "&permalink=" + fid);
            final String fHash = br.getRegex("fileHash=\"(.*?)\"").getMatch(0);
            final String videoId = br.getRegex("videoId=\"(\\d+)\"").getMatch(0);
            final String fileSize = br.getRegex("size=\"(\\d+)\"").getMatch(0);
            final String ext = br.getRegex("extension=\"(\\.[a-z0-9]+)\"").getMatch(0);
            String sTime = br.getRegex("timestamp=\"(\\d+)\"").getMatch(0);
            if (fHash == null || fHash == null || sTime == null || videoId == null || fileSize == null || ext == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            boolean oldDlHandling = fHash.length() < 32;
            String hexTime = String.format("%08x", Long.parseLong(sTime));
            if (!oldDlHandling) {
                link.setName(link.getName() + ext);
                br.setFollowRedirects(true);
                // prepareBrowser("veohplugin-1.3.6 service (NT 6.1; IE 7.0; en-US Windows)");
                /* generate crypted token (ct=) */
                final String path = "/veoh/" + fid + "/" + fHash + ".eveoh";
                final String cryptedToken = JDHash.getSHA1(sTime + "VT Copyright 2008 Veoh" + path);
                final int p = Integer.parseInt(cryptedToken.substring(0, 1), 16);
                link.getLinkStatus().setStatusText("download to initialize ...");
                br.getPage("http://content.veoh.com" + path + "?version=3&ct=" + cryptedToken + xor(cryptedToken.substring(p, p + 8), hexTime));
                if (br.getHttpConnection().getResponseCode() == 404 && br.containsHTML("<title>404 Not Found</title>")) {
                    oldDlHandling = true;
                }
            }
            if (!oldDlHandling) {
                link.setDownloadSize(SizeFormatter.getSize(fileSize));
                /* parse piece eids and decrypt it */
                sTime = br.getRegex("time=\'(\\d+)\'").getMatch(0);
                if (sTime == null) {
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
                final String hexvidID = String.format("%08x", Long.parseLong(videoId));
                final String hexSize = String.format("%08x", Long.parseLong(fileSize));
                final String templateUrl = br.getRegex("url base=\'(.*?)\'").getMatch(0);
                hexTime = String.format("%08x", Long.parseLong(sTime));
                String baseUrl = templateUrl;
                /* CHECK: we should always use getBytes("UTF-8") or with wanted charset, never system charset! */
                IV = fHash.substring(0, 16).getBytes();
                final String[][] content = br.getRegex("<piece eid=\'(.*?)\' a1=\'(.*?)\' a2=\'(.*?)\' a3=\'(.*?)\' />").getMatches();
                prepareBrowser("veoh-1.3.6 service (NT 6.1; IE 9.0.8112.16421; en-US Windows)");
                final File tmpFile = new File(link.getFileOutput() + ".part");
                /* reset */
                if (!tmpFile.exists()) {
                    link.setProperty("bytes_loaded", Long.valueOf(0l));
                    link.setProperty("parts_finished", Long.valueOf(0l));
                }
                /* resuming */
                BYTESLOADED = (Long) link.getProperty("bytes_loaded", Long.valueOf(0l));
                final int resume = Math.round((Long) link.getProperty("parts_finished", Long.valueOf(0l)));
                if (resume > 0) {
                    IV = JDHexUtils.getByteArray(JDHexUtils.getHexString(Base64.decode(content[resume - 1][0])).substring(32));
                }
                int i = 0;
                /* once init the buffer is enough */
                BUFFER = new byte[256 * 1024];
                try {
                    RAFDownload raf = new RAFDownload(this, link, null);
                    dl = raf;
                    try {
                        link.getDownloadLinkController().getConnectionHandler().addConnectionHandler(dl.getManagedConnetionHandler());
                    } catch (final Throwable e) {
                    }
                    /* we have to create folder structure */
                    tmpFile.getParentFile().mkdirs();
                    FILEOUT = new BufferedOutputStream(new FileOutputStream(tmpFile, true));
                    for (i = resume; i < content.length; i++) {
                        final String[] T = content[i];
                        link.getLinkStatus().setStatusText("Video Part " + (Integer.valueOf(T[3]) + 1) + " @ " + String.valueOf(content.length) + " in Progress...");
                        final String pieces = decryptUrl(T, baseUrl, fHash, hexTime, hexvidID, hexSize);
                        if (pieces == null) {
                            break;
                        }
                        try {
                            /* always close the existing connection */
                            DL.disconnect();
                        } catch (final Throwable e) {
                        }
                        DL = br.openGetConnection(pieces);
                        if (DL.getResponseCode() != 200) {
                            if (DL.getResponseCode() == 500) {
                                throw new PluginException(LinkStatus.ERROR_HOSTER_TEMPORARILY_UNAVAILABLE, "ServerError(500)", 5 * 60 * 1000l);
                            } else if (DL.getResponseCode() == 400) {
                                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT, "Decrypt failed!");
                            } else if (DL.getResponseCode() == 404) {
                                logger.warning("Veohdownload: Video Part " + (i + 1) + " not found! Link: " + link.getDownloadURL());
                                FAILCOUNTER += 1;
                                continue;
                            }
                            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                        }
                        long partSize = DL.getLongContentLength();
                        try {
                            if (INPUTSTREAM != null && INPUTSTREAM instanceof org.appwork.utils.net.throttledconnection.MeteredThrottledInputStream) {
                                ((org.appwork.utils.net.throttledconnection.MeteredThrottledInputStream) INPUTSTREAM).setInputStream(DL.getInputStream());
                            } else {
                                INPUTSTREAM = new org.appwork.utils.net.throttledconnection.MeteredThrottledInputStream(DL.getInputStream(), new org.appwork.utils.speedmeter.AverageSpeedMeter(10));
                                dl.getManagedConnetionHandler().addThrottledConnection((org.appwork.utils.net.throttledconnection.MeteredThrottledInputStream) INPUTSTREAM);
                            }
                        } catch (final Throwable e) {
                            INPUTSTREAM = new org.appwork.utils.net.throttledconnection.MeteredThrottledInputStream(DL.getInputStream(), new org.appwork.utils.speedmeter.AverageSpeedMeter(10));
                            /* 0.95xx comp */
                        }
                        try {
                            int miniblock = 0;
                            int partEndByte = 0;
                            while (partSize != 0) {
                                try {
                                    if (partEndByte > 0) {
                                        miniblock = INPUTSTREAM.read(BUFFER, 0, (int) Math.min(BYTES2DO, BUFFER.length));
                                    } else {
                                        miniblock = INPUTSTREAM.read(BUFFER);
                                    }
                                } catch (final SocketException e2) {
                                    if (!isExternalyAborted()) {
                                        throw e2;
                                    }
                                    miniblock = -1;
                                    break;
                                } catch (final ClosedByInterruptException e) {
                                    if (!isExternalyAborted()) {
                                        logger.severe("Timeout detected");
                                    }
                                    miniblock = -1;
                                    break;
                                } catch (final AsynchronousCloseException e3) {
                                    if (!isExternalyAborted() && !CONNECTIONCLOSE) {
                                        throw e3;
                                    }
                                    miniblock = -1;
                                    break;
                                } catch (final IOException e4) {
                                    if (!isExternalyAborted() && !CONNECTIONCLOSE) {
                                        throw e4;
                                    }
                                    miniblock = -1;
                                    break;
                                }
                                if (miniblock == -1) {
                                    break;
                                }
                                BYTES2DO -= miniblock;
                                partSize -= miniblock;
                                FILEOUT.write(BUFFER, 0, miniblock);
                                BYTESLOADED += miniblock;
                                partEndByte += miniblock;
                                link.setDownloadCurrent(BYTESLOADED);
                                if (partEndByte > 0) {
                                    BYTES2DO = partEndByte + 1;
                                }
                            }
                            if (partSize == 0) {
                                link.setProperty("parts_finished", Long.valueOf(T[3]) + 1);
                            } else {
                                link.setProperty("parts_finished", Long.valueOf(T[3]));
                            }
                            if (isExternalyAborted()) {
                                link.setProperty("bytes_loaded", Long.valueOf(BYTESLOADED));
                                link.getLinkStatus().setStatus(LinkStatus.ERROR_DOWNLOAD_INCOMPLETE);
                                break;
                            }
                            baseUrl = templateUrl;
                        } finally {
                            try {
                                DL.disconnect();
                            } catch (final Throwable e) {
                            }
                        }
                    }
                } catch (final InvalidKeyException e) {
                    try {
                        FILEOUT.close();
                    } catch (final Throwable e2) {
                    }
                    if (!tmpFile.delete()) {
                        logger.severe("Could not delete file " + tmpFile);
                    }
                    if (e.getMessage().contains("Illegal key size")) {
                        getPolicyFiles();
                    }
                    throw new PluginException(LinkStatus.ERROR_FATAL, "Unlimited Strength JCE Policy Files needed!");
                } catch (final Exception e2) {
                    e2.printStackTrace();
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                } finally {
                    try {
                        dl.getManagedConnetionHandler().removeThrottledConnection((org.appwork.utils.net.throttledconnection.MeteredThrottledInputStream) INPUTSTREAM);
                    } catch (final Throwable e) {
                    }
                    try {
                        link.getDownloadLinkController().getConnectionHandler().removeConnectionHandler(dl.getManagedConnetionHandler());
                    } catch (final Throwable e) {
                    }
                    try {
                        DL.disconnect();
                    } catch (final Throwable e) {
                    }
                    try {
                        FILEOUT.close();
                    } catch (final Throwable e) {
                    }
                    setDownloadInterface(null);
                    // System.out.println("SOLL: " + downloadLink.getDownloadSize()
                    // +
                    // " - IST: " + BYTESLOADED);
                    if (link.getDownloadSize() == BYTESLOADED || i == content.length) {
                        if (!tmpFile.renameTo(new File(link.getFileOutput()))) {
                            logger.severe("Could not rename file " + tmpFile + " to " + link.getFileOutput());
                        }
                    }
                }
                link.getLinkStatus().setStatusText(null);
                if (!isExternalyAborted()) {
                    link.getLinkStatus().setStatus(LinkStatus.FINISHED);
                    if (FAILCOUNTER > 0) {
                        link.getLinkStatus().setStatusText("File(s) not found: " + FAILCOUNTER);
                    }
                }
            } else {
                /* decrase Browserrequests */
                final Browser br2 = br.cloneBrowser();
                /* webplayer request */
                br2.getPage("https://www." + this.getHost() + "/static/swf/webplayer/VWPBeacon.swf?port=50246&version=1.2.2.1112");
                br.getPage("https://www." + this.getHost() + "/rest/v2/execute.xml?apiKey=" + Encoding.Base64Decode("NTY5Nzc4MUUtMUM2MC02NjNCLUZGRDgtOUI0OUQyQjU2RDM2") + "&method=veoh.video.findByPermalink&permalink=" + fid + "&");
                if (br.containsHTML("(<rsp stat=\"fail\"|\"The video does not exist\"|name=\"YouTube\\.com\" type=\"\")")) {
                    throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
                }
                /* fileextension */
                if (!link.getName().matches(".+\\.[\\w]{1,3}$") || !link.getName().endsWith(ext)) {
                    link.setFinalFileName(link.getName() + ext);
                }
                /* finallinkparameter */
                final String fHashPath = br.getRegex("fullHashPath=\"(.*?)\"").getMatch(0);
                String fHashToken = br.getRegex("fullHashPathToken=\"(.*?)\"").getMatch(0);
                /* decrypt fHashToken */
                try {
                    final byte[] bytes = JDCrypt.decrypt(JDHexUtils.getByteArray(JDHexUtils.getHexString(Base64.decode(fHashToken))), JDHexUtils.getByteArray(Encoding.Base64Decode("ODY5NGRmY2RkODY0Y2FhYWM4OTAyZDdlYmQwNGVkYWU=")), JDHexUtils.getByteArray(Encoding.Base64Decode("ZmY1N2NlYzMwYWVlYTg5YTBmNTBkYjQxNjRhMWRhNzI=")));
                    fHashToken = bytes != null ? new String(bytes, "UTF-8") : null;
                } catch (final Throwable e) {
                }
                if (fHashPath == null || fHashToken == null) {
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
                br.getHeaders().put("Referer", "http://www.veoh.com/static/swf/veoh/VeohMediaPlayer.swf?v=2012-03-26.2");
                br.getHeaders().put("x-flash-version", "10,3,183,7");
                dllink = fHashPath + fHashToken;
            }
        }
        if (StringUtils.isEmpty(dllink)) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        br.setFollowRedirects(true);
        dl = jd.plugins.BrowserAdapter.openDownload(br, link, dllink, true, 0);
        if (!this.looksLikeDownloadableContent(dl.getConnection())) {
            br.followConnection(true);
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl.startDownload();
    }

    private boolean isExternalyAborted() {
        boolean ret = Thread.currentThread().isInterrupted();
        if (ret) {
            return true;
        }
        try {
            ret = dl.externalDownloadStop();
        } catch (final Throwable e) {
        }
        return ret;
    }

    private void prepareBrowser(final String userAgent) {
        br.clearCookies(this.getHost());
        br.getHeaders().put("Pragma", null);
        br.getHeaders().put("Cache-Control", null);
        br.getHeaders().put("Accept-Charset", null);
        br.getHeaders().put("Accept-Encoding", null);
        br.getHeaders().put("Accept", null);
        br.getHeaders().put("Accept-Language", null);
        br.getHeaders().put("User-Agent", userAgent);
        br.getHeaders().put("Connection", null);
        br.getHeaders().put("Referer", null);
    }

    private static final boolean use_api_for_availablecheck = true;

    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws IOException, PluginException {
        setBrowserExclusive();
        br.setFollowRedirects(true);
        // Allow +18 videos
        br.setCookie(this.getHost(), "confirmedAdult", "true");
        String filename;
        if (use_api_for_availablecheck) {
            /* 2019-09-24: New */
            br.getPage("https://www." + this.getHost() + "/watch/getVideo/" + this.getFID(link));
            final String error = PluginJSonUtils.getJson(br, "error");
            if (br.getHttpConnection().getResponseCode() == 404 || error != null && !error.equals("null")) {
                /* E.g. "error":"404-error" */
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            filename = PluginJSonUtils.getJson(br, "title");
        } else {
            /* Old handling (e.g. fails to find filename) */
            br.getPage(link.getPluginPatternMatcher());
            if (br.getHttpConnection().getResponseCode() == 404 || br.containsHTML("(Dieses Video ist nicht mehr verf&uuml;gbar|>Sorry, we couldn\\'t find the video you were looking for)")) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            // |AnyClip| at line 461 (above) is removed to avoid PluginException
            filename = br.getRegex("\"title\":\"(.*?)(\\s+RAW)?\"").getMatch(0);
        }
        if (StringUtils.isEmpty(filename)) {
            filename = getFID(link);
        }
        String ext = null;
        if (filename.contains(".")) {
            ext = filename.substring(filename.lastIndexOf("."));
        }
        if (ext == null || ext.length() > 5) {
            ext = ".mp4";
        } else {
            if (new Regex(ext, "\\.(\\d+)").matches()) {
                ext = ".mp4";
            }
        }
        link.setFinalFileName(Encoding.htmlDecode(filename.trim() + ext));
        return AvailableStatus.TRUE;
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(final DownloadLink link) {
        link.setProperty("bytes_loaded", 0l);
        link.setProperty("parts_finished", 0l);
    }

    @Override
    public void resetPluginGlobals() {
    }

    private String xor(final String a, final String b) {
        final byte[] T1 = JDHexUtils.getByteArray(a);
        final byte[] T2 = JDHexUtils.getByteArray(b);
        final byte[] T3 = new byte[T2.length];
        for (int i = 0; i < 4; i++) {
            T3[i] = (byte) (T1[i] ^ T2[i]);
        }
        return JDHexUtils.getHexString(T3);
    }
}