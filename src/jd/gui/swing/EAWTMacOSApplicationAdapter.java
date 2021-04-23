//    jDownloader - Downloadmanager
//    Copyright (C) 2008  JD-Team support@jdownloader.org
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
package jd.gui.swing;

import java.awt.Image;
import java.io.File;
import java.util.HashMap;

import javax.swing.JFrame;

import jd.SecondLevelLaunch;
import jd.controlling.TaskQueue;
import jd.controlling.downloadcontroller.DownloadLinkCandidate;
import jd.controlling.downloadcontroller.DownloadLinkCandidateResult;
import jd.controlling.downloadcontroller.DownloadWatchDog;
import jd.controlling.downloadcontroller.DownloadWatchDogProperty;
import jd.controlling.downloadcontroller.SingleDownloadController;
import jd.controlling.downloadcontroller.event.DownloadWatchdogListener;
import jd.controlling.linkcollector.LinkCollectingJob;
import jd.controlling.linkcollector.LinkCollector;
import jd.controlling.linkcollector.LinkOrigin;
import jd.gui.swing.dialog.AboutDialog;
import jd.gui.swing.jdgui.JDGui;
import jd.gui.swing.jdgui.views.settings.ConfigurationView;

import org.appwork.storage.config.JsonConfig;
import org.appwork.storage.config.ValidationException;
import org.appwork.storage.config.events.GenericConfigEventListener;
import org.appwork.storage.config.handler.EnumKeyHandler;
import org.appwork.storage.config.handler.KeyHandler;
import org.appwork.utils.event.queue.QueueAction;
import org.appwork.utils.os.CrossSystem;
import org.appwork.utils.os.CrossSystem.OperatingSystem;
import org.appwork.utils.swing.EDTHelper;
import org.appwork.utils.swing.windowmanager.WindowManager;
import org.appwork.utils.swing.windowmanager.WindowManager.FrameState;
import org.jdownloader.controlling.AggregatedNumbers;
import org.jdownloader.gui.views.downloads.table.DownloadsTable;
import org.jdownloader.icon.IconBadgePainter;
import org.jdownloader.images.NewTheme;
import org.jdownloader.settings.GraphicalUserInterfaceSettings;
import org.jdownloader.settings.GraphicalUserInterfaceSettings.MacDockProgressDisplay;
import org.jdownloader.updatev2.RestartController;
import org.jdownloader.updatev2.SmartRlyExitRequest;

import com.apple.eawt.AboutHandler;
import com.apple.eawt.AppEvent;
import com.apple.eawt.AppEvent.AboutEvent;
import com.apple.eawt.AppEvent.AppReOpenedEvent;
import com.apple.eawt.AppEvent.OpenFilesEvent;
import com.apple.eawt.AppEvent.PreferencesEvent;
import com.apple.eawt.AppEvent.QuitEvent;
import com.apple.eawt.AppReOpenedListener;
import com.apple.eawt.Application;
import com.apple.eawt.OpenFilesHandler;
import com.apple.eawt.OpenURIHandler;
import com.apple.eawt.PreferencesHandler;
import com.apple.eawt.QuitHandler;
import com.apple.eawt.QuitResponse;

public class EAWTMacOSApplicationAdapter implements QuitHandler, AboutHandler, PreferencesHandler, AppReOpenedListener, OpenFilesHandler, OpenURIHandler {
    private static Thread       dockUpdater = null;
    private static final Object LOCK        = new Object();

