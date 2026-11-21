package dev.irltunes.media;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Windows fallback for when the media session is unavailable (older Windows, PowerShell blocked by policy):
 * finds known player windows and reads "Artist - Title" from their window titles. No progress or cover art,
 * so covers come from the online lookup if it is enabled.
 */
final class WindowsTitleSource implements MediaSource {
    private static final int PROCESS_QUERY_LIMITED_INFORMATION = 0x1000;
    private final Map<Integer, String> processNames = new HashMap<>();

    @Override
    public String name() {
        return "windows-window-titles";
    }

    @Override
    public Optional<TrackInfo> poll() {
        TrackInfo[] found = new TrackInfo[1];
        Map<String, Boolean> players = AppNames.titlePlayers();
        char[] buf = new char[512];
        User32.INSTANCE.EnumWindows((hwnd, data) -> {
            if (!User32.INSTANCE.IsWindowVisible(hwnd)) return true;
            int len = User32.INSTANCE.GetWindowText(hwnd, buf, buf.length);
            if (len <= 0) return true;
            IntByReference pid = new IntByReference();
            User32.INSTANCE.GetWindowThreadProcessId(hwnd, pid);
            String proc = processNames.computeIfAbsent(pid.getValue(), WindowsTitleSource::processName);
            if (proc == null) return true;
            String id = AppNames.normalize(proc);
            Boolean artistFirst = players.get(id);
            if (artistFirst == null) return true;
            String[] parsed = TitleParser.parse(new String(buf, 0, len), artistFirst);
            if (parsed == null) return true;
            found[0] = new TrackInfo(proc, parsed[1], parsed[0], "", true, -1, -1, System.currentTimeMillis(), null, null);
            return false; // first match wins
        }, null);
        if (processNames.size() > 512) processNames.clear();
        return Optional.ofNullable(found[0]);
    }

    private static String processName(int pid) {
        WinNT.HANDLE h = Kernel32.INSTANCE.OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, false, pid);
        if (h == null) return null;
        try {
            char[] path = new char[1024];
            IntByReference size = new IntByReference(path.length);
            if (!Kernel32.INSTANCE.QueryFullProcessImageName(h, 0, path, size)) return null;
            return Native.toString(path);
        } finally {
            Kernel32.INSTANCE.CloseHandle(h);
        }
    }
}
