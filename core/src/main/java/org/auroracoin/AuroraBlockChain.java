/**
 * Copyright 2011 Google Inc.
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
import com.google.bitcoin.core.NetworkParameters.KGWParams;
import com.google.bitcoin.store.BlockStore;
import com.google.bitcoin.store.BlockStoreException;
import hashengineering.difficulty.KimotoGravityWell.kgw;

import java.math.BigInteger;
import java.util.List;

import static com.google.common.base.Preconditions.checkState;

/**
 * <p>A BlockChain implements the <i>simplified payment verification</i> mode of the Bitcoin protocol. It is the right
 * choice to use for programs that have limited resources as it won't verify transactions signatures or attempt to store
 * all of the block chain. Really, this class should be called SPVBlockChain but for backwards compatibility it is not.
 * </p>
 */
public class AuroraBlockChain extends BlockChain {
    /**
     * <p>Constructs a BlockChain connected to the given wallet and store. To obtain a {@link Wallet} you can construct
     * one from scratch, or you can deserialize a saved wallet from disk using {@link Wallet#loadFromFile(java.io.File)}
     * </p>
     *
     * <p>For the store, you should use {@link com.google.bitcoin.store.SPVBlockStore} or you could also try a
     * {@link com.google.bitcoin.store.MemoryBlockStore} if you want to hold all headers in RAM and don't care about
     * disk serialization (this is rare).</p>
     */
    public AuroraBlockChain(NetworkParameters params, Wallet wallet, BlockStore blockStore) throws BlockStoreException {
    	super(params,wallet,blockStore);
    }

    /**
     * Constructs a BlockChain that has no wallet at all. This is helpful when you don't actually care about sending
     * and receiving coins but rather, just want to explore the network data structures.
     */
    public AuroraBlockChain(NetworkParameters params, BlockStore blockStore) throws BlockStoreException {
    	super(params, blockStore);
    }

    /**
     * Constructs a BlockChain connected to the given list of listeners and a store.
     */
    public AuroraBlockChain(NetworkParameters params, List<BlockChainListener> wallets,
                      BlockStore blockStore) throws BlockStoreException {
        super(params, wallets, blockStore);
    }