    public static void enableMacSpecial() {
        Application macApplication = Application.getApplication();
        final EAWTMacOSApplicationAdapter adapter = new EAWTMacOSApplicationAdapter();
        macApplication.setAboutHandler(adapter);
        macApplication.setPreferencesHandler(adapter);
        macApplication.setQuitHandler(adapter);
        macApplication.addAppEventListener(adapter);
        macApplication.setOpenFileHandler(adapter);
        macApplication.setOpenURIHandler(adapter);
        if (CrossSystem.getOS().isMinimum(OperatingSystem.MAC_SIERRA)) {
            SecondLevelLaunch.GUI_COMPLETE.executeWhenReached(new Runnable() {
                public void run() {
                    try {
                        com.apple.eawt.FullScreenUtilities.setWindowCanFullScreen(JDGui.getInstance().getMainFrame(), true);
                        org.appwork.utils.logging2.extmanager.LoggerFactory.getDefaultLogger().info("MacOS FullScreen Support activated");
                    } catch (Throwable e) {
                        org.appwork.utils.logging2.extmanager.LoggerFactory.getDefaultLogger().log(e);
                    }
                }
            });
        }
        SecondLevelLaunch.INIT_COMPLETE.executeWhenReached(new Runnable() {
            private DownloadWatchdogListener listener;

            @Override
            public void run() {
                TaskQueue.getQueue().add(new QueueAction<Void, RuntimeException>() {
                    @Override
                    protected Void run() throws RuntimeException {
                        MacOSApplicationAdapter.setDockIcon(NewTheme.I().getImage("logo/jd_logo_128_128", 128));
                        return null;
                    }
                });
                EnumKeyHandler MacDOCKProgressDisplay = JsonConfig.create(GraphicalUserInterfaceSettings.class)._getStorageHandler().getKeyHandler("MacDockProgressDisplay", EnumKeyHandler.class);
                MacDOCKProgressDisplay.getEventSender().addListener(new GenericConfigEventListener<Enum>() {
                    @Override
                    public void onConfigValidatorError(KeyHandler<Enum> keyHandler, Enum invalidValue, ValidationException validateException) {
                    }

                    @Override
                    public void onConfigValueModified(KeyHandler<Enum> keyHandler, Enum newValue) {
                        if (MacDockProgressDisplay.TOTAL_PROGRESS.equals(newValue)) {
                            startDockUpdater();
                        } else {
                            stopDockUpdater();
                        }
                    }
                });
                DownloadWatchDog.getInstance().getEventSender().addListener(listener = new DownloadWatchdogListener() {
                    @Override
                    public void onDownloadWatchdogStateIsStopping() {
                        stopDockUpdater();
                    }

                    @Override
                    public void onDownloadWatchdogStateIsStopped() {
                        stopDockUpdater();
                    }

                    @Override
                    public void onDownloadWatchdogStateIsRunning() {
                        startDockUpdater();
                    }

                    @Override
                    public void onDownloadWatchdogStateIsPause() {
                        startDockUpdater();
                    }

                    @Override
                    public void onDownloadWatchdogStateIsIdle() {
                    }

                    @Override
                    public void onDownloadWatchdogDataUpdate() {
                    }

                    @Override
                    public void onDownloadControllerStart(SingleDownloadController downloadController, DownloadLinkCandidate candidate) {
                    }

                    @Override
                    public void onDownloadControllerStopped(SingleDownloadController downloadController, DownloadLinkCandidate candidate, DownloadLinkCandidateResult result) {
                    }

                    @Override
                    public void onDownloadWatchDogPropertyChange(DownloadWatchDogProperty propertyChange) {
                    }
                });
                DownloadWatchDog.getInstance().notifyCurrentState(listener);
            }
        });
    }

    private static void startDockUpdater() {
        synchronized (LOCK) {
            if (JsonConfig.create(GraphicalUserInterfaceSettings.class).getMacDockProgressDisplay() != MacDockProgressDisplay.TOTAL_PROGRESS) {
                return;
            }
            Thread ldockUpdater = dockUpdater;
            if (ldockUpdater != null && ldockUpdater.isAlive()) {
                return;
            }
            ldockUpdater = new Thread("MacDOCKUpdater") {
                @Override
                public void run() {
                    int lastPercent = -1;
                    HashMap<Integer, Image> imageCache = new HashMap<Integer, Image>();
                    try {
                        while (Thread.currentThread() == dockUpdater) {
                            try {
                                try {
                                    Thread.sleep(5000);
                                } catch (InterruptedException e) {
                                    break;
                                }
                                final AggregatedNumbers aggn = new AggregatedNumbers(DownloadsTable.getInstance().getSelectionInfo(false, false));
                                int percent = 0;
                                if (aggn.getTotalBytes() > 0) {
                                    percent = (int) ((aggn.getLoadedBytes() * 100) / aggn.getTotalBytes());
                                }
                                final int finalpercent = percent;
                                if (lastPercent == finalpercent) {
                                    continue;
                                }
                                lastPercent = finalpercent;
                                Image image = imageCache.get(finalpercent);
                                if (image == null) {
                                    image = new EDTHelper<Image>() {
                                        @Override
                                        public Image edtRun() {
                                            return new IconBadgePainter(NewTheme.I().getImage("logo/jd_logo_128_128", 128)).getImage(finalpercent, finalpercent + "");
                                        }
                                    }.getReturnValue();
                                    // interrupt will return null
                                    imageCache.put(finalpercent, image);
                                }
                                if (image != null) {
                                    final Image finalImage = image;
                                    TaskQueue.getQueue().add(new QueueAction<Void, RuntimeException>() {
                                        @Override
                                        protected Void run() throws RuntimeException {
                                            MacOSApplicationAdapter.setDockIcon(finalImage);
                                            return null;
                                        }
                                    });
                                }
                            } catch (final Throwable e) {
                                org.appwork.utils.logging2.extmanager.LoggerFactory.getDefaultLogger().log(e);
                            }
                        }
                    } finally {
                        /* restore default Icon */
                        TaskQueue.getQueue().add(new QueueAction<Void, RuntimeException>() {
                            @Override
                            protected Void run() throws RuntimeException {
                                MacOSApplicationAdapter.setDockIcon(NewTheme.I().getImage("logo/jd_logo_128_128", 128));
                                return null;
                            }
                        });
                        /* release reference if this thread is current dockUpdater */
                        synchronized (LOCK) {
                            if (Thread.currentThread() == dockUpdater) {
                                dockUpdater = null;
                            }
                        }
                    }
                }
            };
            ldockUpdater.setDaemon(true);
            dockUpdater = ldockUpdater;
            ldockUpdater.start();
        }
    }

