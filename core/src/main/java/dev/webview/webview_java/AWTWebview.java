package dev.webview.webview_java;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.Closeable;
import java.util.function.Consumer;

import co.casterlabs.commons.platform.Platform;
import lombok.Getter;
import lombok.Setter;

/**
 * An AWT component a which will automatically initialize the webview when it's
 * considered "drawable".
 * 
 */
public class AWTWebview extends Canvas implements Closeable {
    private static final long serialVersionUID = 5199512256429931156L;

    private volatile @Getter Webview webview;
    private final boolean debug;

    private Dimension lastSize = null;

    /**
     * The callback handler for when the Webview gets created.
     */
    private @Setter Consumer<Webview> onInitialized;

    private @Getter boolean initialized = false;

    public AWTWebview() {
        this(false);
    }

    /**
     * @param debug Whether or not to allow the opening of inspect element/devtools.
     */
    public AWTWebview(boolean debug) {
        this.debug = debug;
        this.setBackground(Color.BLACK);
        this.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                Dimension size = getSize();
                lastSize = size;
                if (webview != null) {
                    int w = size.width, h = size.height;
                    if (Platform.osFamily.name().equals("WINDOWS")) {
                        // set_size_impl calls SetWindowPos(m_window=Canvas) which does nothing
                        // useful in embedded mode; resize m_widget directly so its WndProc
                        // WM_SIZE handler calls resize_webview() → put_Bounds().
                        long canvasHwnd = webview.getNativeWindowPointer();
                        webview.dispatch(() -> _WindowsHelper.resizeWidgetChild(canvasHwnd, w, h));
                    } else {
                        webview.dispatch(() -> webview.setFixedSize(w, h));
                    }
                }
            }
        });
    }

    @Override
    public void paint(Graphics g) {
        if (!this.initialized) {
            if (this.lastSize == null) {
                this.lastSize = this.getSize();
            }
            this.initialized = true;

            // We need to create the webview off of the swing thread.
            Thread t = new Thread(() -> {
                // webview 0.11.0 requires COM/STA initialization on the calling thread
                // when embedding into an existing window (not needed when webview owns the window).
                boolean needsCOM = Platform.osFamily.name().equals("WINDOWS");
                if (needsCOM) {
                    _WindowsHelper.initializeCOM();
                }
                try {
                    this.webview = new Webview(this.debug, this);

                    this.updateSize();

                    if (this.onInitialized != null) {
                        this.onInitialized.accept(this.webview);
                    }

                    this.webview.run();
                } finally {
                    if (needsCOM) {
                        _WindowsHelper.uninitializeCOM();
                    }
                }
            });
            t.setDaemon(false);
            t.setName("AWTWebview RunAsync Thread - #" + this.hashCode());
            t.start();
        }
    }

    private void updateSize() {
        int width = this.lastSize.width;
        int height = this.lastSize.height;

        this.webview.setFixedSize(width, height);
    }

    @Override
    public void close() {
        this.webview.close();
        this.initialized = false;
        this.webview = null;
    }

}