    /**
     * Throws an exception if the blocks difficulty is not correct.
     */
    @Override
    protected void checkDifficultyTransitions(StoredBlock storedPrev, Block nextBlock) throws BlockStoreException, VerificationException {
        checkState(lock.isHeldByCurrentThread());
        BigInteger newDifficulty;
        
    	if ((storedPrev.getHeight()+1) > 5400) {
    		CheckpointManager manager = CheckpointManager.getCheckpointManager();
    		long currentTime = System.currentTimeMillis() / 1000L;
    		if ((manager != null) && (storedPrev.getHeight() < manager.getCheckpointBefore(currentTime).getHeight())) {
    			log.info("Block before latest checkpoint, difficulty not checked");
    			return;
    		}
    		if(!kgw.isNativeLibraryLoaded())
    		    newDifficulty = gravityWellDiff(storedPrev, nextBlock, params.getKgwParams());
    		else
    		    newDifficulty = gravityWellDiff_N(storedPrev, nextBlock, params.getKgwParams());
        } else {
    	
	        Block prev = storedPrev.getHeader();
	        
	        // Is this supposed to be a difficulty transition point?
	        if ((storedPrev.getHeight() + 1) % params.getInterval() != 0) {
	
	            // TODO: Refactor this hack after 0.5 is released and we stop supporting deserialization compatibility.
	            // This should be a method of the NetworkParameters, which should in turn be using singletons and a subclass
	            // for each network type. Then each network can define its own difficulty transition rules.
	            //if (params.getId().equals(TestNet3Params.ID_TESTNET) && nextBlock.getTime().after(testnetDiffDate)) {
	            //    checkTestnetDifficulty(storedPrev, prev, nextBlock);
	            //    return;
	            //}
	
	            // No ... so check the difficulty didn't actually change.
	            if (nextBlock.getDifficultyTarget() != prev.getDifficultyTarget())
	                throw new VerificationException("Unexpected change in difficulty at height " + storedPrev.getHeight() +
	                        ": " + Long.toHexString(nextBlock.getDifficultyTarget()) + " vs " +
	                        Long.toHexString(prev.getDifficultyTarget()));
	            return;
	        }
	
	        // We need to find a block far back in the chain. It's OK that this is expensive because it only occurs every
	        // two weeks after the initial block chain download.
	        long now = System.currentTimeMillis();
	        StoredBlock cursor = blockStore.get(prev.getHash());
	
	        int goBack = params.getRetargetBlockCount(cursor);
	
	        for (int i = 0; i < goBack; i++) {
	            if (cursor == null) {
	                // This should never happen. If it does, it means we are following an incorrect or busted chain.
	                throw new VerificationException(
	                        "Difficulty transition point but we did not find a way back to the genesis block.");
	            }
	            cursor = blockStore.get(cursor.getHeader().getPrevBlockHash());
	        }
	
	        long elapsed = System.currentTimeMillis() - now;
	        if (elapsed > 50)
	            log.info("Difficulty transition traversal took {}msec", elapsed);
	
	        // Check if our cursor is null.  If it is, we've used checkpoints to restore.
	        if(cursor == null) return;
	
	        Block blockIntervalAgo = cursor.getHeader();
	        //log.info("Using block " + cursor.getHeight() + " to calculate next difficulty");
	        //log.info(cursor.toString());
	        int timespan = (int) (prev.getTimeSeconds() - blockIntervalAgo.getTimeSeconds());
	        final int targetTimespan = params.getTargetTimespan();
	        newDifficulty = Utils.decodeCompactBits(prev.getDifficultyTarget());
	    	if ((storedPrev.getHeight()+1) < 135)
	    		newDifficulty = params.getProofOfWorkLimit();
	    	else 
	    	{
	        	
	        	int nActualTimespan = timespan;
	        	//log.info(" nActualTimespan = " + nActualTimespan + " before bounds\n");        
	
		            int nActualTimespanMax = ((targetTimespan*75)/50);
		            int nActualTimespanMin = ((targetTimespan*50)/75);
		           
		       if (nActualTimespan < nActualTimespanMin)
		           nActualTimespan = nActualTimespanMin;
		       if (nActualTimespan > nActualTimespanMax)
		           nActualTimespan = nActualTimespanMax;
		       
		       //log.info("Old diff target: " + newDifficulty.toString(16));
		       newDifficulty = newDifficulty.multiply(BigInteger.valueOf(nActualTimespan));
		       //log.info("Times " + nActualTimespan);
		       //log.info("    is  " + newDifficulty.toString(16));
		       newDifficulty = newDifficulty.divide(BigInteger.valueOf(targetTimespan));
	           //log.info("Div by " + targetTimespan);
		       //log.info("    is  " + newDifficulty.toString(16));
	    	} 
        } 


	int accuracyBytes = (int) (nextBlock.getDifficultyTarget() >>> 24) - 3;
    	
	// The calculated difficulty is to a higher precision than received, so reduce here.
	BigInteger mask = BigInteger.valueOf(0xFFFFFFL).shiftLeft(accuracyBytes * 8);
	newDifficulty = newDifficulty.and(mask);

        	
        if (newDifficulty.compareTo(params.getProofOfWorkLimit()) > 0) {
            log.info("Difficulty hit proof of work limit: {}", params.getProofOfWorkLimit().toString(16));
            newDifficulty = params.getProofOfWorkLimit();
            log.info("Setting to: {}", newDifficulty.toString(16));
        } else {
            //log.info("Difficulty did not hit proof of work limit: {}", params.getProofOfWorkLimit().toString(16));
        }

        BigInteger receivedDifficulty = nextBlock.getDifficultyTargetAsInteger();
        if (newDifficulty.compareTo(receivedDifficulty) != 0)
            throw new VerificationException("Network provided difficulty bits do not match what was calculated: " +
                    receivedDifficulty.toString(16) + " vs " + newDifficulty.toString(16));
        else {
        	//log.info("Network provided difficulty bits match what was calculated: " +
            //        receivedDifficulty.toString(16) + " vs " + newDifficulty.toString(16));        
       }
    }

