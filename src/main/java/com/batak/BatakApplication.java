package com.batak;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.awt.Desktop;
import java.net.URI;

@SpringBootApplication
public class BatakApplication {

    public static void main(String[] args) {
        SpringApplication.run(BatakApplication.class, args);
    }

    @Component
    public static class BrowserOpener {
        @EventListener(ApplicationReadyEvent.class)
        public void openBrowser() {
            String url = "http://localhost:8080";
            System.out.println("============================================");
            System.out.println("  BATAK GAME SERVER STARTED");
            System.out.println("  Open this URL on each device: " + url);
            System.out.println("============================================");
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(new URI(url));
                } else {
                    // Fallback for Windows
                    String os = System.getProperty("os.name").toLowerCase();
                    if (os.contains("win")) {
                        Runtime.getRuntime().exec(new String[]{"rundll32", "url.dll,FileProtocolHandler", url});
                    }
                }
            } catch (Exception e) {
                System.out.println("Could not open browser automatically. Please open " + url + " manually.");
            }
        }
    }
}
