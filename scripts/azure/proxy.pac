/* proxy.pac:
 * A proxy-autoconfig file that will route HTTP traffic
 * over a SOCKS5 tunnel to a number of Azure clusters based on
 * hostname conventions and IP address ranges.
 *
 * See instructions for installing this on the wiki.
 * TODO: write instructions on the wiki.
 *
 * To open a tunnel to one of the remote hosts, use ssh's `-D` option
 * with the appropriate port below.  For instance, when using our
 * internal DNS you can connect to the west-us-2 cluster, run this
 * command:
 * $ ssh -L 20142:127.0.0.1:3128 azure-west-us-2
 * A tunnel will be openend between your host and an HTTP proxy running on
 * the west2 bastion, allowing transparent HTTP proxying once this config
 * file is installed.
 *
 * TODO: use an auto-ssh config to maintain persistent proxy tunnels
 * to the various remote regions.
 *
 * Assumed port mapping:
 * 20141 -> Azure central-us
 * 20142 -> Azure west-us-2
 * 20143 -> Azure south-central-us
 */

function FindProxyForURL(url, host) {
    if (isInNet(host, "10.20.0.0", "255.255.0.0") || host.match("^twentyn-")) {
        // Azure central-us
        // TODO: update naming scheme to match other regions.
        return "PROXY 127.0.0.1:20141";
    } else if (isInNet(host, "10.21.0.0", "255.255.0.0") || host.match("-west2$")) {
        // Azure west-us-2
        return "PROXY 127.0.0.1:20142";
    } else if (isInNet(host, "10.22.0.0", "255.255.0.0") || host.match("-scus$")) {
        // Azure south-central-us
        return "PROXY 127.0.0.1:20144";
    }

    return "DIRECT";
}

/* Made with help from these resources:
 * https://developer.mozilla.org/en-US/docs/Mozilla/Projects/Necko/Proxy_Auto-Configuration_(PAC)_file
 * https://en.wikipedia.org/wiki/Proxy_auto-config
 * http://findproxyforurl.com/example-pac-file/
 * http://www.systutorials.com/944/proxy-using-ssh-tunnel/
 */
