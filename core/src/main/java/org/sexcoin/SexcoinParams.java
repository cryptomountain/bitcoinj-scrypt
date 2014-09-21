package org.sexcoin;

import com.google.bitcoin.core.*;
//import com.google.bitcoin.core.NetworkParameters.KGWParams;
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
	 * 
	 */
	private static final long serialVersionUID = 4720786260636487008L;
	/**
	 * Parameters for the testnet, a separate public instance of Bitcoin that has relaxed rules suitable for development
	 * and testing of applications and new Bitcoin versions.
	 */
	public static final String ID_SEXCOIN = "org.sexcoin.production";
	private static final int FIX_KGW_TIMEWARP_HEIGHT = 643808;
	protected static final int targetKGWTimespan = (int)(60); // sexcoin: 60 seconds per block
    private static final int	timeDaySeconds = 60 * 60 * 24;
	private static final long PastSecondsMin	= (long)(timeDaySeconds * 0.25); // * 0.5;
	private static final long PastSecondsMax	= timeDaySeconds * 7;
	private static final long PastBlocksMin	= PastSecondsMin / targetKGWTimespan;
	private static final long PastBlocksMax	= PastSecondsMax / targetKGWTimespan;
	

    private KGWParams kgwParams= new KGWParams(targetKGWTimespan, PastBlocksMin, PastBlocksMax);
    
    protected int targetTimespan_1;
    protected int targetTimespan_2;
    protected int interval_1;
    protected int interval_2;
    
    @Override
    public KGWParams getKgwParams() {
		return kgwParams;
	}
    
    public static int getFixKgwTimewarpHeight() {
		return FIX_KGW_TIMEWARP_HEIGHT;
	}

	@Override
    public int getTargetTimespan(){
    	return this.targetTimespan;
    }
    
    public int getTargetTimespan(int blockHeight){
    	if( blockHeight <= 155000 ) return this.targetTimespan;
    	if( blockHeight  > 155000 && blockHeight < 572001) return this.targetTimespan_1;
    	if( blockHeight >= 572000 && blockHeight < getFixKgwTimewarpHeight() ) return this.targetTimespan_2;
    	
    	return targetKGWTimespan;
    	
    }
    
    public int getInterval(int blockHeight){
    	if( blockHeight <= 155000 ) return this.interval;
    	if( blockHeight  > 155000 && blockHeight < 572000) return this.interval_1;
    	if( blockHeight >= 572000 && blockHeight < getFixKgwTimewarpHeight() ) return this.interval_2;
    	return this.interval_2;
    	
    }
    
    public SexcoinParams() {
	        super();
	        
	        // TODO Adjust these for Sexcoin
	        id = ID_SEXCOIN;
	        proofOfWorkLimit = Utils.decodeCompactBits(0x1e7fffffL);
	        //proofOfWorkLimit = Utils.decodeCompactBits(0x1d00ffffL);
	        addressHeader = 62;                            // sexcoin
	        acceptableAddressCodes = new int[] { addressHeader }; // sexcoin
	        port = 9560;                                   // sexcoin
	        packetMagic = 0xface6969L;						// sexcoin
	       // packetMagic = 0xfbc0b6dbL;					// sexcoin block < 155000
	        dumpedPrivateKeyHeader = 128 + addressHeader;

	        // up to block 155000
	        targetTimespan = (int)(8 * 60 * 60); 			// sexcoin's original retarget was 8 hours   
	        interval = targetTimespan/((int)(1 * 60));     // sexcoin [ every 480 blocks ]
	        
	        // between block 155000 and 572001
	        targetTimespan_1 = (int)( 30 * 60);				// retarget for 30 minutes
	        interval_1 = targetTimespan_1/((int)(30));
	        
	        // after block 572001
	        targetTimespan_2 = (int)( 15 * 60 );
	        interval_2 = targetTimespan_2/((int) 15);

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
	            		("04a5814813115273a109cff99907ba4a05d951873dae7acb6c973d0c9e7c88911a3dbc9aa600deac241b91707e7b4ffb30ad91c8e56e695a1ddf318592988afe0a")); //sexcoin
	            // OP_CHECKSIG shows up in abe as part of the PubKey, so we use the real key and add on the OP_CHECKSIG for consistency with bitcoin.		
	            scriptPubKeyBytes.write(ScriptOpCodes.OP_CHECKSIG);
	            t.addOutput(new TransactionOutput(this, t, Utils.toNanoCoins(50, 0), scriptPubKeyBytes.toByteArray()));
	        } catch (Exception e) {
	            // Cannot happen.
	            throw new RuntimeException(e);
	        }
	        genesisBlock.addTransaction(t);
	        String genesisHash = genesisBlock.getHashAsString();
	        if(!genesisHash.equals("f42b9553085a1af63d659d3907a42c3a0052bbfa2693d3acf990af85755f2279"))
	        {
	        	System.out.println("genesis hash issue: " + genesisHash + " : " + "f42b9553085a1af63d659d3907a42c3a0052bbfa2693d3acf990af85755f2279" );
	        	System.out.println("merkle root: " + genesisBlock.getMerkleRoot());
	        	System.out.println("Nonce: " + genesisBlock.getNonce());
	        	System.out.println("     : " + genesisBlock.toString());
	        }else{
	        	System.out.println("GenesisBlock : " + genesisBlock.toString());
	        }
	        //checkState(genesisHash.equals("f42b9553085a1af63d659d3907a42c3a0052bbfa2693d3acf990af85755f2279"), // sexcoin
	        //		genesisBlock);

	        subsidyDecreaseBlockCount = 600000; //sexcoin

	        dnsSeeds = new String[] {
	        	"dnsseed.lavajumper.com"
	        };
	    }

	    private static BigInteger MAX_MONEY = Utils.COIN.multiply(BigInteger.valueOf(250000000));
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

	    public int getRetargetBlockCount(StoredBlock cursor,int blockHeight) {
	        if (cursor.getHeight() + 1 != getInterval()) {
	            System.out.println("returning interval " + getInterval(blockHeight));
	            
	            return getInterval(blockHeight);
	        } else {
	            //Logger.getLogger("wallet_sxc").info("Genesis SXC retarget");
	            return getInterval(blockHeight) - 1;
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

