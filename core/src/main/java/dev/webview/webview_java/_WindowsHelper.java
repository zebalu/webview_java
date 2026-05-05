package dev.webview.webview_java;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.PointerType;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.platform.win32.WinDef.BOOL;
import com.sun.jna.platform.win32.WinDef.BOOLByReference;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.ptr.PointerByReference;

class _WindowsHelper {

    // webview 0.11.0 no longer calls CoInitializeEx when a parent window is provided;
    // the caller must initialize COM with STA before webview_create and uninitialize after webview_destroy.
    static void initializeCOM() {
        Ole32.INSTANCE.CoInitializeEx(null, 0x2 /* COINIT_APARTMENTTHREADED */);
    }

    static void uninitializeCOM() {
        Ole32.INSTANCE.CoUninitialize();
    }

    @SuppressWarnings("deprecation")
    static void setWindowAppearance(Webview webview, boolean shouldBeDark) {
        // References:
        // https://docs.microsoft.com/en-us/windows/win32/api/dwmapi/nf-dwmapi-dwmsetwindowattribute
        // https://winscp.net/forum/viewtopic.php?t=30088
        // https://gist.github.com/rossy/ebd83ba8f22339ce25ef68bfc007dfd2
        //
        // This is the code that we're mimicking (in c):
        /*
        DwmSetWindowAttribute(
            hwnd, 
            DWMWA_USE_IMMERSIVE_DARK_MODE,
            &(BOOL) { TRUE }, 
            sizeof(BOOL)
        );
        InvalidateRect(hwnd, null, FALSE);
        */

        HWND hwnd = new HWND(new Pointer(webview.getNativeWindowPointer()));
        BOOLByReference pvAttribute = new BOOLByReference(new BOOL(shouldBeDark));

        DWM.N.DwmSetWindowAttribute(
            hwnd,
            DWM.DWMWA_USE_IMMERSIVE_DARK_MODE,
            pvAttribute,
            BOOL.SIZE
        );

        DWM.N.DwmSetWindowAttribute(
            hwnd,
            DWM.DWMWA_USE_IMMERSIVE_DARK_MODE_BEFORE_20H1,
            pvAttribute,
            BOOL.SIZE
        );

        User32.N.InvalidateRect(hwnd, null, 0); // Repaint
    }

    private static interface DWM extends Library {
        static final DWM N = Native.load("dwmapi", DWM.class);

        static final int DWMWA_USE_IMMERSIVE_DARK_MODE_BEFORE_20H1 = 19;
        static final int DWMWA_USE_IMMERSIVE_DARK_MODE = 20;

        int DwmSetWindowAttribute(HWND hwnd, int dwAttribute, PointerType pvAttribute, int cbAttribute);

    }

    // Resizes the embedded WebView2 windows to fill the Canvas on resize.
    // The chain is: Canvas child (webview_widget / m_widget) → Canvas grandchild
    // (Chrome_WidgetWin). put_Bounds on the controller doesn't propagate to
    // Chrome_WidgetWin in this custom build, so both levels must be resized explicitly.
    // MUST be called from within webview.dispatch() (webview STA thread) — cross-thread
    // MoveWindow from AWT EDT would risk a SendMessage deadlock.
    static void resizeWidgetChild(long canvasHwnd, int w, int h) {
        com.sun.jna.platform.win32.User32 u32 = com.sun.jna.platform.win32.User32.INSTANCE;
        HWND parent = new HWND(new Pointer(canvasHwnd));
        HWND child = u32.FindWindowEx(parent, null, null, null);
        while (child != null && Pointer.nativeValue(child.getPointer()) != 0) {
            User32.N.MoveWindow(child, 0, 0, w, h, true);
            HWND gc = u32.FindWindowEx(child, null, null, null);
            while (gc != null && Pointer.nativeValue(gc.getPointer()) != 0) {
                User32.N.MoveWindow(gc, 0, 0, w, h, true);
                gc = u32.FindWindowEx(child, gc, null, null);
            }
            child = u32.FindWindowEx(parent, child, null, null);
        }
    }

    private static interface User32 extends Library {
        static final User32 N = Native.load("user32", User32.class);

        int     InvalidateRect(HWND hwnd, PointerByReference rect, int erase);
        boolean MoveWindow(HWND hWnd, int X, int Y, int nWidth, int nHeight, boolean bRepaint);
    }

}
