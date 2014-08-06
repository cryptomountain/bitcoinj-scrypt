/**
 * 
 */
package org.sexcoin;

import com.google.bitcoin.core.*;
import com.google.bitcoin.net.discovery.PeerDBDiscovery;
import com.google.bitcoin.net.discovery.PeerDiscoveryException;

import java.io.File;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * @author Lavajumper <lavajumper@lavajumper.com>
 *
 */
public class SexcoinPeerDBDiscovery extends PeerDBDiscovery {

    private static class WrappedEventListener extends AbstractPeerEventListener {
        PeerEventListener parent;
        NetworkParameters params;
        WrappedEventListener(NetworkParameters params, PeerEventListener parent) {
            this.params = params;
            this.parent = parent;
        }
        @Override
        public Message onPreMessageReceived(Peer p, Message m) {
            if (m instanceof AddressMessage) {
                AddressMessage newMessage = new AddressMessage(params);
                for (PeerAddress addr : ((AddressMessage) m).getAddresses())
                    if (addr.getServices().and(BigInteger.valueOf(1 << 1)).equals(BigInteger.valueOf(1 << 1)))
                        newMessage.addAddress(addr);
                return newMessage;
            }
            return m;
        }
        @Override
        public void onPeerConnected(Peer p, int peerCount) {
            if ((p.getPeerVersionMessage().localServices & (1<<1)) == (1<<1) &&
                    p.getPeerVersionMessage().clientVersion >= 60011)  // sexcoin **check this**
                parent.onPeerConnected(p, peerCount);
            else
                p.close();
        }

        @Override
        public void onPeerDisconnected(Peer p, int peerCount) {
            if (p.getPeerVersionMessage() != null && (p.getPeerVersionMessage().localServices & (1<<1)) == (1<<1))
                parent.onPeerDisconnected(p, peerCount);
        }
    }
      
    private static class PeerGroupWrapper extends PeerGroup {
        private PeerGroup parent;
        NetworkParameters params;
        private PeerGroupWrapper(NetworkParameters params, PeerGroup peerGroup) {
            super(params);
            this.params = params;
            parent = peerGroup;
        }
        @Override
        public void addEventListener(PeerEventListener listener) {
            parent.addEventListener(new WrappedEventListener(params, listener));
        }
    }

	public SexcoinPeerDBDiscovery(NetworkParameters params, File db, PeerGroup group) {
		super(params, db, new PeerGroupWrapper(params, group));
	}

}