    private static void stopDockUpdater() {
        synchronized (LOCK) {
            if (dockUpdater == null) {
                return;
            }
            Thread ldockUpdater = dockUpdater;
            dockUpdater = null;
            if (ldockUpdater != null && ldockUpdater.isDaemon()) {
                ldockUpdater.interrupt();
            }
        }
    }

    private EAWTMacOSApplicationAdapter() {
    }

    public void handleQuitRequestWith(QuitEvent e, final QuitResponse response) {
        RestartController.getInstance().exitAsynch(new SmartRlyExitRequest() {
            @Override
            public void onShutdown() {
                new Thread() {
                    public void run() {
                        /*
                         * own thread because else it will block, performQuit calls exit again
                         */
                        response.performQuit();
                    };
                }.start();
            }

            @Override
            public void onShutdownVeto() {
                new Thread() {
                    public void run() {
                        /*
                         * own thread because else it will block, performQuit calls exit again
                         */
                        response.cancelQuit();
                    };
                }.start();
            }
        });
    }

    public void handlePreferences(PreferencesEvent e) {
        appReOpened(null);
        JsonConfig.create(GraphicalUserInterfaceSettings.class).setConfigViewVisible(true);
        JDGui.getInstance().setContent(ConfigurationView.getInstance(), true);
    }

    public void handleAbout(AboutEvent e) {
        AboutDialog.showNonBlocking();
    }

    public void appReOpened(AppReOpenedEvent e) {
        final JDGui swingGui = JDGui.getInstance();
        if (swingGui == null || swingGui.getMainFrame() == null) {
            return;
        }
        final JFrame mainFrame = swingGui.getMainFrame();
        if (!mainFrame.isVisible()) {
            WindowManager.getInstance().setVisible(mainFrame, true, FrameState.OS_DEFAULT);
        }
    }

    public void openFiles(final OpenFilesEvent e) {
        appReOpened(null);
        org.appwork.utils.logging2.extmanager.LoggerFactory.getDefaultLogger().info("Handle open files from Dock " + e.getFiles().toString());
        final StringBuilder sb = new StringBuilder();
        for (final File file : e.getFiles()) {
            if (sb.length() > 0) {
                sb.append("\r\n");
            }
            sb.append(file.toURI().toString());
        }
        final String links = sb.toString();
        SecondLevelLaunch.GUI_COMPLETE.executeWhenReached(new Runnable() {
            @Override
            public void run() {
                org.appwork.utils.logging2.extmanager.LoggerFactory.getDefaultLogger().info("Distribute links: " + links);
                LinkCollector.getInstance().addCrawlerJob(new LinkCollectingJob(LinkOrigin.MAC_DOCK.getLinkOriginDetails(), links));
            }
        });
    }

    public void openURI(final AppEvent.OpenURIEvent e) {
        appReOpened(null);
        org.appwork.utils.logging2.extmanager.LoggerFactory.getDefaultLogger().info("Handle open uri from Dock " + e.getURI().toString());
        final String links = e.getURI().toString();
        SecondLevelLaunch.GUI_COMPLETE.executeWhenReached(new Runnable() {
            @Override
            public void run() {
                org.appwork.utils.logging2.extmanager.LoggerFactory.getDefaultLogger().info("Distribute links: " + links);
                LinkCollector.getInstance().addCrawlerJob(new LinkCollectingJob(LinkOrigin.MAC_DOCK.getLinkOriginDetails(), links));
            }
        });
    }
}