    private BigInteger gravityWellDiff(StoredBlock storedPrev, Block nextBlock, KGWParams kgwParams) throws BlockStoreException {
        if ((storedPrev == null) ||  (storedPrev.getHeight() == 0) ||  (storedPrev.getHeight() < kgwParams.pastBlocksMin)) 
        {
        	return params.getProofOfWorkLimit();
       	}
    	long PastBlocksMass = 0;
    	long PastRateActualSeconds		= 0;
    	long PastRateTargetSeconds		= 0;
    	double PastRateAdjustmentRatio	= 1.0;
    	BigInteger PastDifficultyAverage = BigInteger.valueOf(0);
    	BigInteger PastDifficultyAveragePrev = PastDifficultyAverage;
    	double EventHorizonDeviation;
    	double	EventHorizonDeviationFast;
    	double	EventHorizonDeviationSlow;

    	StoredBlock blockReading = storedPrev;
    	for (int i = 1; ((blockReading != null) && (blockReading.getHeight() > 0)); i++) {
    		if ((kgwParams.pastBlocksMax > 0) && (i > kgwParams.pastBlocksMax )) { break; }
    		PastBlocksMass++;
    		Block b = blockReading.getHeader();
    		if (i == 1)	{ PastDifficultyAverage = Utils.decodeCompactBits(b.getDifficultyTarget()); }
    		else { 
    			PastDifficultyAverage = Utils.decodeCompactBits(b.getDifficultyTarget());
    			PastDifficultyAverage = PastDifficultyAverage.subtract(PastDifficultyAveragePrev);
    			PastDifficultyAverage = PastDifficultyAverage.divide(BigInteger.valueOf(i)); 
    			PastDifficultyAverage = PastDifficultyAverage.add(PastDifficultyAveragePrev); 
    		}
    		PastDifficultyAveragePrev = PastDifficultyAverage;
    		
    		PastRateActualSeconds			= storedPrev.getHeader().getTimeSeconds() - b.getTimeSeconds();
    		PastRateTargetSeconds			= kgwParams.blocksTargetSpacing * PastBlocksMass;
    		PastRateAdjustmentRatio			= 1.0;
    		if (PastRateActualSeconds < 0) { PastRateActualSeconds = 0; }
    		if (PastRateActualSeconds != 0 && PastRateTargetSeconds != 0) {
    			PastRateAdjustmentRatio		= (double)PastRateTargetSeconds / (double)PastRateActualSeconds;
    		}
    		EventHorizonDeviation = 1 + (0.7084 * Math.pow( (((double)PastBlocksMass)/(double)144), -1.228));
    		EventHorizonDeviationFast		= EventHorizonDeviation;
    		EventHorizonDeviationSlow		= 1 / EventHorizonDeviation;
    		
    		if (PastBlocksMass >= kgwParams.pastBlocksMin) {
    			if ((PastRateAdjustmentRatio <= EventHorizonDeviationSlow) || (PastRateAdjustmentRatio >= EventHorizonDeviationFast)) 
    			{ /*assert(BlockReading);*/ break; }
    		}
    		//if (BlockReading->pprev == NULL) { assert(BlockReading); break; }
    		blockReading = blockReading.getPrev(blockStore);
    		if (blockReading == null) { /*assert(BlockReading);*/ break; }
       	}
    	BigInteger bnNew = PastDifficultyAverage;
	log.info("Average   : " + bnNew.toString(16));
    	if ((PastRateActualSeconds != 0) && (PastRateTargetSeconds != 0)) {
    		bnNew = bnNew.multiply(BigInteger.valueOf(PastRateActualSeconds));
		log.info("Multiplied: " + bnNew.toString(16));
    		bnNew = bnNew.divide(BigInteger.valueOf(PastRateTargetSeconds));
		log.info("Divided   : " + bnNew.toString(16));
    	}
	log.info("Difficulty Retarget - Gravity Well");
	log.info("PastRateAdjustmentRatio = "+PastRateAdjustmentRatio);
	log.info("PastRateAdjustmentRatio = "+PastRateActualSeconds + "/"+PastRateTargetSeconds);
	log.info("Before: " + storedPrev.getHeader().getDifficultyTargetAsInteger().toString(16));
	log.info("After: " + bnNew.toString(16));

    	/*
    	if (bnNew > bnProofOfWorkLimit) { bnNew = bnProofOfWorkLimit; }
    	
        /// debug print
        printf("Difficulty Retarget - Gravity Well\n");
        printf("PastRateAdjustmentRatio = %g\n", PastRateAdjustmentRatio);
        printf("Before: %08x  %s\n", BlockLastSolved->nBits, CBigNum().SetCompact(BlockLastSolved->nBits).getuint256().ToString().c_str());
        printf("After:  %08x  %s\n", bnNew.GetCompact(), bnNew.getuint256().ToString().c_str());
    	
    	return bnNew.GetCompact();
    	*/
    	return bnNew;
    }


private BigInteger  gravityWellDiff_N(StoredBlock storedPrev, Block nextBlock, KGWParams kgwParams)  throws BlockStoreException, VerificationException {
        StoredBlock         BlockLastSolved             = storedPrev;
        StoredBlock         BlockReading                = storedPrev;
        Block               BlockCreating               = nextBlock;
        BlockCreating				= BlockCreating;
        long				PastBlocksMass				= 0;
        long				PastRateActualSeconds		= 0;
        long				PastRateTargetSeconds		= 0;
        double				PastRateAdjustmentRatio		= 1f;
        BigInteger			PastDifficultyAverage = BigInteger.valueOf(0);
        BigInteger			PastDifficultyAveragePrev = BigInteger.valueOf(0);;
        double				EventHorizonDeviation;
        double				EventHorizonDeviationFast;
        double				EventHorizonDeviationSlow;

        long start = System.currentTimeMillis();
        long endLoop = 0;

        if (BlockLastSolved == null || BlockLastSolved.getHeight() == 0 || (long)BlockLastSolved.getHeight() < kgwParams.pastBlocksMin)
        { return params.getProofOfWorkLimit(); }

        int i = 0;
        //log.info("KGW: i = {}; height = {}; hash {} ", i, BlockReading.getHeight(), BlockReading.getHeader().getHashAsString());

        long totalCalcTime = 0;
        long totalReadtime = 0;
        long totalBigIntTime = 0;

        int init_result = kgw.KimotoGravityWell_init(kgwParams.blocksTargetSpacing, kgwParams.pastBlocksMin, kgwParams.pastBlocksMax, 144d);


        for (i = 1; BlockReading != null && BlockReading.getHeight() > 0; i++) {
            int result = kgw.KimotoGravityWell_loop2(i, BlockReading.getHeader().getDifficultyTarget(),BlockReading.getHeight(), BlockReading.getHeader().getTimeSeconds(), BlockLastSolved.getHeader().getTimeSeconds());
            BigInteger diff = BlockReading.getHeader().getDifficultyTargetAsInteger();
            //if(i == 1)
            //    log.info("KGW-N2: difficulty of i=1: " + BlockReading.getHeader().getDifficultyTarget() +"->"+ diff.toString(16));
            if(result == 1)
                break;
            if(result == 2)
                return null;
            
            long calcTime = System.currentTimeMillis();
            StoredBlock BlockReadingPrev = blockStore.get(BlockReading.getHeader().getPrevBlockHash());
            if (BlockReadingPrev == null)
            {
                //If this is triggered, then we are using checkpoints and haven't downloaded enough blocks to verify the difficulty.
                //assert(BlockReading);     //from C++ code
                //break;                    //from C++ code
                return null;
            }
            BlockReading = BlockReadingPrev;
        }

        BigInteger newDifficulty = new BigInteger(kgw.KimotoGravityWell_close());

        return newDifficulty;
    }

}
