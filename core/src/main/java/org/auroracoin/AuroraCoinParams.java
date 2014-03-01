/*
 * Copyright 2013 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.auroracoin;

import com.google.bitcoin.core.*;
import com.google.bitcoin.script.Script;
import com.google.bitcoin.script.ScriptOpCodes;
import com.lambdaworks.crypto.SCrypt;
import org.spongycastle.util.encoders.Hex;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.security.GeneralSecurityException;

import static com.google.common.base.Preconditions.checkState;

/**
 * Parameters for the testnet, a separate public instance of Bitcoin that has relaxed rules suitable for development
 * and testing of applications and new Bitcoin versions.
 */
public class AuroraCoinParams extends NetworkParameters {
    public static final String ID_AURORACOIN = "org.auroracoin.production";
    public AuroraCoinParams() {
        super();
        id = ID_AURORACOIN;
        proofOfWorkLimit = Utils.decodeCompactBits(0x1e0fffffL);
        addressHeader = 23;
        acceptableAddressCodes = new int[] { addressHeader };
        port = 12340;
        packetMagic = 0xfda4dc6cL;
        dumpedPrivateKeyHeader = 128 + addressHeader;

        targetTimespan = (int)(8 * 10 * 60);
	int targetSpacing = (int)(10 * 60);
        interval = targetTimespan/targetSpacing;

        genesisBlock.setDifficultyTarget(0x1e0fffffL);
        genesisBlock.setTime(1390598806L);
        genesisBlock.setNonce(538548L);
        genesisBlock.removeTransaction(0);
        Transaction t = new Transaction(this);
        try {
            // A script containing the difficulty bits and the following message:
            //
            //   "The Times 03/Jan/2009 Chancellor on brink of second bailout for banks"
	    // const char* pszTimestamp = "Visir 10. oktober 2008 Gjaldeyrishoft sett a Islendinga";
            byte[] bytes = Hex.decode
                    ("04ffff001d01043756697369722031302e206f6b746f626572203230303820476a616c646579726973686f6674207365747420612049736c656e64696e6761");
	    TransactionInput ti = new TransactionInput(this, t, 4294967295L, bytes);
	    //TransactionInput ti = new TransactionInput(this, t, bytes);
            t.addInput(ti);
            ByteArrayOutputStream scriptPubKeyBytes = new ByteArrayOutputStream();
            Script.writeBytes(scriptPubKeyBytes, Hex.decode
("04a5814813115273a109cff99907ba4a05d951873dae7acb6c973d0c9e7c88911a3dbc9aa600deac241b91707e7b4ffb30ad91c8e56e695a1ddf318592988afe0a"));

            scriptPubKeyBytes.write(ScriptOpCodes.OP_CHECKSIG);
            t.addOutput(new TransactionOutput(this, t, Utils.toNanoCoins(1, 0), scriptPubKeyBytes.toByteArray()));
        } catch (Exception e) {
            // Cannot happen.
            throw new RuntimeException(e);
        }
        genesisBlock.addTransaction(t);
        String genesisHash = genesisBlock.getHashAsString();

	// This is the block hash
        checkState(genesisHash.equals("2a8e100939494904af825b488596ddd536b3a96226ad02e0f7ab7ae472b27a8e"),
                genesisBlock);

        subsidyDecreaseBlockCount = 840000;

        dnsSeeds = new String[] {
                "dnsseed.aurora.so"
        		
        };
    }

    private static BigInteger MAX_MONEY = Utils.COIN.multiply(BigInteger.valueOf(84000000));
    @Override
    public BigInteger getMaxMoney() { return MAX_MONEY; }

    private static AuroraCoinParams instance;
    public static synchronized AuroraCoinParams get() {
        if (instance == null) {
            instance = new AuroraCoinParams();
        }
        return instance;
    }

    /** The number of previous blocks to look at when calculating the next Block's difficulty */
    @Override
    public int getRetargetBlockCount(StoredBlock cursor) {
        if (cursor.getHeight() + 1 != getInterval()) {
            //Logger.getLogger("wallet_ltc").info("Normal LTC retarget");
            return getInterval();
        } else {
            //Logger.getLogger("wallet_ltc").info("Genesis LTC retarget");
            return getInterval() - 1;
        }
    }

    @Override public String getURIScheme() { return "auroracoin:"; }

    /** Gets the hash of the given block for the purpose of checking its PoW */
    public Sha256Hash calculateBlockPoWHash(Block b) {
        byte[] blockHeader = b.cloneAsHeader().bitcoinSerialize();
        try {
            return new Sha256Hash(Utils.reverseBytes(SCrypt.scrypt(blockHeader, blockHeader, 1024, 1, 1, 32)));
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    static {
        NetworkParameters.registerParams(get());
        NetworkParameters.PROTOCOL_VERSION = 1000000;
    }
}
