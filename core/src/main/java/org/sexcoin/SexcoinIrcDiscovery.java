package org.sexcoin;

import com.google.bitcoin.net.discovery.*;

import com.google.bitcoin.core.AddressFormatException;
import com.google.bitcoin.core.Base58;
import com.google.bitcoin.core.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * IrcDiscovery provides a way to find network peers by joining a pre-agreed rendevouz point on the LFnet IRC network.
 * <b>This class is deprecated because LFnet has ceased to operate and DNS seeds now exist for both prod and test
 * networks.</b> It may conceivably still be useful for running small ad-hoc networks by yourself.
 */

public class SexcoinIrcDiscovery implements PeerDiscovery {
    private static final Logger log = LoggerFactory.getLogger(SexcoinIrcDiscovery.class);

    private String channel;
    private int port = 6667;
    private String server;

    private BufferedWriter writer = null;

    private Socket connection;

    /**
     * Finds a list of peers by connecting to an IRC network, joining a channel, decoding the nicks and then
     * disconnecting.
     *
     * @param channel The IRC channel to join, either "#sexcoin00" or "#sexcoinTEST3" for the production and test networks
     *                respectively.
     */
    public SexcoinIrcDiscovery(String channel) {
        this(channel, "irc.smutfairy.com", 6667); // sexcoin
    }

    /**
     * Finds a list of peers by connecting to an IRC network, joining a channel, decoding the nicks and then
     * disconnecting.
     *
     * @param server  Name or textual IP address of the IRC server to join.
     * @param channel The IRC channel to join, either "#bitcoin" or "#bitcoinTEST3" for the production and test networks
     */
    public SexcoinIrcDiscovery(String channel, String server, int port) {
        this.channel = channel;
        this.server = server;
        this.port = port;
    }

    protected void onIRCSend(String message) {
        log.info("IRC Send: "+message);
    }

    protected void onIRCReceive(String message) {
        log.info("IRC Receive: "+message);
    }

