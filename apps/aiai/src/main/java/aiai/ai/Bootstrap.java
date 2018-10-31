package aiai.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.loader.JarLauncher;
import org.springframework.boot.loader.jar.JarFile;

@Slf4j
public class Bootstrap extends JarLauncher {

    private static ClassLoader classLoader = null;
    private static Bootstrap bootstrap = null;

    protected void launch(String[] args, String mainClass, ClassLoader classLoader, boolean wait)
            throws Exception {
        Thread.currentThread().setContextClassLoader(classLoader);
        Thread thread = new Thread(() -> {
            try {
                createMainMethodRunner(mainClass, args, classLoader).run();
            }
            catch(Exception e) {
                log.warn("There is an error", e);
            }
        });
        thread.setContextClassLoader(classLoader);
        thread.setName(Thread.currentThread().getName());
        thread.start();
        if (wait) {
            thread.join();
        }
    }

    public static void start (String []args) {
        bootstrap = new Bootstrap ();
        try {
            JarFile.registerUrlProtocolHandler();
            classLoader = bootstrap.createClassLoader(bootstrap.getClassPathArchives());
            bootstrap.launch(args, bootstrap.getMainClass(), classLoader, true);
        }
        catch (Exception ex) {
            ex.printStackTrace();
            System.exit(1);
        }
    }

    public static void stop (String []args) {
        try {
            if (bootstrap != null) {
                bootstrap.launch(args, bootstrap.getMainClass(), classLoader, true);
                bootstrap = null;
                classLoader = null;
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
            System.exit(1);
        }
    }

    public static void main(String[] args) {
        String mode = args != null && args.length > 0 ? args[0] : null;
        if ("start".equals(mode)) {
            Bootstrap.start(args);
        }
        else if ("stop".equals(mode)) {
            Bootstrap.stop(args);
        }
    }
}