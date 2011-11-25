package org.jdownloader.update;

import java.io.File;
import java.io.IOException;

import org.appwork.shutdown.ShutdownEvent;
import org.appwork.utils.Application;
import org.appwork.utils.os.CrossSystem;

public class SilentUpdaterEvent extends ShutdownEvent {
    private static final SilentUpdaterEvent INSTANCE = new SilentUpdaterEvent();

    /**
     * get the only existing instance of SilentUpdaterEvent. This is a singleton
     * 
     * @return
     */
    public static SilentUpdaterEvent getInstance() {
        return SilentUpdaterEvent.INSTANCE;
    }

    /**
     * Create a new instance of SilentUpdaterEvent. This is a singleton class.
     * Access the only existing instance by using {@link #getInstance()}.
     */
    private SilentUpdaterEvent() {
        super();
        this.setHookPriority(Integer.MIN_VALUE);

    }

    @Override
    public void run() {

        final String tiny[] = new String[] { CrossSystem.getJavaBinary(), "-jar", RestartController.UPDATER_JARNAME, "-autoclose", "10" };
        if (Application.getResource(RestartController.JARNAME).exists()) {
            System.out.println(Application.getResource(RestartController.JARNAME) + " exists");
        } else {
            System.err.println(Application.getResource(RestartController.JARNAME) + " is Missing");
        }

        /*
         * build complete call arguments for tinybootstrap
         */
        final StringBuilder sb = new StringBuilder();

        for (final String arg : tiny) {
            sb.append(arg + " ");
        }

        System.out.println("UpdaterCall: " + sb.toString());

        final ProcessBuilder pb = new ProcessBuilder(tiny);
        /*
         * needed because the root is different for jre/class version
         */
        File pbroot = null;

        pbroot = new File(Application.getHome());

        System.out.println("Root: " + pbroot);
        pb.directory(pbroot);
        try {
            pb.start();
        } catch (final IOException e) {
        }
    }
}