    public void shutdown() {
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (IOException ex) {
            // ignore
        }
    }
    /**
     * Returns a list of peers that were found in the IRC channel. Note that just because a peer appears in the list
     * does not mean it is accepting connections. The given time out value is applied for every IP returned by DNS
     * for the given server, so a timeout value of 1 second may result in 5 seconds delay if 5 servers are advertised.
     */
    public InetSocketAddress[] getPeers(long timeoutValue, TimeUnit timeoutUnit) throws PeerDiscoveryException {
        log.info("IrcDiscovery, getpeers [" + server + ":" + port + "]");
        ArrayList<InetSocketAddress> addresses = new ArrayList<InetSocketAddress>();
        connection = null;
        BufferedReader reader = null;
        try {
            InetAddress[] ips = InetAddress.getAllByName(server);
            // Pick a random server for load balancing reasons. Try the rest if
            int ipCursorStart = (int)(Math.random()*ips.length);
            int ipCursor = ipCursorStart;
            do {
                log.info("opening connection...");
            	connection = new Socket();
                int timeoutMsec = (int) TimeUnit.MILLISECONDS.convert(timeoutValue, timeoutUnit);
                connection.setSoTimeout(timeoutMsec);
                try {
                    InetAddress ip = ips[ipCursor];
                    log.info("Connecting to IRC with " + ip + ":" + port);
                    connection.connect(new InetSocketAddress(ip, port), timeoutMsec);
                } catch (SocketTimeoutException e) {
                	log.info("SOCKET TIMEOUT IRC");
                    connection = null;
                } catch (IOException e) {
                	log.info("IOException IRC");
                    connection = null;
                }
                log.info("GOT IPs ...");
                ipCursor = (ipCursor + 1) % ips.length;
                if (ipCursor == ipCursorStart) {
                	log.info("WTF!!! IRC");
                    //throw new PeerDiscoveryException("Could not connect to " + server);
                }
                if(connection == null){
                	log.info("Attempted connection to server " + server + "failed...");
                }
            } while (connection == null);
            log.info("Connected to IRC");

            writer = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream(), "UTF-8"));
            reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));

            // Generate a random nick for the connection. This is chosen to be clearly identifiable as coming from
            // bitcoinj but not match the standard nick format, so full peers don't try and connect to us.
            String nickRnd = String.format("bcj%d", new Random().nextInt(Integer.MAX_VALUE));
            String command = "NICK " + nickRnd;
            logAndSend(command);
            // USER <user> <mode> <unused> <realname> (RFC 2812)
            command = "USER " + nickRnd + " 8 *: " + nickRnd;
            logAndSend(command);
            writer.flush();

            // Wait to be logged in. Worst case we end up blocked until the server PING/PONGs us out.
            String currLine;
            while ((currLine = reader.readLine()) != null) {
                onIRCReceive(currLine);
                // 004 tells us we are connected
                // TODO: add common exception conditions (nick already in use, etc..)
                // these aren't bullet proof checks but they should do for our purposes.
                if (checkLineStatus("004", currLine)) {
                    break;
                }
                
            }

            // Join the channel.
            logAndSend("JOIN " + channel);
            // List users in channel.
            logAndSend("NAMES " + channel);
            writer.flush();

            // A list of the users should be returned. Look for code 353 and parse until code 366.
            while ((currLine = reader.readLine()) != null) {
                onIRCReceive(currLine);
                log.info("**IRC CONVERSATION**: " + currLine);
                if (checkLineStatus("353", currLine)) {
                    // Line contains users. List follows ":" (second ":" if line starts with ":")
                    int subIndex = 0;
                    if (currLine.startsWith(":")) {
                        subIndex = 1;
                    }

                    String spacedList = currLine.substring(currLine.indexOf(":", subIndex));
                    addresses.addAll(parseUserList(spacedList.substring(1).split(" ")));
                } else if (checkLineStatus("366", currLine)) {
                    // End of user list.
                    break;
                }
            }

            // Quit the server.
            logAndSend("PART " + channel);
            logAndSend("QUIT");
            writer.flush();
        } catch (Exception e) {
            // Throw the original error wrapped in the discovery error.
            throw new PeerDiscoveryException(e.getMessage(), e);
        } finally {
            try {
                if (reader != null) reader.close();
                if (writer != null) writer.close();
                // No matter what try to close the connection.
                if (connection != null) connection.close();
            } catch (IOException e) {
                log.warn("Exception whilst closing IRC discovery: " + e.toString());
            }
        }
        log.info("IrcDiscovery, getpeers found " + addresses.size());
        return addresses.toArray(new InetSocketAddress[]{});
    }

    private void logAndSend(String command) throws Exception {
        onIRCSend(command);
        writer.write(command + "\n");
    }

    // Visible for testing.
    static ArrayList<InetSocketAddress> parseUserList(String[] userNames) throws UnknownHostException {
        ArrayList<InetSocketAddress> addresses = new ArrayList<InetSocketAddress>();
        for (String user : userNames) {
            // All BitCoin peers start their nicknames with a 'u' character or an 'x'.
            if (!user.startsWith("u") && !user.startsWith("x")) {
                continue;
            }

            // After "u" is stripped from the beginning array contains unsigned chars of:
            // 4 byte ip address, 2 byte port, 4 byte hash check (ipv4)

            byte[] addressBytes;
            try {
                // Strip off the "u" before decoding. Note that it's possible for anyone to join these IRC channels and
                // so simply beginning with "u" does not imply this is a valid BitCoin encoded address.
                //
                // decodeChecked removes the checksum from the returned bytes.
                addressBytes = Base58.decodeChecked(user.substring(1));
            } catch (AddressFormatException e) {
                log.warn("IRC nick does not parse as base58: " + user);
                continue;
            }

            // TODO: Handle IPv6 if one day the official client uses it. It may be that IRC discovery never does.
            if (addressBytes.length != 6) {
                continue;
            }

            byte[] ipBytes = new byte[]{addressBytes[0], addressBytes[1], addressBytes[2], addressBytes[3]};
            int port = Utils.readUint16BE(addressBytes, 4);
            if (port == 12341) port = 12340; //get around that stupid bug
            InetAddress ip;
            try {
                ip = InetAddress.getByAddress(ipBytes);
            } catch (UnknownHostException e) {
                // Bytes are not a valid IP address.
                continue;
            }

            InetSocketAddress address = new InetSocketAddress(ip, port);
            //log.warn("IRC found address: " + ip+":"+port);
            addresses.add(address);
        }

        return addresses;
    }

    private static boolean checkLineStatus(String statusCode, String response) {
        // Lines can either start with the status code or an optional :<source>
        //
        // All the testing shows the servers for this purpose use :<source> but plan for either.
        // TODO: Consider whether regex would be worth it here.
        if (response.startsWith(":")) {
            // Look for first space.
            int startIndex = response.indexOf(" ") + 1;
            // Next part should be status code.
            return response.indexOf(statusCode + " ", startIndex) == startIndex;
        } else {
            if (response.startsWith(statusCode + " ")) {
                return true;
            }
        }
        return false;
    }
}