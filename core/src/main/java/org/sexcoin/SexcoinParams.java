package org.sexcoin;

import com.google.bitcoin.core.*;
import com.google.bitcoin.core.NetworkParameters.KGWParams;
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
	public static final String ID_SEXCOIN = "org.sexcoin.production";
	protected static final int targetKGWTimespan = (int)(60); // sexcoin: 60 seconds per block
    private static final int	timeDaySeconds = 60 * 60 * 24;
	private static final long PastSecondsMin	= timeDaySeconds / 2; // * 0.5;
	private static final long PastSecondsMax	= timeDaySeconds * 14;
	private static final long PastBlocksMin	= PastSecondsMin / targetKGWTimespan;
	private static final long PastBlocksMax	= PastSecondsMax / targetKGWTimespan;	

    private KGWParams kgwParams= new KGWParams(targetKGWTimespan, PastBlocksMin, PastBlocksMax);
    
    @Override
    public KGWParams getKgwParams() {
		return kgwParams;
	}
    
	public int getTargetTimespan(int blockHeight) {
    	if (blockHeight < 5401) return this.getTargetTimespan();
    	return targetKGWTimespan;
    }
    
    public SexcoinParams() {
	        super();
	        
	        // TODO Adjust these for Sexcoin
	        id = ID_SEXCOIN;
	        proofOfWorkLimit = Utils.decodeCompactBits(0x1e0fffffL);
	        addressHeader = 62;                            // sexcoin
	        acceptableAddressCodes = new int[] { addressHeader }; // sexcoin
	        port = 9560;                                   // sexcoin
	        packetMagic = 0xface6969L;                     // sexcoin
	        dumpedPrivateKeyHeader = 128 + addressHeader;

	        targetTimespan = (int)(3.5 * 24 * 60 * 60);    
	        interval = targetTimespan/((int)(1 * 60));     // sexcoin

	        genesisBlock.setDifficultyTarget(0x1e7fffffL); // sexcoin
	        genesisBlock.setTime(1369146359L);             // sexcoin
	        genesisBlock.setNonce(244086L);                // sexcoin
	        genesisBlock.removeTransaction(0);
	        Transaction t = new Transaction(this);
	        try {
	            // A script containing the difficulty bits and the following message:
	            //
	            //   "Disaster from the sky in Oklahoma"

	            byte[] bytes = Hex.decode
	            		("04ffff001d01042144697361737465722066726f6d2074686520736b7920696e204f6b6c61686f6d61"); // sexcoin
	            t.addInput(new TransactionInput(this, t, bytes));
	            ByteArrayOutputStream scriptPubKeyBytes = new ByteArrayOutputStream();
	            Script.writeBytes(scriptPubKeyBytes, Hex.decode
	            		("040184710fa689ad5023690c80f3a49c8f13f8d45b8c857fbcbc8bc4a8e4d3eb4b10f4d4604fa08dce601aaf0f470216fe1b51850b4acf21b179c45070ac7b03a9")); //sexcoin
	            scriptPubKeyBytes.write(ScriptOpCodes.OP_CHECKSIG);
	            t.addOutput(new TransactionOutput(this, t, Utils.toNanoCoins(50, 0), scriptPubKeyBytes.toByteArray()));
	        } catch (Exception e) {
	            // Cannot happen.
	            throw new RuntimeException(e);
	        }
	        genesisBlock.addTransaction(t);
	        String genesisHash = genesisBlock.getHashAsString();
	        checkState(genesisHash.equals("f42b9553085a1af63d659d3907a42c3a0052bbfa2693d3acf990af85755f2279"), // sexcoin
	        		genesisBlock);

	        subsidyDecreaseBlockCount = 600000; //sexcoin

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
	        NetworkParameters.PROTOCOL_VERSION = 60011; //sexcoin
	    }
}

