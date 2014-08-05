package org.sexcoin;

import com.google.bitcoin.core.*;
import com.google.bitcoin.script.Script;
import com.google.bitcoin.script.ScriptOpCodes;
import com.lambdaworks.crypto.SCrypt;

import org.spongycastle.util.encoders.Hex;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.security.GeneralSecurityException;

import static com.google.common.base.Preconditions.checkState;

public class SexcoinParams extends NetworkParameters {
	/**
	 * Parameters for the testnet, a separate public instance of Bitcoin that has relaxed rules suitable for development
	 * and testing of applications and new Bitcoin versions.
	 */
    public SexcoinParams() {
	        super();
	        // TODO Adjust these for Sexcoin
	        id = "org.sexcoin.production";
	        proofOfWorkLimit = Utils.decodeCompactBits(0x1e0fffffL);
	        addressHeader = 48;
	        acceptableAddressCodes = new int[] { 48 };
	        port = 9560;
	        packetMagic = 0xfbc0b6dbL;
	        dumpedPrivateKeyHeader = 128 + addressHeader;

	        targetTimespan = (int)(3.5 * 24 * 60 * 60);
	        interval = targetTimespan/((int)(2.5 * 60));

	        genesisBlock.setDifficultyTarget(0x1e0ffff0L);
	        genesisBlock.setTime(1317972665L);
	        genesisBlock.setNonce(2084524493L);
	        genesisBlock.removeTransaction(0);
	        Transaction t = new Transaction(this);
	        try {
	            // A script containing the difficulty bits and the following message:
	            //
	            //   "The Times 03/Jan/2009 Chancellor on brink of second bailout for banks"
	            byte[] bytes = Hex.decode
	                    ("04ffff001d0104404e592054696d65732030352f4f63742f32303131205374657665204a6f62732c204170706c65e280997320566973696f6e6172792c2044696573206174203536");
	            t.addInput(new TransactionInput(this, t, bytes));
	            ByteArrayOutputStream scriptPubKeyBytes = new ByteArrayOutputStream();
	            Script.writeBytes(scriptPubKeyBytes, Hex.decode
	                    ("040184710fa689ad5023690c80f3a49c8f13f8d45b8c857fbcbc8bc4a8e4d3eb4b10f4d4604fa08dce601aaf0f470216fe1b51850b4acf21b179c45070ac7b03a9"));
	            scriptPubKeyBytes.write(ScriptOpCodes.OP_CHECKSIG);
	            t.addOutput(new TransactionOutput(this, t, Utils.toNanoCoins(50, 0), scriptPubKeyBytes.toByteArray()));
	        } catch (Exception e) {
	            // Cannot happen.
	            throw new RuntimeException(e);
	        }
	        genesisBlock.addTransaction(t);
	        String genesisHash = genesisBlock.getHashAsString();
	        checkState(genesisHash.equals("12a765e31ffd4059bada1e25190f6e98c99d9714d334efa41a195a7e7e04bfe2"),
	                genesisBlock);

	        subsidyDecreaseBlockCount = 840000;

	        dnsSeeds = new String[] {
	                "dnsseed.litecointools.com",
	                "dnsseed.litecoinpool.org",
	                "dnsseed.ltc.xurious.com",
	                "dnsseed.koin-project.com",
	                "dnsseed.weminemnc.com"
	        };
	    }

	    private static BigInteger MAX_MONEY = Utils.COIN.multiply(BigInteger.valueOf(84000000));
	    @Override
	    public BigInteger getMaxMoney() { return MAX_MONEY; }

	    private static SexcoinParams instance;
	    public static synchronized SexcoinParams get() {
	        if (instance == null) {
	            instance = new SexcoinParams();
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

	    @Override public String getURIScheme() { return "sexcoin:"; }

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
	        NetworkParameters.PROTOCOL_VERSION = 60002;
	    }
}

