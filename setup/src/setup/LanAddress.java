package setup;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

// 폰이 이 PC에 접속할 때 쓸 같은 Wi-Fi(LAN)의 주소. VirtualBox/Hyper-V/VPN 같은 가상 어댑터는 Java의 isVirtual()이
// 걸러주지 못해서(실측), 이름으로 직접 걸러야 진짜 Wi-Fi/이더넷 주소를 안정적으로 찾을 수 있다 (앱의 ServerLauncher와 같은 방식).
final class LanAddress {

    private LanAddress() {}

    private static final String[] IGNORED = {
            "virtualbox", "vmware", "hyper-v", "docker", "wsl", "zerotier", "hamachi", "tailscale", "loopback", "vpn", "teredo"
    };

    // 못 찾으면 null
    static String find() {
        try {
            String fallback = null;
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                NetworkInterface iface = ifaces.nextElement();
                if (!iface.isUp() || iface.isLoopback() || iface.isVirtual()) continue;
                String name = iface.getDisplayName() == null ? "" : iface.getDisplayName().toLowerCase();
                boolean virtualLooking = false;
                for (String k : IGNORED) {
                    if (name.contains(k)) { virtualLooking = true; break; }
                }
                Enumeration<InetAddress> addrs = iface.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) {
                        if (!virtualLooking) return a.getHostAddress();
                        if (fallback == null) fallback = a.getHostAddress();
                    }
                }
            }
            return fallback;
        } catch (Exception e) {
            return null;
        }
    }
}
