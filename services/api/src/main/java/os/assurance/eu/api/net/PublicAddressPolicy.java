package os.assurance.eu.api.net;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Decides whether an outbound fetch may connect to an address. Only globally routable unicast addresses pass:
 * loopback, private, carrier-grade NAT, link-local (cloud metadata at 169.254.169.254), benchmark, documentation,
 * multicast and reserved ranges are refused, including IPv6 forms that embed an IPv4 address (NAT64, 6to4,
 * IPv4-compatible) so a private IPv4 target cannot be reached through them.
 */
public final class PublicAddressPolicy {
  private PublicAddressPolicy() {
  }

  public static boolean isPublic(InetAddress address) {
    if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
        || address.isSiteLocalAddress() || address.isMulticastAddress()) {
      return false;
    }
    byte[] b = address.getAddress();
    return b.length == 4 ? isPublicV4(b, 0) : isPublicV6(b);
  }

  private static boolean isPublicV4(byte[] b, int at) {
    int a0 = b[at] & 0xff;
    int a1 = b[at + 1] & 0xff;
    int a2 = b[at + 2] & 0xff;
    return !(a0 == 0                                   // 0.0.0.0/8 ("this network", reaches localhost on Linux)
        || a0 == 10                                    // 10/8
        || (a0 == 100 && a1 >= 64 && a1 <= 127)        // 100.64/10 carrier-grade NAT
        || a0 == 127                                   // loopback
        || (a0 == 169 && a1 == 254)                    // link-local, cloud metadata
        || (a0 == 172 && a1 >= 16 && a1 <= 31)         // 172.16/12
        || (a0 == 192 && a1 == 0 && (a2 == 0 || a2 == 2)) // 192.0.0/24 protocol assignments, 192.0.2/24 docs
        || (a0 == 192 && a1 == 88 && a2 == 99)         // 6to4 relay anycast
        || (a0 == 192 && a1 == 168)                    // 192.168/16
        || (a0 == 198 && (a1 == 18 || a1 == 19))       // 198.18/15 benchmarking
        || (a0 == 198 && a1 == 51 && a2 == 100)        // docs
        || (a0 == 203 && a1 == 0 && a2 == 113)         // docs
        || a0 >= 224);                                 // multicast, 240/4 reserved, broadcast
  }

  private static boolean isPublicV6(byte[] b) {
    int b0 = b[0] & 0xff;
    int b1 = b[1] & 0xff;
    if ((b0 & 0xfe) == 0xfc) {
      return false; // fc00::/7 unique local
    }
    if (b0 == 0xfe && (b1 & 0xc0) == 0xc0) {
      return false; // fec0::/10 deprecated site-local
    }
    if (allZero(b, 0, 12)) {
      return isPublicV4(b, 12); // ::a.b.c.d IPv4-compatible
    }
    if (b0 == 0x00 && b1 == 0x64 && (b[2] & 0xff) == 0xff && (b[3] & 0xff) == 0x9b) {
      return isPublicV4(b, 12); // 64:ff9b::/96 and 64:ff9b:1::/48 NAT64
    }
    if (b0 == 0x20 && b1 == 0x02) {
      return isPublicV4(b, 2); // 2002::/16 6to4
    }
    if (b0 == 0x20 && b1 == 0x01 && b[2] == 0 && b[3] == 0) {
      return false; // 2001::/32 Teredo
    }
    if (b0 == 0x20 && b1 == 0x01 && (b[2] & 0xff) == 0x0d && (b[3] & 0xff) == 0xb8) {
      return false; // 2001:db8::/32 documentation
    }
    if (b0 == 0x01 && b1 == 0x00 && allZero(b, 2, 8)) {
      return false; // 100::/64 discard
    }
    return true;
  }

  private static boolean allZero(byte[] b, int from, int to) {
    for (int i = from; i < to; i++) {
      if (b[i] != 0) {
        return false;
      }
    }
    return true;
  }

  /** Resolves {@code host} and refuses it unless every address is public. */
  public static InetAddress[] resolvePublic(String host) throws UnknownHostException {
    InetAddress[] addresses = InetAddress.getAllByName(host);
    for (InetAddress address : addresses) {
      if (!isPublic(address)) {
        throw new NonPublicAddressException(host);
      }
    }
    return addresses;
  }

  /** Thrown from DNS resolution so HTTP clients abort before opening a socket. */
  public static class NonPublicAddressException extends UnknownHostException {
    public NonPublicAddressException(String host) {
      super("URL resolves to a private or reserved address: " + host);
    }
  }
}
